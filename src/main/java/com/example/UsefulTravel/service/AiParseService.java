package com.example.UsefulTravel.service;

import com.example.UsefulTravel.DAO.*;
import com.example.UsefulTravel.entity.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
    private final GoogleMapsClient googleMapsClient;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
        你是專門協助台灣旅行社線控(OP)拆解旅遊行程文件的助手。
        使用者會貼上一段行程文字(可能是別家旅行社的企劃書、客戶需求描述、或行程草稿)。
        你的任務是把它拆解成結構化資料，並「只」輸出一個 JSON 物件，不要有任何其他文字、
        不要用 markdown code fence 包起來。

        JSON 格式規則:
        {
          "suggested_title": "根據內容建議的行程標題, 例如「花蓮經典三日遊」",
          "country": "根據內容判斷的國家, 例如「台灣」「日本」「泰國」, 判斷不出來就填「未知」",
          "region": "根據內容判斷的地區/城市 (國家底下更細的地點), 例如「花蓮」「北海道」「清邁」, 判斷不出來就填「未知」",
          "days": [
            {
              "day_number": 1,
              "theme": "當天主題簡短描述 (例如: 台北市區文化巡禮)",
              "items": [
                {
                  "item_type": "attraction | meal | hotel | transport | highlight",
                  "name": "景點/餐廳/飯店/交通方式的名稱",
                  "item_country": "這一個項目實際所在的國家 (不是整趟行程的國家, 是這一項自己的), 例如多國行程裡某個景點在「不丹」某個在「印度」就分別標註, 判斷不出來給 null",
                  "item_region": "這一個項目實際所在的地區/城市, 判斷不出來給 null",
                  "time_slot": "morning | noon | afternoon | evening | breakfast | lunch | dinner | null",
                  "note": "原文中的補充說明 (例如: 含早餐、五星飯店、包車接送、注意事項等), 沒有就給空字串",
                  "stay_minutes": "預估在這個地方會停留幾分鐘 (數字, 15的倍數, 例如 90); attraction/meal 一定要給估計值, hotel 給 null (入住時間另外算), transport/highlight 給 null"
                }
              ]
            }
          ]
        }

        規則:
        - item_type=attraction 是景點/活動; meal 是餐食; hotel 是住宿; transport 是航班/高鐵/包車等交通;
          highlight 是行銷亮點文案或注意事項(不屬於實體地點的敘述)。
        - 依照原文出現的天數順序拆解，若原文沒有明確分天，你要合理推斷。
        - 名稱要精簡(例如「故宮博物院」而不是整句話)，細節放到 note。
        - stay_minutes 依常識估算: 大型景點/博物館約90~180分鐘, 一般景點約60~90分鐘,
          用餐約60~90分鐘, 如果原文有明確提到停留時間就用原文的。
        - 如果原文資訊不完整，盡力用你判斷合理的方式填, 不要留空必填欄位。
        - 如果使用者訊息最前面有一段用「【行程重點資訊】」包起來的文字, 那是使用者自己填寫的出發/抵達機場、
          時間、行程說明等重點資訊, 準確度比後面的原始文字高, 天數判斷、日期相關的 note、行程國家/地區
          都要優先參考這段內容, 如果跟後面原始文字衝突以這段為準。
        - 絕對不要輸出 JSON 以外的任何文字或說明。
        """;

    @Autowired
    public AiParseService(AnthropicClient anthropicClient, AiImportDAO aiImportDAO,
                          AiParsedDayDAO aiParsedDayDAO, AiParsedItemDAO aiParsedItemDAO,
                          PoiDAO poiDAO, ItineraryService itineraryService,
                          ObjectMapper objectMapper, GoogleMapsClient googleMapsClient) {
        this.anthropicClient = anthropicClient;
        this.aiImportDAO = aiImportDAO;
        this.aiParsedDayDAO = aiParsedDayDAO;
        this.aiParsedItemDAO = aiParsedItemDAO;
        this.poiDAO = poiDAO;
        this.itineraryService = itineraryService;
        this.googleMapsClient = googleMapsClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 送出文字給 Claude 解析, 存成暫存資料, 回傳 AiImport 讓 controller 導去 review 頁面
     */
    public AiImport parseText(int AID, int UID, String rawText) {
        return parseText(AID, UID, rawText, "text", "default", null);
    }

    /**
     * @param sourceType 記錄這份資料原始來源: text / pdf / docx (方便之後在列表分辨)
     */
    public AiImport parseText(int AID, int UID, String rawText, String sourceType) {
        return parseText(AID, UID, rawText, sourceType, "default", null);
    }

    /**
     * @param userStyle 使用者手動指定的風格 (wenqing/luxury/corporate/default)
     */
    public AiImport parseText(int AID, int UID, String rawText, String sourceType, String userStyle) {
        return parseText(AID, UID, rawText, sourceType, userStyle, null);
    }

    /**
     * @param extraContext 使用者填的「行程重點資訊」(出發/抵達機場+時間、行程說明) 格式化文字, 選填。
     *                      會存進 ai_import.extra_context (review 頁面顯示用), 也會當額外 context
     *                      一併送給 AI 解析, 提高天數/日期判斷的準確度。
     */
    public AiImport parseText(int AID, int UID, String rawText, String sourceType, String userStyle, String extraContext) {
        AiImport aiImport = new AiImport(AID, UID, sourceType, rawText);
        aiImport.setExtraContext(emptyToNull(extraContext));
        aiImportDAO.save(aiImport); // 先存一筆 pending, 拿到 IPID

        try {
            String userContent = buildUserContent(rawText, aiImport.getExtraContext());
            String jsonText = anthropicClient.complete(SYSTEM_PROMPT, userContent, 16000);
            JsonNode root = objectMapper.readTree(stripCodeFence(jsonText));

            aiImport.setTemplateStyle(normalizeStyle(userStyle));

            aiImport.setSuggestedTitle(emptyToNull(root.path("suggested_title").asText(null)));
            String country = emptyToNull(root.path("country").asText(null));
            aiImport.setSuggestedCountry("未知".equals(country) ? null : country);
            String region = emptyToNull(root.path("region").asText(null));
            aiImport.setSuggestedRegion("未知".equals(region) ? null : region);

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

                    item.setItemCountry(emptyToNull(itemNode.path("item_country").asText(null)));
                    item.setItemRegion(emptyToNull(itemNode.path("item_region").asText(null)));

                    JsonNode stayNode = itemNode.path("stay_minutes");
                    if (stayNode.isNumber()) {
                        item.setStayMinutes(stayNode.asInt());
                    }

                    aiParsedItemDAO.save(item);
                }
            }

            aiImport.setStatus("parsed");
        } catch (Exception e) {
            aiImport.setStatus("failed");
            String msg = e.getMessage() != null ? e.getMessage() : e.toString();
            if (msg.contains("Unexpected end-of-input") || msg.contains("closing quote")) {
                msg = "AI 回應內容被截斷了（通常是行程內容太多），請嘗試把文件拆成較短的段落分次解析。原始錯誤：" + msg;
            } else if (msg.toLowerCase().contains("timed out") || msg.toLowerCase().contains("timeout")) {
                msg = "AI 解析逾時了（通常是行程內容太長，AI 生成時間拉長導致），請嘗試把文件拆成較短的段落分次解析，或稍後再試一次。原始錯誤：" + msg;
            }
            aiImport.setErrorMessage(msg);
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

    // 把使用者填的「行程重點資訊」包成一個明顯的區塊放在原始文字前面, 讓 AI 知道這段優先度較高
    // (SYSTEM_PROMPT 裡有明確說明【行程重點資訊】這個標記的意義)
    private String buildUserContent(String rawText, String extraContext) {
        if (extraContext == null || extraContext.isBlank()) return rawText;
        return "【行程重點資訊】\n" + extraContext.trim() + "\n\n【原始行程文字】\n" + rawText;
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

        // 優先用這個項目自己判斷的國家/地區 (更精確, 例如多國行程裡每個景點不同國家),
        // 項目自己沒有的話才 fallback 用整份 AI 解析紀錄共用的國家/地區
        int ipid = getIpidByItem(APIID);
        AiImport aiImport = aiImportDAO.findById(ipid);
        String country = item.getItemCountry() != null ? item.getItemCountry()
                : (aiImport != null ? firstToken(aiImport.getSuggestedCountry()) : null);
        String region = item.getItemRegion() != null ? item.getItemRegion()
                : (aiImport != null ? firstToken(aiImport.getSuggestedRegion()) : null);

        Poi poi = new Poi(AID, category, item.getName(), country, region, null, null, null);

        // 地理編碼 (先試 Places API 準確定位, 找不到再 fallback Geocoding API)、AI 停留時間估算、
        // AI 景點介紹說明生成三個平行呼叫, 都是外部 API/AI 呼叫, 依序做的話要等三次網路來回。
        // 飛機/飯店早餐這類不需要座標的項目直接跳過地理編碼。
        java.util.concurrent.CompletableFuture<GoogleMapsClient.GeocodeResult> geoFuture;
        if (shouldGeocode(item.getItemType(), item.getTimeSlot(), item.getName())) {
            String geocodeQuery = String.join(" ", nonBlank(item.getName()), nonBlank(region), nonBlank(country)).trim();
            geoFuture = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                GoogleMapsClient.GeocodeResult r = googleMapsClient.findPlace(geocodeQuery, country);
                return r != null ? r : googleMapsClient.geocode(geocodeQuery, country);
            });
        } else {
            geoFuture = java.util.concurrent.CompletableFuture.completedFuture(null);
        }
        java.util.concurrent.CompletableFuture<Integer> stayFuture = (item.getStayMinutes() != null)
                ? java.util.concurrent.CompletableFuture.completedFuture(item.getStayMinutes())
                : java.util.concurrent.CompletableFuture.supplyAsync(() -> anthropicClient.estimateStayMinutes(item.getName(), category, null));
        // 自動生成景點介紹說明: 用 AI 解析時附帶的 note 當提示, 讓介紹更貼近這份行程實際提到的重點
        java.util.concurrent.CompletableFuture<String> descriptionFuture = java.util.concurrent.CompletableFuture.supplyAsync(
                () -> anthropicClient.generateDescription(item.getName(), category, country, region, item.getNote()));

        GoogleMapsClient.GeocodeResult geo = geoFuture.join();
        if (geo != null) {
            poi.setLatitude(BigDecimal.valueOf(geo.latitude));
            poi.setLongitude(BigDecimal.valueOf(geo.longitude));
        }
        poi.setSuggestedStayMin(stayFuture.join());

        // AI 生成失敗就 fallback 用原文的 note, 至少不要留白
        String generatedDescription = descriptionFuture.join();
        poi.setDescription(generatedDescription != null ? generatedDescription : item.getNote());

        poiDAO.save(poi);

        item.setMatchedPid(poi.getPID());
        aiParsedItemDAO.save(item);

        return poi;
    }

    // 判斷這個項目是否需要自動地理編碼: 飛機不用, 純飯店內早餐(沒有具體店名)也不用, 其餘一律要定位
    private boolean shouldGeocode(String itemType, String timeSlot, String name) {
        if ("transport".equals(itemType)) return false;
        if ("meal".equals(itemType) && "breakfast".equals(timeSlot)
                && (name == null || name.contains("飯店") || name.contains("早餐"))) {
            return false;
        }
        return true;
    }

    private String nonBlank(String s) {
        return s == null ? "" : s;
    }

    // 避免多國合併字串 (例如「印度、不丹」) 整包存進 POI 資料庫, 只取第一個當主要國家/地區
    private String firstToken(String value) {
        if (value == null) return null;
        String first = value.split("[、,/]")[0].trim();
        return first.isEmpty() ? null : first;
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
     * 編輯 AI 解析出來、還沒確認轉正式行程的項目 (讓線控可以在 review 頁面直接修正)
     */
    public void updateParsedItem(int APIID, String name, String itemType, String timeSlot,
                                 String note, Integer stayMinutes) {
        AiParsedItem item = aiParsedItemDAO.findById(APIID);
        if (item == null) throw new IllegalArgumentException("找不到這個項目");

        item.setName(name);
        item.setItemType(itemType);
        item.setTimeSlot(timeSlot == null || timeSlot.isBlank() ? null : timeSlot);
        item.setNote(note);
        item.setStayMinutes(stayMinutes);
        aiParsedItemDAO.save(item);
    }

    /**
     * 線控確認暫存資料無誤後, 轉成正式行程
     * (matchedPid 有值就直接連結公司 POI, 沒有就當自訂項目, 名稱先用 AI 解析出來的文字)
     */
    public Itinerary confirmImport(int IPID, String title, String country, String region) {
        AiImport aiImport = aiImportDAO.findById(IPID);
        if (aiImport == null) throw new IllegalArgumentException("找不到這筆 AI 解析紀錄");

        List<AiParsedDay> days = aiParsedDayDAO.findByImport(IPID);
        int daysCount = Math.max(1, days.size());

        Itinerary itinerary = itineraryService.createItinerary(
                aiImport.getAID(), aiImport.getCreatedBy(), title, country, region, daysCount, LocalDate.now());

        // 把使用者選擇的企劃書風格帶到正式行程上, 匯出企劃書時會套用同樣風格
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
                itineraryService.addItem(realDay.getIDID(), item.getMatchedPid(), item.getItemType(),
                        item.getName(), item.getStayMinutes(), item.getItemCountry(), item.getItemRegion(),
                        item.getTimeSlot());
            }

            // 套用預設規則: 早餐固定第一個、中午安排午餐、晚上安排晚餐 (只補沒時段的餐廳)、飯店固定排這天最後
            itineraryService.autoArrangeDay(realDay.getIDID());
        }

        aiImport.setStatus("confirmed");
        aiImport.setResultItineraryId(itinerary.getITID());
        aiImportDAO.save(aiImport);

        return itinerary;
    }
}