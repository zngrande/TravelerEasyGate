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
                  "name": "景點/餐廳/飯店的中文名稱(或原文語言, 找不到中文就用原文); item_type=transport 時可以留空字串, 顯示名稱會由系統依 transport_number/from_location/to_location 自動組成",
                  "name_en": "這個地點的英文名稱或該國常見的外文原名 (例如日文景點給日文原名、英文景點/連鎖店給英文), 原文裡有出現才填, 完全沒有就給 null; 不要自己音譯猜測",
                  "item_country": "這一個項目實際所在的國家 (不是整趟行程的國家, 是這一項自己的), 例如多國行程裡某個景點在「不丹」某個在「印度」就分別標註, 判斷不出來給 null",
                  "item_region": "這一個項目實際所在的地區/城市, 判斷不出來給 null",
                  "time_slot": "morning | noon | afternoon | evening | breakfast | lunch | dinner | null",
                  "note": "原文中的補充說明 (例如: 含早餐、五星飯店、注意事項等), 沒有就給空字串",
                  "stay_minutes": "預估在這個地方會停留幾分鐘 (數字, 15的倍數, 例如 90); attraction/meal 一定要給估計值, hotel/transport 給 null",
                  "transport_method": "只有 item_type=transport 才需要: 交通工具, 例如「飛機」「高鐵」「遊覽車」「渡輪」「計程車」, 判斷不出來就填「交通」, 其他 item_type 一律給 null",
                  "transport_number": "只有 item_type=transport 才需要: 航班/車次編號, 例如「CI100」「新幹線のぞみ23号」, 原文沒提到就給 null, 其他 item_type 一律給 null",
                  "from_location": "只有 item_type=transport 才需要: 出發地點 (機場/車站/飯店名稱等), 判斷不出來給 null, 其他 item_type 一律給 null",
                  "to_location": "只有 item_type=transport 才需要: 抵達地點, 判斷不出來給 null, 其他 item_type 一律給 null",
                  "departure_time": "只有 item_type=transport 才需要: 出發時間, 24小時制 HH:mm 格式 (例如 09:30), 原文沒提到就給 null, 其他 item_type 一律給 null",
                  "arrival_time": "只有 item_type=transport 才需要: 抵達時間, 24小時制 HH:mm 格式, 原文沒提到就給 null, 其他 item_type 一律給 null"
                }
              ]
            }
          ]
        }

        規則:
        - item_type=attraction 是景點/活動; meal 是餐食; hotel 是住宿; transport 是航班/高鐵/包車接送等
          交通移動資訊; highlight 是行銷亮點文案或注意事項(不屬於實體地點的敘述)。
        - 原文裡提到的機場接送、航班班次、高鐵車次、包車移動等交通資訊，都要拆解成 item_type=transport
          的項目 (依原文出現的位置放進對應的天數/順序即可)，不要略過、也不要只放進其他項目的 note 裡。
        - 依照原文出現的天數順序拆解，若原文沒有明確分天，你要合理推斷。
        - 名稱要精簡(例如「故宮博物院」而不是整句話)，細節放到 note。
        - stay_minutes 依常識估算: 大型景點/博物館約90~180分鐘, 一般景點約60~90分鐘,
          用餐約60~90分鐘, 如果原文有明確提到停留時間就用原文的。
        - 如果原文資訊不完整，盡力用你判斷合理的方式填, 不要留空必填欄位。
        - name_en 只在原文本身就有寫出英文/外文名稱時才填 (例如原文寫「硫磺山纜車 Banff Gondola」就填
          "Banff Gondola"), 用途是幫忙比對公司景點資料庫裡登記的原文別名, 提高比對命中率; 原文完全沒有
          外文名稱就給 null, 不要自己翻譯或音譯出一個名稱。
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
        return parseText(AID, UID, rawText, "text", "default");
    }

    /**
     * @param sourceType 記錄這份資料原始來源: text / pdf / docx (方便之後在列表分辨)
     */
    public AiImport parseText(int AID, int UID, String rawText, String sourceType) {
        return parseText(AID, UID, rawText, sourceType, "default");
    }

    /**
     * @param userStyle 使用者手動指定的風格 (wenqing/luxury/corporate/default)
     */
    public AiImport parseText(int AID, int UID, String rawText, String sourceType, String userStyle) {
        AiImport aiImport = new AiImport(AID, UID, sourceType, rawText);
        aiImportDAO.save(aiImport); // 先存一筆 pending, 拿到 IPID

        try {
            String jsonText = anthropicClient.complete(SYSTEM_PROMPT, rawText, 16000);
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
                    String itemType = itemNode.path("item_type").asText("attraction");
                    String name = itemNode.path("name").asText("");
                    String nameEn = emptyToNull(itemNode.path("name_en").asText(null));
                    boolean isTransport = "transport".equals(itemType);

                    // 交通項目是「從A到B」的移動資訊, 不是實體地點, 不會也不應該去比對公司 POI 資料庫
                    // (硬要比對反而可能誤配到同名的機場/車站景點資料)
                    Integer matchedPid = null;
                    if (!isTransport) {
                        // 比對範圍優先用這個項目自己判斷的國家 (比較精確, 多國行程時才不會跨國誤配),
                        // 項目自己沒有判斷出國家就退回整份行程共用的國家 (見下方 findMatchingPoi 的模糊比對說明)
                        String itemScopeCountry = emptyToNull(itemNode.path("item_country").asText(null));
                        matchedPid = findMatchingPoi(AID, name, nameEn,
                                itemScopeCountry != null ? itemScopeCountry : aiImport.getSuggestedCountry());
                    }

                    AiParsedItem item = new AiParsedItem(
                            day.getAPDID(),
                            itemType,
                            name,
                            emptyToNull(itemNode.path("time_slot").asText(null)),
                            itemNode.path("note").asText(""),
                            sortOrder++
                    );
                    item.setNameEn(nameEn);
                    item.setMatchedPid(matchedPid);

                    item.setItemCountry(emptyToNull(itemNode.path("item_country").asText(null)));
                    item.setItemRegion(emptyToNull(itemNode.path("item_region").asText(null)));

                    JsonNode stayNode = itemNode.path("stay_minutes");
                    if (stayNode.isNumber()) {
                        item.setStayMinutes(stayNode.asInt());
                    }

                    if (isTransport) {
                        item.setFromLocation(emptyToNull(itemNode.path("from_location").asText(null)));
                        item.setToLocation(emptyToNull(itemNode.path("to_location").asText(null)));
                        item.setTransportMethod(emptyToNull(itemNode.path("transport_method").asText(null)));
                        item.setTransportNumber(emptyToNull(itemNode.path("transport_number").asText(null)));
                        item.setDepartureTime(parseTimeOrNull(itemNode.path("departure_time").asText(null)));
                        item.setArrivalTime(parseTimeOrNull(itemNode.path("arrival_time").asText(null)));
                        // AI 通常會把 transport 項目的 name 留空 (顯示名稱交給系統組), review 頁面的
                        // 項目清單是直接顯示 item.getName(), 空字串會讓那一列看起來像壞掉的資料;
                        // 這裡先組一個跟正式匯入後 (buildFlightLabel) 同樣格式的顯示名稱存進去，
                        // 讓使用者在確認匯入之前就能看到「CI100 桃園國際機場 → 東京成田機場」這種完整資訊。
                        if (emptyToNull(name) == null) {
                            item.setName(buildTransportDisplayName(item.getTransportNumber(),
                                    item.getFromLocation(), item.getToLocation()));
                        }
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

    // 名稱相似度低於這個門檻就不採用模糊比對結果, 寧可比對不到讓使用者手動連結, 也不要誤配到不相關的地點
    // (例如「東京鐵塔」不應該被誤判成「晴空塔」)。0.62 是抓「新穗高纜車」vs 資料庫「新穗高高空纜車」這組
    // 使用者實際回報的案例算出來的相似度 (約 0.71) 抓一個留有餘裕、但不會鬆到隨便兩個名稱都能配對的門檻。
    private static final double FUZZY_NAME_MATCH_THRESHOLD = 0.62;

    // 用名稱比對公司 POI 資料庫, 找到就自動連結。
    // 使用者反映: AI 解析出來的地點名稱, 有時候跟公司資料庫裡實際登記的名稱用字不完全一樣, 但明顯是同一個
    // 地方 (例如 AI 解析出「新穗高纜車」, 但資料庫裡這筆景點登記的全名是「新穗高高空纜車」), 原本只有
    // LIKE '%關鍵字%' 這種「其中一邊要完整包含另一邊」的比對, 中間多了「高空」兩個字就直接比對不到、
    // 變成完全沒有自動連結建議, 使用者必須自己一個個手動找。
    // 修正: LIKE 包含比對抓不到時, 退而求其次改用「名稱相似度」(正規化 Levenshtein 編輯距離, 見下方
    // nameSimilarity()) 在候選範圍內找相似度最高的一筆, 相似度要超過門檻才採用 (避免誤配到不相關地點)。
    // 候選範圍優先限縮在「這個項目/整份行程判斷出的國家」內 (country 參數, 呼叫端會依序嘗試 item_country、
    // 整份 AI 解析紀錄的 suggestedCountry), 沒有任何國家資訊可用時才退回這間旅行社看得到的全部景點
    // (共用庫+自己的)。限縮範圍除了避免跨國誤配 (兩個國家可能剛好有相似命名的地點), 對大型資料庫來說
    // 也是必要的效能考量 (模糊比對是逐筆算相似度, 全表掃描在候選很多時會變慢)。
    private Integer findMatchingPoi(int AID, String name, String country) {
        return findMatchingPoi(AID, name, null, country);
    }

    // nameEn: AI 解析時原文附帶抽取出來的英文/外文名稱 (原文沒有就是 null)。
    // 使用者反映: 資料庫登記的中文名稱跟 AI 解析出來的中文名稱用字差太多時 (例如「硫磺山纜車」
    // vs 資料庫「班夫硫磺山景觀纜車」, 相似度只有 0.56, 低於門檻), 純中文比對完全比不到, 但兩邊
    // 其實常常共用同一個英文/外文原名 (都是 "Banff Gondola")。修正: 比對時中文 name 跟英文
    // nameEn 都分別去比對候選 POI 的 name 跟 original_name (原文別名, 通常存英文/外文拼寫),
    // 四種組合取最高分, 只要有一種組合超過門檻就採用。
    private Integer findMatchingPoi(int AID, String name, String nameEn, String country) {
        if (name == null || name.isBlank()) return null;
        List<Poi> matches = poiDAO.searchByKeyword(AID, name, null);
        if (!matches.isEmpty()) return matches.get(0).getPID();
        if (nameEn != null && !nameEn.isBlank()) {
            matches = poiDAO.searchByKeyword(AID, nameEn, null);
            if (!matches.isEmpty()) return matches.get(0).getPID();
        }

        List<Poi> candidates = (country != null && !country.isBlank())
                ? poiDAO.findByAgencyAndCountry(AID, country, null)
                : poiDAO.findByAgencyOrShared(AID);

        Poi best = null;
        double bestScore = 0;
        for (Poi candidate : candidates) {
            double score = nameSimilarity(name, candidate.getName());
            if (candidate.getOriginalName() != null && !candidate.getOriginalName().isBlank()) {
                score = Math.max(score, nameSimilarity(name, candidate.getOriginalName()));
                if (nameEn != null && !nameEn.isBlank()) {
                    score = Math.max(score, nameSimilarity(nameEn, candidate.getOriginalName()));
                }
            }
            if (nameEn != null && !nameEn.isBlank()) {
                score = Math.max(score, nameSimilarity(nameEn, candidate.getName()));
            }
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return (best != null && bestScore >= FUZZY_NAME_MATCH_THRESHOLD) ? best.getPID() : null;
    }

    // 正規化 Levenshtein 相似度, 範圍 0~1 (1 = 完全相同): 1 - (編輯距離 / 兩字串長度中較長的那個)。
    // 字元層級比對對中文地名特別合適 (不需要斷詞), 「插入/刪除幾個字但骨架相同」這種常見的命名差異
    // (新穗高纜車 vs 新穗高「高空」纜車) 差距越小分數就越高。
    //
    // 純 Levenshtein 對「短名稱前後被包了一圈地名/描述字」這種常見情況分數會偏低——例如「硫磺山纜車」
    // vs 資料庫登記的全名「班夫硫磺山景觀纜車」, 中間插入「班夫」「景觀」四個字, 相似度只有 0.56
    // (使用者實際回報案例), 但這兩個字串明顯是同一個地方, 只是資料庫存的是含地名/類型描述的全名。
    // 修正: 額外算一個「包含」分數來源, 取跟 Levenshtein 相似度兩者的最大值。這個包含分數依語言分兩種
    // 算法 (見下方說明), 都是「較短的名稱是否完整藏在較長的名稱裡面」的概念, 差別在中文/英文的斷詞方式
    // 天生不同, 用同一種演算法會顧此失彼:
    //
    //   1. 不含空白 (中文/日文等 CJK 地名通常沒有空白): 用「最長共同子序列 (LCS)」, 允許中間插入其他字,
    //      只要相對順序不變, 例如「硫磺山纜車」四個字依序出現在「班夫硫磺山景觀纜車」裡面即可, 不需要
    //      連續。只在較短字串至少 4 個字時才採用, 避免 2~3 個字的短名稱太容易「湊巧」在無關的長名稱裡
    //      湊出一樣的字序 (用「東京鐵塔」vs「晴空塔」這組之前特別提防的誤配案例驗證過, LCS 只有 1)。
    //
    //   2. 含空白 (通常是 name_en 這種英文/外文名稱): 使用者實際回報案例「Bow Falls」(弓河瀑布) 被誤配到
    //      「Montmorency Falls」(蒙特倫斯瀑布)——這兩個地方完全無關, 但用 LCS 字元層級跳著配對算出來
    //      相似度高達 0.78 (超過門檻), 因為兩者剛好共用了一個很常見的地形字尾 "Falls"。英文單字之間允許
    //      LCS 跳著配對風險太高 (常見字尾如 Falls/River/Lake/Point 到處都會出現), 所以改成「完整子字串
    //      包含比對」(大小寫不分), 一定要整段短名稱連續出現在長名稱裡面才算數, 不能跳著湊字元, 明顯嚴謹
    //      許多; 同樣只在較短字串至少 5 個字元時才採用, 避免單一個泛用字被到處誤配。
    private double nameSimilarity(String a, String b) {
        if (a == null || b == null) return 0;
        String s1 = a.trim();
        String s2 = b.trim();
        if (s1.isEmpty() || s2.isEmpty()) return 0;
        if (s1.equals(s2)) return 1.0;

        int distance = levenshteinDistance(s1, s2);
        int maxLen = Math.max(s1.length(), s2.length());
        double editSimilarity = maxLen == 0 ? 0 : 1.0 - ((double) distance / maxLen);

        int shorterLen = Math.min(s1.length(), s2.length());
        boolean multiWord = s1.indexOf(' ') >= 0 || s2.indexOf(' ') >= 0;
        double containmentSimilarity = 0;
        if (multiWord) {
            if (shorterLen >= 5) {
                String lower1 = s1.toLowerCase();
                String lower2 = s2.toLowerCase();
                if (lower1.contains(lower2) || lower2.contains(lower1)) {
                    containmentSimilarity = 0.9;
                }
            }
        } else if (shorterLen >= 4) {
            int lcsLen = longestCommonSubsequence(s1, s2);
            containmentSimilarity = (double) lcsLen / shorterLen;
        }

        return Math.max(editSimilarity, containmentSimilarity);
    }

    // 最長共同子序列 (Longest Common Subsequence) 長度: 允許中間插入其他字元, 只要相對順序不變,
    // 用來偵測「較短的名稱整串依序藏在較長的名稱裡面」(rolling array 版本, 只需要 O(min(m,n)) 額外空間)。
    private int longestCommonSubsequence(String s1, String s2) {
        int m = s1.length(), n = s2.length();
        int[] prev = new int[n + 1];
        int[] curr = new int[n + 1];
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    curr[j] = prev[j - 1] + 1;
                } else {
                    curr[j] = Math.max(prev[j], curr[j - 1]);
                }
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[n];
    }

    private int levenshteinDistance(String s1, String s2) {
        int[] prev = new int[s2.length() + 1];
        int[] curr = new int[s2.length() + 1];
        for (int j = 0; j <= s2.length(); j++) prev[j] = j;
        for (int i = 1; i <= s1.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= s2.length(); j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[s2.length()];
    }

    private String emptyToNull(String s) {
        return (s == null || s.isBlank() || "null".equalsIgnoreCase(s)) ? null : s;
    }

    // 跟 ItineraryService.buildFlightLabel() 同一套顯示格式 (那邊是私有方法, 服務不同、犯不著為了共用
    // 十幾行邏輯特地改成 public), 只在這裡給 review 頁面「還沒確認匯入之前」預先組一個看得懂的顯示名稱用:
    // 一律維持「交通：...」前綴 (有填編號則是「交通：CI100 出發地→目的地」)。
    private String buildTransportDisplayName(String transportNumber, String fromLocation, String toLocation) {
        boolean hasFrom = !isBlankStr(fromLocation);
        boolean hasTo = !isBlankStr(toLocation);
        String route;
        if (hasFrom && hasTo) route = fromLocation.trim() + " → " + toLocation.trim();
        else if (hasFrom) route = fromLocation.trim() + " 出發";
        else if (hasTo) route = "抵達 " + toLocation.trim();
        else route = null;

        String routeWithNumber;
        if (!isBlankStr(transportNumber)) {
            routeWithNumber = route != null ? (transportNumber.trim() + " " + route) : transportNumber.trim();
        } else {
            routeWithNumber = route;
        }

        return routeWithNumber != null ? ("交通：" + routeWithNumber) : "交通";
    }

    private boolean isBlankStr(String s) {
        return s == null || s.isBlank();
    }

    // AI 依提示詞要求輸出 24 小時制 "HH:mm" (也容錯 "HH:mm:ss"), 格式不對或沒填就當作沒有這個時間,
    // 不要讓整筆解析失敗——只是這個交通項目少了時間資訊而已, 使用者在 review 頁面還是能手動補。
    private java.time.LocalTime parseTimeOrNull(String time) {
        if (time == null || time.isBlank() || "null".equalsIgnoreCase(time)) return null;
        try {
            return java.time.LocalTime.parse(time.trim());
        } catch (Exception e) {
            return null;
        }
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
     * 給 review 頁面用: 顯示「已比對」的項目實際會採用哪一筆 POI 資料庫的正式名稱
     * (確認轉正式行程時就是用這個名稱, 不是 AI 解析出來的文字), 讓使用者確認前就能看到。
     */
    public String getMatchedPoiName(Integer matchedPid) {
        if (matchedPid == null) return null;
        Poi poi = poiDAO.findById(matchedPid);
        return poi != null ? poi.getName() : null;
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
        poi.setOriginalName(item.getNameEn());

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

    // AI 解析出的 item_type (英文) 對應到 POI 資料庫的 category (transport/highlight 沒有對應, 不能加入)。
    // 使用者要求 poi.category 維持中文, 要跟 ItineraryService.mapItemTypeToPoiCategory、
    // poi/new.html、poi/edit.html、poi/list.html 一致
    private String mapItemTypeToPoiCategory(String itemType) {
        if (itemType == null) return null;
        return switch (itemType) {
            case "attraction" -> "景點";
            case "meal" -> "餐廳";
            case "hotel" -> "飯店";
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
     * fromLocation/toLocation/transportMethod/transportNumber/departureTime/arrivalTime 只有
     * itemType=transport 才有意義, 其他類型傳了也不會有效果 (confirmImport() 只有 transport 才會讀取)。
     */
    public void updateParsedItem(int APIID, String name, String itemType, String timeSlot,
                                 String note, Integer stayMinutes,
                                 String fromLocation, String toLocation,
                                 String transportMethod, String transportNumber,
                                 String departureTime, String arrivalTime) {
        AiParsedItem item = aiParsedItemDAO.findById(APIID);
        if (item == null) throw new IllegalArgumentException("找不到這個項目");

        item.setName(name);
        item.setItemType(itemType);
        item.setTimeSlot(timeSlot == null || timeSlot.isBlank() ? null : timeSlot);
        item.setNote(note);
        item.setStayMinutes(stayMinutes);

        item.setFromLocation(emptyToNull(fromLocation));
        item.setToLocation(emptyToNull(toLocation));
        item.setTransportMethod(emptyToNull(transportMethod));
        item.setTransportNumber(emptyToNull(transportNumber));
        item.setDepartureTime(parseTimeOrNull(departureTime));
        item.setArrivalTime(parseTimeOrNull(arrivalTime));

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
                if ("transport".equals(item.getItemType())) {
                    // 交通項目 (航班/高鐵/包車等): 不連結 POI, 直接用跟「建立新行程」手動填去程/回程班機
                    // 一致的方式組成項目 (ItineraryService.addTransportItem → buildFlightLabel), 顯示格式
                    // 統一是「航班/車次編號 出發地→目的地」(沒填編號就退回「交通：出發地→目的地」)。
                    itineraryService.addTransportItem(realDay.getIDID(), "交通",
                            item.getTransportMethod(), item.getTransportNumber(),
                            item.getFromLocation(), item.getToLocation(),
                            item.getDepartureTime(), item.getArrivalTime(),
                            item.getNote());
                    continue;
                }

                // 使用者反映: review 頁面顯示「已比對」, 但轉成正式行程後項目名稱還是 AI 自己解析出來的文字,
                // 不是資料庫裡登記的正式名稱。原因: 這裡原本不管有沒有比對到 POI, 一律用 item.getName()
                // (AI 生成的文字) 當顯示名稱。修正: 有比對到 POI 的話, 改用該筆 POI 資料庫裡的正式名稱,
                // 真正做到「已比對=採用資料庫資料」, 沒比對到才維持用 AI 解析出來的文字。
                String displayName = item.getName();
                if (item.getMatchedPid() != null) {
                    Poi matchedPoi = poiDAO.findById(item.getMatchedPid());
                    if (matchedPoi != null && matchedPoi.getName() != null && !matchedPoi.getName().isBlank()) {
                        displayName = matchedPoi.getName();
                    }
                }
                itineraryService.addItem(realDay.getIDID(), item.getMatchedPid(), item.getItemType(),
                        displayName, item.getStayMinutes(), item.getItemCountry(), item.getItemRegion(),
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