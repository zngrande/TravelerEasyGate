package com.example.UsefulTravel.service;

import com.example.UsefulTravel.DAO.*;
import com.example.UsefulTravel.entity.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * AiParseService - 「智慧行程打碎與結構化解析」核心邏輯
 *
 * 流程:
 *   1. parseText(): 把線控貼上的原始文字丟給 Claude, 要求輸出結構化 JSON,
 *      存成 ai_import + ai_parsed_day + ai_parsed_item (暫存, 讓人先確認)
 *      過程中會自動比對公司 POI 資料庫 (matchedPid)
 *   2. confirmImport(): 線控確認無誤後, 把暫存資料轉成正式的
 *      itinerary / itinerary_day / itinerary_item (呼叫 ItineraryService)
 */
@Service
public class AiParseService {

    private final AnthropicClient anthropicClient;
    private final AiImportDAO aiImportDAO;
    private final AiParsedDayDAO aiParsedDayDAO;
    private final AiParsedItemDAO aiParsedItemDAO;
    private final PoiDAO poiDAO;
    private final ItineraryService itineraryService;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
        你是專門協助台灣旅行社線控(OP)拆解旅遊行程文件的助手。
        使用者會貼上一段行程文字(可能是別家旅行社的企劃書、客戶需求描述、或行程草稿)。
        你的任務是把它拆解成結構化資料，並「只」輸出一個 JSON 物件，不要有任何其他文字、
        不要用 markdown code fence 包起來。

        JSON 格式規則:
        {
          "template_style": "wenqing | luxury | corporate | default",
          "days": [
            {
              "day_number": 1,
              "theme": "當天主題簡短描述 (例如: 台北市區文化巡禮)",
              "items": [
                {
                  "item_type": "attraction | meal | hotel | transport | highlight",
                  "name": "景點/餐廳/飯店/交通方式的名稱",
                  "time_slot": "morning | noon | afternoon | evening | breakfast | lunch | dinner | null",
                  "note": "原文中的補充說明 (例如: 含早餐、五星飯店、包車接送、注意事項等), 沒有就給空字串"
                }
              ]
            }
          ]
        }

        template_style 判斷規則 (依照原文的文字風格/用詞判斷, 只能選以下四種之一):
        - "wenqing" (文青風): 原文用詞抒情、感性, 常見詩意描述、生活風格用語, 較少制式條列
        - "luxury" (高端奢華風): 原文強調五星、尊榮、獨家、頂級、私人管家等高端字眼
        - "corporate" (企業員工旅遊簡報風): 原文用詞正式、條列、商務用語多, 像公司內部簡報或制式報價單
        - "default": 看不出明顯風格, 或原文很簡短沒有太多形容詞時使用

        規則:
        - item_type=attraction 是景點/活動; meal 是餐食; hotel 是住宿; transport 是航班/高鐵/包車等交通;
          highlight 是行銷亮點文案或注意事項(不屬於實體地點的敘述)。
        - 依照原文出現的天數順序拆解，若原文沒有明確分天，你要合理推斷。
        - 名稱要精簡(例如「故宮博物院」而不是整句話)，細節放到 note。
        - 如果原文資訊不完整，盡力用你判斷合理的方式填, 不要留空必填欄位。
        - 絕對不要輸出 JSON 以外的任何文字或說明。
        """;

    @Autowired
    public AiParseService(AnthropicClient anthropicClient, AiImportDAO aiImportDAO,
                           AiParsedDayDAO aiParsedDayDAO, AiParsedItemDAO aiParsedItemDAO,
                           PoiDAO poiDAO, ItineraryService itineraryService,
                           ObjectMapper objectMapper) {
        this.anthropicClient = anthropicClient;
        this.aiImportDAO = aiImportDAO;
        this.aiParsedDayDAO = aiParsedDayDAO;
        this.aiParsedItemDAO = aiParsedItemDAO;
        this.poiDAO = poiDAO;
        this.itineraryService = itineraryService;
        this.objectMapper = objectMapper;
    }

    /**
     * 送出文字給 Claude 解析, 存成暫存資料, 回傳 AiImport 讓 controller 導去 review 頁面
     */
    public AiImport parseText(int AID, int UID, String rawText) {
        return parseText(AID, UID, rawText, "text");
    }

    /**
     * @param sourceType 記錄這份資料原始來源: text / pdf / docx (方便之後在列表分辨)
     */
    public AiImport parseText(int AID, int UID, String rawText, String sourceType) {
        AiImport aiImport = new AiImport(AID, UID, sourceType, rawText);
        aiImportDAO.save(aiImport); // 先存一筆 pending, 拿到 IPID

        try {
            String jsonText = anthropicClient.complete(SYSTEM_PROMPT, rawText, 4000);
            JsonNode root = objectMapper.readTree(stripCodeFence(jsonText));

            aiImport.setTemplateStyle(normalizeStyle(root.path("template_style").asText("default")));

            for (JsonNode dayNode : root.path("days")) {
                AiParsedDay day = new AiParsedDay(
                        aiImport.getIPID(),
                        dayNode.path("day_number").asInt(),
                        dayNode.path("theme").asText(null)
                );
                aiParsedDayDAO.save(day);

                int sortOrder = 0;
                for (JsonNode itemNode : dayNode.path("items")) {
                    String name = itemNode.path("name").asText("");
                    Integer matchedPid = findMatchingPoi(AID, name);

                    AiParsedItem item = new AiParsedItem(
                            day.getAPDID(),
                            itemNode.path("item_type").asText("attraction"),
                            name,
                            emptyToNull(itemNode.path("time_slot").asText(null)),
                            itemNode.path("note").asText(""),
                            sortOrder++
                    );
                    item.setMatchedPid(matchedPid);
                    aiParsedItemDAO.save(item);
                }
            }

            aiImport.setStatus("parsed");
        } catch (Exception e) {
            aiImport.setStatus("failed");
            aiImport.setErrorMessage(e.getMessage() != null ? e.getMessage() : e.toString());
        }

        aiImportDAO.save(aiImport);
        return aiImport;
    }

    // 用名稱關鍵字比對公司 POI 資料庫, 找到就自動連結 (簡化版, 之後可換成更精準的比對演算法)
    private Integer findMatchingPoi(int AID, String name) {
        if (name == null || name.isBlank()) return null;
        List<Poi> matches = poiDAO.searchByKeyword(AID, name, null);
        return matches.isEmpty() ? null : matches.get(0).getPID();
    }

    private String emptyToNull(String s) {
        return (s == null || s.isBlank() || "null".equalsIgnoreCase(s)) ? null : s;
    }

    // 保險起見, AI 有時可能回傳不在預期範圍內的值, 一律 fallback 成 default
    private String normalizeStyle(String style) {
        if (style == null) return "default";
        return switch (style.toLowerCase()) {
            case "wenqing", "luxury", "corporate" -> style.toLowerCase();
            default -> "default";
        };
    }

    // Claude 有時仍會習慣性包 ```json ... ``` , 保險起見去掉
    private String stripCodeFence(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```[a-zA-Z]*\\s*", "");
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
        }
        return trimmed.trim();
    }

    public AiImport findById(int IPID) {
        return aiImportDAO.findById(IPID);
    }

    public List<AiImport> findByAgency(int AID) {
        return aiImportDAO.findByAgency(AID);
    }

    public List<AiParsedDay> getDays(int IPID) {
        return aiParsedDayDAO.findByImport(IPID);
    }

    public List<AiParsedItem> getItems(int APDID) {
        return aiParsedItemDAO.findByDay(APDID);
    }

    /**
     * 把 AI 解析出來的單一項目寫進公司 POI 資料庫 (時間預設 NULL, 之後線控自己補)
     * 加入後會把這個暫存項目的 matchedPid 更新成新建立的 POI, 畫面上會顯示「已比對」
     *
     * @return 新建立的 Poi
     */
    public Poi addItemToPoi(int AID, int APIID) {
        AiParsedItem item = aiParsedItemDAO.findById(APIID);
        if (item == null) throw new IllegalArgumentException("找不到這個項目");
        if (item.getMatchedPid() != null) throw new IllegalStateException("這個項目已經比對過 POI 資料庫了");

        String category = mapItemTypeToPoiCategory(item.getItemType());
        if (category == null) {
            throw new IllegalArgumentException("「" + typeDisplayName(item.getItemType()) + "」不是景點/餐廳/住宿類型，無法加入 POI 資料庫");
        }

        Poi poi = new Poi(AID, category, item.getName(), null, null, null, null, null);
        poi.setSuggestedStayMin(null); // 依需求: 時間預設 NULL, 之後再由線控補
        poi.setDescription(item.getNote());
        poiDAO.save(poi);

        item.setMatchedPid(poi.getPID());
        aiParsedItemDAO.save(item);

        return poi;
    }

    // AI 解析出的 item_type 對應到 POI 資料庫的 category (transport/highlight 沒有對應, 不能加入)
    private String mapItemTypeToPoiCategory(String itemType) {
        if (itemType == null) return null;
        return switch (itemType) {
            case "attraction" -> "attraction";
            case "meal" -> "restaurant";
            case "hotel" -> "hotel";
            default -> null; // transport / highlight 不是實體地點, 不能加入 POI
        };
    }

    private String typeDisplayName(String itemType) {
        if (itemType == null) return "此項目";
        return switch (itemType) {
            case "transport" -> "交通";
            case "highlight" -> "亮點文案";
            default -> itemType;
        };
    }

    // 取得單一暫存項目所屬的 AiImport IPID (給 controller 導回 review 頁面用)
    public int getIpidByItem(int APIID) {
        AiParsedItem item = aiParsedItemDAO.findById(APIID);
        if (item == null) throw new IllegalArgumentException("找不到這個項目");
        AiParsedDay day = aiParsedDayDAO.findById(item.getAPDID());
        if (day == null) throw new IllegalArgumentException("找不到對應的解析紀錄");
        return day.getIPID();
    }

    /**
     * 線控確認暫存資料無誤後, 轉成正式行程
     * (matchedPid 有值就直接連結公司 POI, 沒有就當自訂項目, 名稱先用 AI 解析出來的文字)
     */
    public Itinerary confirmImport(int IPID, String title, String country) {
        AiImport aiImport = aiImportDAO.findById(IPID);
        if (aiImport == null) throw new IllegalArgumentException("找不到這筆 AI 解析紀錄");

        List<AiParsedDay> days = aiParsedDayDAO.findByImport(IPID);
        int daysCount = Math.max(1, days.size());

        Itinerary itinerary = itineraryService.createItinerary(
                aiImport.getAID(), aiImport.getCreatedBy(), title, country, daysCount, LocalDate.now());

        // 把 AI 判斷出來的文件風格帶到正式行程上, 匯出企劃書時會套用同樣風格
        itineraryService.updateTemplateStyle(itinerary.getITID(), aiImport.getTemplateStyle());
        itinerary.setTemplateStyle(aiImport.getTemplateStyle());

        List<ItineraryDay> realDays = itineraryService.getDays(itinerary.getITID());

        for (AiParsedDay day : days) {
            // day_number 對應到剛剛自動產生的 itinerary_day
            ItineraryDay realDay = realDays.stream()
                    .filter(d -> d.getDayNumber() == day.getDayNumber())
                    .findFirst()
                    .orElse(realDays.get(0)); // 保底: 找不到對應天數就丟第一天

            for (AiParsedItem item : aiParsedItemDAO.findByDay(day.getAPDID())) {
                itineraryService.addItem(realDay.getIDID(), item.getMatchedPid(), item.getItemType(), item.getName());
            }
        }

        aiImport.setStatus("confirmed");
        aiImport.setResultItineraryId(itinerary.getITID());
        aiImportDAO.save(aiImport);

        return itinerary;
    }
}
