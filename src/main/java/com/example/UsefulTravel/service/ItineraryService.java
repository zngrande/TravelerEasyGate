package com.example.UsefulTravel.service;

import com.example.UsefulTravel.DAO.AiImportDAO;
import com.example.UsefulTravel.DAO.ItineraryDAO;
import com.example.UsefulTravel.DAO.ItineraryDayDAO;
import com.example.UsefulTravel.DAO.ItineraryItemDAO;
import com.example.UsefulTravel.DAO.ItineraryItemOptionDAO;
import com.example.UsefulTravel.DAO.PoiDAO;
import com.example.UsefulTravel.DAO.RouteSegmentDAO;
import com.example.UsefulTravel.entity.Itinerary;
import com.example.UsefulTravel.entity.ItineraryDay;
import com.example.UsefulTravel.entity.ItineraryItem;
import com.example.UsefulTravel.entity.ItineraryItemOption;
import com.example.UsefulTravel.entity.Poi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ItineraryService - 行程建立與積木式排版的核心跨表邏輯
 */
@Service
public class ItineraryService {

    private final ItineraryDAO itineraryDAO;
    private final ItineraryDayDAO itineraryDayDAO;
    private final ItineraryItemDAO itineraryItemDAO;
    private final ItineraryItemOptionDAO itineraryItemOptionDAO;
    private final RouteSegmentDAO routeSegmentDAO;
    private final RouteService routeService;
    private final PoiDAO poiDAO;
    private final AiImportDAO aiImportDAO;
    private final GoogleMapsClient googleMapsClient;
    private final AnthropicClient anthropicClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public ItineraryService(ItineraryDAO itineraryDAO, ItineraryDayDAO itineraryDayDAO,
                            ItineraryItemDAO itineraryItemDAO, ItineraryItemOptionDAO itineraryItemOptionDAO,
                            RouteSegmentDAO routeSegmentDAO,
                            RouteService routeService, PoiDAO poiDAO, AiImportDAO aiImportDAO,
                            GoogleMapsClient googleMapsClient, AnthropicClient anthropicClient,
                            ObjectMapper objectMapper) {
        this.itineraryDAO = itineraryDAO;
        this.itineraryDayDAO = itineraryDayDAO;
        this.itineraryItemDAO = itineraryItemDAO;
        this.itineraryItemOptionDAO = itineraryItemOptionDAO;
        this.routeSegmentDAO = routeSegmentDAO;
        this.routeService = routeService;
        this.poiDAO = poiDAO;
        this.aiImportDAO = aiImportDAO;
        this.googleMapsClient = googleMapsClient;
        this.anthropicClient = anthropicClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 建立新行程, 依 daysCount 自動產生 Day1 ~ DayN 空白骨架
     */
    public Itinerary createItinerary(int AID, int createdBy, String title, String country,
                                     int daysCount, LocalDate startDate) {
        return createItinerary(AID, createdBy, title, country, null, daysCount, startDate);
    }

    public Itinerary createItinerary(int AID, int createdBy, String title, String country, String region,
                                     int daysCount, LocalDate startDate) {
        Itinerary itinerary = new Itinerary(AID, createdBy, title, country, daysCount);
        itinerary.setRegion(region);
        itinerary.setStartDate(startDate);
        if (startDate != null) {
            itinerary.setEndDate(startDate.plusDays(daysCount - 1));
        }
        itineraryDAO.save(itinerary);

        for (int d = 1; d <= daysCount; d++) {
            LocalDate dayDate = startDate != null ? startDate.plusDays(d - 1) : null;
            itineraryDayDAO.save(new ItineraryDay(itinerary.getITID(), d, dayDate, null));
        }
        return itinerary;
    }

    public List<ItineraryDay> getDays(int ITID) {
        return itineraryDayDAO.findByItinerary(ITID);
    }

    /**
     * 「AI 安排行程」: 跟一般建立行程一樣先產生 Day1~DayN 骨架, 但接著會用 AI 從「這個國家/地區在公司
     * POI 資料庫裡已有的景點/餐廳/飯店」中挑選、安排進每一天, 不是憑空生出資料庫沒有的地點。
     * 用在「建立新行程」頁面的「AI 安排行程」按鈕 (跟旁邊「建立行程並進入看板」的差別只在於這個會先幫忙排好初稿)。
     *
     * @return 建好的 Itinerary。如果這個國家/地區在資料庫裡完全找不到候選景點、或 AI 呼叫失敗,
     *         仍然會回傳建立好的行程 (退回成跟原本一樣的空白行程), 呼叫端可以用 hasAnyItem() 判斷要不要提示使用者。
     */
    public Itinerary createItineraryWithAiPlan(int AID, int createdBy, String title, String country, String region,
                                               int daysCount, LocalDate startDate) {
        Itinerary itinerary = createItinerary(AID, createdBy, title, country, region, daysCount, startDate);

        List<Poi> candidates = poiDAO.findByAgencyAndCountry(AID, country, region);
        if (candidates.isEmpty()) return itinerary; // 資料庫裡沒有符合國家/地區的景點, 保持空白行程讓使用者自己排

        List<ItineraryDay> days = itineraryDayDAO.findByItinerary(itinerary.getITID());

        try {
            Map<Integer, List<Integer>> plan = planDaysWithAi(country, region, daysCount, candidates);
            if (plan.isEmpty()) return itinerary; // AI 沒排出任何結果, 一樣退回空白行程

            Map<Integer, Poi> candidateByPid = new HashMap<>();
            for (Poi poi : candidates) candidateByPid.put(poi.getPID(), poi);

            for (ItineraryDay day : days) {
                List<Integer> pids = plan.get(day.getDayNumber());
                if (pids == null) continue;
                for (Integer pid : pids) {
                    Poi poi = candidateByPid.get(pid); // AI 幻覺出資料庫沒有的 PID 就直接跳過, 不要硬加
                    if (poi == null) continue;
                    addItem(day.getIDID(), poi.getPID(), mapPoiCategoryToItemType(poi.getCategory()),
                            poi.getName(), poi.getSuggestedStayMin());
                }
            }

            // 資料庫裡這個國家/地區如果完全沒有餐廳/飯店類的候選景點, AI 當然也排不出真正的早/午/晚餐跟住宿。
            // 這種情況不要放著不管 (時間軸看起來像漏排), 也不要硬找不相關的地點湊數, 改成先補一個純文字的
            // 預留項目 (早餐/午餐/晚餐/住宿), 不連結 POI、不查座標、不顯示在地圖上, 讓線控之後自己換成真正的地點。
            boolean hasRestaurant = candidates.stream().anyMatch(p -> "restaurant".equals(p.getCategory()));
            boolean hasHotel = candidates.stream().anyMatch(p -> "hotel".equals(p.getCategory()));
            if (!hasRestaurant || !hasHotel) {
                for (ItineraryDay day : days) {
                    if (!hasRestaurant) {
                        addPlaceholderItem(day.getIDID(), "meal", "早餐");
                        addPlaceholderItem(day.getIDID(), "meal", "午餐");
                        addPlaceholderItem(day.getIDID(), "meal", "晚餐");
                    }
                    if (!hasHotel) {
                        addPlaceholderItem(day.getIDID(), "hotel", "住宿");
                    }
                }
            }

            // 加完之後跑一次自動整理 (meal_time 模式): 依序標出早/中/晚餐、住宿排到當天最後面, 排序更像正常行程
            autoArrangeItinerary(itinerary.getITID(), "meal_time");
        } catch (Exception e) {
            // AI 沒設定 API Key / 呼叫失敗 / 回應解析失敗都不應該讓「建立行程」整個失敗,
            // 保留已經建立好的空白行程骨架, 讓使用者可以照原本流程手動編排
        }

        return itinerary;
    }

    // 把資料庫裡的 POI 分類 (attraction/restaurant/hotel/rest_stop/airport/transport/shopping) 轉成
    // 行程項目的分類 (attraction/meal/hotel/...), 跟前端地圖上「點灰色建議標記直接加入行程」用的判斷邏輯一致
    private String mapPoiCategoryToItemType(String poiCategory) {
        if (poiCategory == null) return "attraction";
        return switch (poiCategory) {
            case "restaurant" -> "meal";
            case "hotel" -> "hotel";
            default -> "attraction";
        };
    }

    // 資料庫裡完全沒有符合的餐廳/飯店時, 用這個補一個純文字的預留項目 (早餐/午餐/晚餐/住宿):
    // 不連結 POI (PID=null)、完全不查座標、不顯示在地圖上 —— 只是先佔住這個位置, 之後線控可以自己換成真正的地點
    private void addPlaceholderItem(int IDID, String itemType, String customName) {
        List<ItineraryItem> existing = itineraryItemDAO.findByDay(IDID);
        int nextOrder = existing.size();
        ItineraryItem item = new ItineraryItem(IDID, null, itemType, customName, nextOrder);
        item.setShowOnMap(false);
        itineraryItemDAO.save(item);
    }

    // 把候選景點清單丟給 AI, 請它只從清單裡挑選 PID 並安排每一天要去哪些, 回傳 {天數 -> [PID,...]}
    private Map<Integer, List<Integer>> planDaysWithAi(String country, String region, int daysCount, List<Poi> candidates) throws Exception {
        String system = """
            你是旅遊行程規劃助手, 負責幫旅行社從「已有的景點/餐廳/飯店資料庫」裡挑選並安排出一份 N 天的行程初稿。
            使用者會給你這個國家/地區在資料庫裡「所有可用」的候選清單 (每筆有 pid / name / category / stay_min),
            以及總共要安排幾天。你的任務是決定每一天要排哪幾個地點、排幾個, 只能使用候選清單裡出現過的 pid,
            絕對不可以自己生出候選清單沒有的地點或 pid。

            規則:
            - category=景點 的排每天 2~4 個當作主要行程; category=餐廳 的每天安排 1~3 個 (盡量涵蓋午餐/晚餐);
              category=飯店 的每天最多安排 1 個 (如果只有一間飯店, 每天都排同一間也沒關係, 代表這幾天都住這裡)。
            - 同一個 pid 不要在同一天重複出現; 不同天之間, 如果 景點/餐廳 數量足夠, 盡量不要重複,
              但如果候選數量比行程天數少, 允許合理重複使用, 不要留空某一天。
            - 每個地點只放在最適合的一天就好, 不要漏掉候選清單裡看起來明顯必去的知名景點。
            - 只能輸出一個 JSON 物件, 不要有任何其他文字, 不要用 markdown code fence 包起來, 格式如下:
              {"days":[{"day":1,"pids":[12,7,45]},{"day":2,"pids":[3,9]}]}
              day 是第幾天 (從 1 開始), pids 是這一天依造訪順序排列的候選 pid 陣列。
            """;

        StringBuilder userContent = new StringBuilder();
        userContent.append("國家: ").append(country != null ? country : "未指定");
        userContent.append("\n地區: ").append(region != null && !region.isBlank() ? region : "未指定");
        userContent.append("\n總天數: ").append(daysCount);
        userContent.append("\n候選景點清單 (JSON 陣列, 每筆是 pid/name/category/stay_min):\n");
        userContent.append("[");
        for (int i = 0; i < candidates.size(); i++) {
            Poi poi = candidates.get(i);
            if (i > 0) userContent.append(",");
            userContent.append("{\"pid\":").append(poi.getPID())
                    .append(",\"name\":\"").append(poi.getName() != null ? poi.getName().replace("\"", "") : "")
                    .append("\",\"category\":\"").append(poi.getCategory() != null ? poi.getCategory() : "attraction")
                    .append("\",\"stay_min\":").append(poi.getSuggestedStayMin() != null ? poi.getSuggestedStayMin() : 60)
                    .append("}");
        }
        userContent.append("]");

        String response = anthropicClient.complete(system, userContent.toString(), 4000);
        JsonNode root = objectMapper.readTree(stripCodeFence(response));

        Map<Integer, List<Integer>> plan = new HashMap<>();
        for (JsonNode dayNode : root.path("days")) {
            int dayNumber = dayNode.path("day").asInt();
            List<Integer> pids = new ArrayList<>();
            for (JsonNode pidNode : dayNode.path("pids")) {
                pids.add(pidNode.asInt());
            }
            plan.put(dayNumber, pids);
        }
        return plan;
    }

    // Claude 有時仍會習慣性包 ```json ... ``` , 保險起見去掉 (跟 AiParseService 同樣的處理方式)
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

    public Itinerary getItinerary(int ITID) {
        return itineraryDAO.findById(ITID);
    }

    // ------------------------------------------------------------
    // 行程上鎖 (需求文件 2.2「行程是否上鎖（供他人編輯）」)
    // ------------------------------------------------------------

    /** 上鎖：只有還沒上鎖時才能鎖, 避免蓋掉別人剛上的鎖。回傳 false 代表已經被別人鎖住了。 */
    public boolean lockItinerary(int ITID, int UID) {
        Itinerary itinerary = itineraryDAO.findById(ITID);
        if (itinerary == null) return false;
        if (itinerary.isLocked() && itinerary.getLockedBy() != null && itinerary.getLockedBy() != UID) {
            return false; // 已經被別人鎖住
        }
        itinerary.setLocked(true);
        itinerary.setLockedBy(UID);
        itinerary.setLockedAt(java.time.LocalDateTime.now());
        itineraryDAO.save(itinerary);
        return true;
    }

    /** 解鎖：任何有編輯權限的人都能解鎖 (例如原本上鎖的人下線忘記解鎖, 主管可以強制解鎖)。 */
    public void unlockItinerary(int ITID) {
        Itinerary itinerary = itineraryDAO.findById(ITID);
        if (itinerary == null) return;
        itinerary.setLocked(false);
        itinerary.setLockedBy(null);
        itinerary.setLockedAt(null);
        itineraryDAO.save(itinerary);
    }

    /**
     * 檢查這個行程目前是否可以被 UID 編輯：沒上鎖，或上鎖的人就是自己。
     * 供 controller 在寫入動作前擋一下, 避免兩人同時改同一個行程互相覆蓋。
     */
    public boolean isEditableBy(int ITID, int UID) {
        Itinerary itinerary = itineraryDAO.findById(ITID);
        if (itinerary == null) return false;
        return !itinerary.isLocked() || itinerary.getLockedBy() == null || itinerary.getLockedBy() == UID;
    }

    /** 依 IDID (某一天) 反查所屬的 ITID, 給只帶 IDID 的 API 用來檢查上鎖狀態。 */
    public Integer getItineraryIdByDay(int IDID) {
        ItineraryDay day = itineraryDayDAO.findById(IDID);
        return day != null ? day.getITID() : null;
    }

    // 給「AI 安排行程」用: 建立完後檢查有沒有真的排到任何項目, 沒有的話 controller 要提示使用者手動編排
    public boolean hasAnyItem(int ITID) {
        for (ItineraryDay day : itineraryDayDAO.findByItinerary(ITID)) {
            if (!itineraryItemDAO.findByDay(day.getIDID()).isEmpty()) return true;
        }
        return false;
    }

    public void deleteItinerary(int ITID) {
        aiImportDAO.clearResultItinerary(ITID); // 先解除外鍵參照, 不然刪除會被擋
        // 同樣道理: route_segment 對 itinerary_item 的外鍵沒設 CASCADE,
        // 要先把這個行程底下每一天算過的拉車距離快取清掉, 不然整串 CASCADE 刪除會在 itinerary_item 這關被擋住
        for (ItineraryDay day : itineraryDayDAO.findByItinerary(ITID)) {
            routeSegmentDAO.deleteByDay(day.getIDID());
        }
        itineraryDAO.deleteById(ITID);
    }

    // 更新某一天的出發時間 (時間軸看板的起點, 例如「早上7點出發」)
    public void updateDayStartTime(int IDID, java.time.LocalTime startTime) {
        ItineraryDay day = itineraryDayDAO.findById(IDID);
        if (day != null) {
            day.setStartTime(startTime);
            itineraryDayDAO.save(day);
        }
        // 出發時間改變的話, 中午/晚上的目標時刻也跟著變了, 已經排過早午晚餐的位置要重新算一次
        // (不重新指定早/午/晚餐的標籤, 只是依「目前已經有的標籤」重新算插入位置)
        repositionMealsByExistingTimeSlot(IDID, startTime);
    }

    // 依「這天目前的出發時間」跟每個餐廳「已經有的時段標籤」(早/午/晚餐), 重新計算它們該插入的位置。
    // 不會動到還沒有時段標籤的餐廳 (那些要靠 autoArrangeDay 才會被指派標籤跟位置)。
    private void repositionMealsByExistingTimeSlot(int IDID, java.time.LocalTime dayStart) {
        List<ItineraryItem> items = itineraryItemDAO.findByDay(IDID);
        if (items.isEmpty()) return;
        if (dayStart == null) dayStart = java.time.LocalTime.of(9, 0);

        List<ItineraryItem> hotels = new ArrayList<>();
        List<ItineraryItem> labeledMeals = new ArrayList<>(); // 已經有早/午/晚餐標籤的餐廳, 要重新定位
        List<ItineraryItem> anchors = new ArrayList<>();      // 其餘項目 (含還沒標籤的餐廳), 維持原順序當骨架

        for (ItineraryItem item : items) {
            if ("hotel".equals(item.getItemType())) {
                hotels.add(item);
            } else if ("meal".equals(item.getItemType()) && !isBlank(item.getTimeSlot())
                    && (item.getTimeSlot().equals("breakfast") || item.getTimeSlot().equals("lunch") || item.getTimeSlot().equals("dinner"))) {
                labeledMeals.add(item);
            } else {
                anchors.add(item);
            }
        }

        if (labeledMeals.isEmpty()) return; // 沒有已標籤的餐廳需要重新定位, 不用動排序

        List<ItineraryItem> arranged = new ArrayList<>(anchors);
        for (ItineraryItem meal : labeledMeals) {
            int insertIndex;
            if ("breakfast".equals(meal.getTimeSlot())) {
                insertIndex = 0;
            } else {
                java.time.LocalTime target = "lunch".equals(meal.getTimeSlot())
                        ? java.time.LocalTime.of(12, 0) : java.time.LocalTime.of(18, 0);
                insertIndex = findIndexForTargetTime(arranged, dayStart, target);
            }
            arranged.add(Math.min(insertIndex, arranged.size()), meal);
        }
        arranged.addAll(hotels);

        for (int i = 0; i < arranged.size(); i++) {
            arranged.get(i).setSortOrder(i);
            itineraryItemDAO.save(arranged.get(i));
        }

        recalculateRoutes(IDID);
    }

    // 切換這天的交通方式 (auto=AI依距離自動推薦 / driving=全部開車 / walking=全部走路)
    // 這是「整天強制套用」的動作, 選 driving/walking 時會蓋掉每一段個別選過的通勤方式; 選 auto 則交還給 AI 重新判斷
    public void updateDayTransportMode(int IDID, String transportMode) {
        ItineraryDay day = itineraryDayDAO.findById(IDID);
        if (day != null) {
            String normalized = "walking".equalsIgnoreCase(transportMode) ? "walking"
                    : "auto".equalsIgnoreCase(transportMode) ? "auto" : "driving";
            day.setTransportMode(normalized);
            itineraryDayDAO.save(day);
            recalculateRoutes(IDID, false); // false = 不保留每段的手動覆寫, 全部依新設定重算
        }
    }

    public List<ItineraryItem> getItems(int IDID) {
        return itineraryItemDAO.findByDay(IDID);
    }

    // 給看板顯示「兩點之間拉車距離/時間」與迴頭路警示用
    public List<com.example.UsefulTravel.entity.RouteSegment> getRoutes(int IDID) {
        return routeSegmentDAO.findByDay(IDID);
    }

    // 更新這個行程匯出企劃書時要套用的模板風格 (wenqing/luxury/corporate/default)
    public void updateTemplateStyle(int ITID, String style) {
        Itinerary itinerary = itineraryDAO.findById(ITID);
        if (itinerary != null) {
            itinerary.setTemplateStyle(style);
            itineraryDAO.save(itinerary);
        }
    }

    // 行程排版看板按下「完成行程」: 把狀態改成 completed, 首頁「已完成行程」統計會反映這筆
    public void markCompleted(int ITID) {
        Itinerary itinerary = itineraryDAO.findById(ITID);
        if (itinerary == null) throw new IllegalArgumentException("找不到這個行程");
        itinerary.setStatus("completed");
        itineraryDAO.save(itinerary);
    }

    /**
     * 把排版看板上「還沒連結 POI」的項目 (item.PID == null, 通常是手動打字加的自訂項目)
     * 寫進公司 POI 資料庫 (時間預設 NULL), 寫完後自動把這個項目連結到新建立的 POI
     *
     * @return 新建立的 Poi
     */
    public Poi addItemToPoi(int AID, int IIID) {
        ItineraryItem item = itineraryItemDAO.findById(IIID);
        if (item == null) throw new IllegalArgumentException("找不到這個項目");
        if (item.getPID() != null) throw new IllegalStateException("這個項目已經連結 POI 資料庫了");

        String category = mapItemTypeToPoiCategory(item.getItemType());
        if (category == null) {
            throw new IllegalArgumentException("「" + typeDisplayName(item.getItemType()) + "」不是景點/餐廳/住宿類型，無法加入 POI 資料庫");
        }

        String country = item.getItemCountry(), region = item.getItemRegion();
        if (country == null) {
            ItineraryDay day = itineraryDayDAO.findById(item.getIDID());
            if (day != null) {
                Itinerary parentItinerary = itineraryDAO.findById(day.getITID());
                if (parentItinerary != null) {
                    country = firstToken(parentItinerary.getCountry());
                    region = region != null ? region : firstToken(parentItinerary.getRegion());
                }
            }
        }

        Poi poi = new Poi(AID, category, item.getCustomName(), country, region, null, null, null);

        // 地理編碼跟 AI 生成介紹說明平行呼叫, 不要依序做 (這是「加入景點資料庫」按鈕之前很慢的主因:
        // 地理編碼本身可能就要試兩次 Google API, 加上又要再等一次 AI 生成介紹, 依序做形同疊加兩次網路等待時間)
        final String finalCountry = country, finalRegion = region;
        boolean alreadyHasCoord = item.getLatitude() != null && item.getLongitude() != null;
        java.util.concurrent.CompletableFuture<GoogleMapsClient.GeocodeResult> geoFuture;
        if (!alreadyHasCoord && shouldGeocode(item.getItemType(), item.getTimeSlot(), item.getCustomName())) {
            geoFuture = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                String query = String.join(" ", item.getCustomName(), finalRegion != null ? finalRegion : "", finalCountry != null ? finalCountry : "");
                GoogleMapsClient.GeocodeResult r = googleMapsClient.findPlace(query, finalCountry);
                return r != null ? r : googleMapsClient.geocode(query, finalCountry);
            });
        } else {
            geoFuture = java.util.concurrent.CompletableFuture.completedFuture(null);
        }
        java.util.concurrent.CompletableFuture<String> descriptionFuture = java.util.concurrent.CompletableFuture.supplyAsync(
                () -> anthropicClient.generateDescription(item.getCustomName(), category, finalCountry, finalRegion, item.getNote()));

        if (alreadyHasCoord) {
            poi.setLatitude(item.getLatitude());
            poi.setLongitude(item.getLongitude());
        } else {
            GoogleMapsClient.GeocodeResult geo = geoFuture.join();
            if (geo != null) {
                poi.setLatitude(BigDecimal.valueOf(geo.latitude));
                poi.setLongitude(BigDecimal.valueOf(geo.longitude));
            }
        }

        // 自動生成景點介紹說明: 用這個項目跟著行程的備註當提示; AI 生成失敗就 fallback 用原本的備註, 至少不要留白
        String generatedDescription = descriptionFuture.join();
        poi.setDescription(generatedDescription != null ? generatedDescription : item.getNote());

        poiDAO.save(poi);
        item.setPID(poi.getPID());
        if (poi.getLatitude() != null) {
            item.setLatitude(poi.getLatitude());
            item.setLongitude(poi.getLongitude());
        }
        itineraryItemDAO.save(item);
        return poi;
    }

    // AI/手動輸入的 item_type 對應到 POI 資料庫的 category (transport/highlight 不是實體地點, 不能加入)
    // 這裡要用跟 poi/new.html、PoiController、AiParseService 一致的英文 category 值 (attraction/
    // restaurant/hotel), 不能用中文, 不然新建立的 POI 會跟畫面上的類型篩選/自動完成對不上
    private String mapItemTypeToPoiCategory(String itemType) {
        if (itemType == null) return null;
        return switch (itemType) {
            case "attraction" -> "attraction";
            case "meal" -> "restaurant";
            case "hotel" -> "hotel";
            default -> null;
        };
    }

    private String typeDisplayName(String itemType) {
        if (itemType == null) return "此項目";
        return switch (itemType) {
            case "transport" -> "交通";
            case "optional" -> "自費項目";
            case "free_time" -> "自由活動";
            default -> itemType;
        };
    }

    // 避免使用者手動輸入或 AI 判斷出「印度、不丹」這種多國合併字串時整包存進 POI 資料庫,
    // 只取第一個當作主要國家/地區
    private String firstToken(String value) {
        if (value == null) return null;
        String first = value.split("[、,/]")[0].trim();
        return first.isEmpty() ? null : first;
    }

    /**
     * 把一個 POI (或自訂項目) 加到某一天的行程尾端
     */
    public ItineraryItem addItem(int IDID, Integer PID, String itemType, String customName) {
        return addItem(IDID, PID, itemType, customName, null, null, null);
    }

    /**
     * @param stayDurationMin AI 預估的停留時間(分鐘), 沒有就傳 null (時間軸會用預設值)
     */
    public ItineraryItem addItem(int IDID, Integer PID, String itemType, String customName, Integer stayDurationMin) {
        return addItem(IDID, PID, itemType, customName, stayDurationMin, null, null);
    }

    /**
     * @param itemCountry AI 解析時針對這個項目自己判斷出的國家 (比行程層級的國家精確, 例如多國行程)
     * @param itemRegion  AI 解析時針對這個項目自己判斷出的地區/城市
     */
    public ItineraryItem addItem(int IDID, Integer PID, String itemType, String customName, Integer stayDurationMin,
                                 String itemCountry, String itemRegion) {
        return addItem(IDID, PID, itemType, customName, stayDurationMin, itemCountry, itemRegion, null);
    }

    /**
     * @param timeSlot AI 解析或手動指定的時段 (breakfast/lunch/dinner/morning/noon/afternoon/evening), 沒有就傳 null
     */
    public ItineraryItem addItem(int IDID, Integer PID, String itemType, String customName, Integer stayDurationMin,
                                 String itemCountry, String itemRegion, String timeSlot) {
        List<ItineraryItem> existing = itineraryItemDAO.findByDay(IDID);
        int nextOrder = existing.size();

        ItineraryItem item = new ItineraryItem(IDID, PID, itemType, customName, nextOrder);
        item.setStayDurationMin(stayDurationMin);
        item.setItemCountry(itemCountry);
        item.setItemRegion(itemRegion);
        item.setTimeSlot(timeSlot);

        // 有連結 POI 的話, 直接把座標也複製到項目自己身上 (跟自訂項目走同一套地圖邏輯, 不用每次都查 Poi 表)
        if (PID != null) {
            Poi poi = poiDAO.findById(PID);
            if (poi != null && poi.getLatitude() != null) {
                item.setLatitude(poi.getLatitude());
                item.setLongitude(poi.getLongitude());
            }
        } else if (shouldGeocode(itemType, timeSlot, customName)) {
            // 沒有連結 POI (通常是 AI 解析出來、但公司資料庫裡還沒有的新景點) 也要自動查座標,
            // 不然這種項目在地圖上永遠不會出現。邏輯跟 addCustomItem 一致: 先試 Places API 找地點,
            // 找不到再退回一般地理編碼。
            String query = String.join(" ", customName != null ? customName : "",
                    itemRegion != null ? itemRegion : "", itemCountry != null ? itemCountry : "").trim();
            GoogleMapsClient.GeocodeResult geo = googleMapsClient.findPlace(query, itemCountry);
            if (geo == null) {
                geo = googleMapsClient.geocode(query, itemCountry);
            }
            if (geo != null) {
                item.setLatitude(BigDecimal.valueOf(geo.latitude));
                item.setLongitude(BigDecimal.valueOf(geo.longitude));
            }
        }

        itineraryItemDAO.save(item);

        recalculateRoutes(IDID);
        return item;
    }

    /**
     * 新增「自訂項目」: 不連結公司 POI 資料庫, 但會自動地理編碼取得座標, 讓地圖照樣顯示這個點
     * 支援名稱用「或」分隔多個選項 (例如飯店常見的「A飯店或B飯店或C飯店」), 每個候選都會分別地理編碼,
     * 預設選第一個, 之後可以用 selectItemOption() 切換
     */
    public ItineraryItem addCustomItem(int IDID, String itemType, String rawName, Integer stayDurationMin, String locationHint) {
        String[] candidates = rawName.split("或");
        List<String> names = new ArrayList<>();
        for (String c : candidates) {
            String trimmed = c.trim();
            if (!trimmed.isEmpty()) names.add(trimmed);
        }
        if (names.isEmpty()) names.add(rawName.trim());

        // 拿這個項目所在行程的國家/地區, 讓地理編碼查詢更準確
        String country = null, region = null;
        ItineraryDay day = itineraryDayDAO.findById(IDID);
        if (day != null) {
            Itinerary itinerary = itineraryDAO.findById(day.getITID());
            if (itinerary != null) {
                country = itinerary.getCountry();
                region = itinerary.getRegion();
            }
        }

        List<ItineraryItem> existing = itineraryItemDAO.findByDay(IDID);
        int nextOrder = existing.size();

        ItineraryItem item = new ItineraryItem(IDID, null, itemType, names.get(0), nextOrder);
        item.setStayDurationMin(stayDurationMin);
        itineraryItemDAO.save(item); // 先存起來拿 IIID

        boolean first = true;
        for (String name : names) {
            GoogleMapsClient.GeocodeResult geo = null;

            if (!shouldGeocode(itemType, null, name)) {
                // 飛機/飯店早餐：不查座標，直接跳過
            } else {
                if (first && locationHint != null && !locationHint.isBlank()) {
                    geo = googleMapsClient.resolveLocationHint(locationHint);
                }
                if (geo == null) {
                    String query = String.join(" ", name, region != null ? region : "", country != null ? country : "").trim();
                    geo = googleMapsClient.findPlace(query, country);
                    if (geo == null) {
                        geo = googleMapsClient.geocode(query, country);
                    }
                }
            }

            BigDecimal lat = geo != null ? BigDecimal.valueOf(geo.latitude) : null;
            BigDecimal lng = geo != null ? BigDecimal.valueOf(geo.longitude) : null;

            itineraryItemOptionDAO.save(new ItineraryItemOption(item.getIIID(), name, lat, lng, first));

            if (first) {
                item.setLatitude(lat);
                item.setLongitude(lng);
                first = false;
            }
        }
        itineraryItemDAO.save(item);

        recalculateRoutes(IDID);
        return item;
    }

    /**
     * 切換某個項目要用哪一個候選點 (例如飯店 A或B或C, 這裡選定其中一個), 地圖會跟著更新
     */
    public void selectItemOption(int IIID, int IIOID) {
        List<ItineraryItemOption> options = itineraryItemOptionDAO.findByItem(IIID);
        ItineraryItemOption chosen = options.stream().filter(o -> o.getIIOID() == IIOID).findFirst().orElse(null);
        if (chosen == null) throw new IllegalArgumentException("找不到這個選項");

        itineraryItemOptionDAO.clearSelected(IIID);
        chosen.setSelected(true);
        itineraryItemOptionDAO.save(chosen);

        ItineraryItem item = itineraryItemDAO.findById(IIID);
        if (item != null) {
            item.setCustomName(chosen.getName());
            item.setLatitude(chosen.getLatitude());
            item.setLongitude(chosen.getLongitude());
            itineraryItemDAO.save(item);
            recalculateRoutes(item.getIDID());
        }
    }

    public List<ItineraryItemOption> getItemOptions(int IIID) {
        return itineraryItemOptionDAO.findByItem(IIID);
    }

    /**
     * 編輯排版看板上已存在的項目: 名稱、停留時間、地點提示 (填了會重新定位, 不填就保留原本座標)
     */
    public void updateItemDetails(int IIID, String customName, Integer stayDurationMin, String locationHint) {
        updateItemDetails(IIID, customName, stayDurationMin, locationHint, null, null, null);
    }

    /**
     * @param timeSlot 選填, 傳了才會更新時段標記 (breakfast/lunch/dinner/morning/noon/afternoon/evening); 傳空字串會清空
     */
    public void updateItemDetails(int IIID, String customName, Integer stayDurationMin, String locationHint, String timeSlot) {
        updateItemDetails(IIID, customName, stayDurationMin, locationHint, timeSlot, null, null);
    }

    /**
     * @param note       項目自己的備註 (不會存進 POI 資料庫, 只跟著這個行程項目, 匯出企劃書時會一起輸出)
     * @param showOnMap  這個項目要不要顯示在地圖上, 傳 null 代表不更動
     */
    public void updateItemDetails(int IIID, String customName, Integer stayDurationMin, String locationHint,
                                  String timeSlot, String note, Boolean showOnMap) {
        updateItemDetails(IIID, customName, stayDurationMin, locationHint, timeSlot, note, showOnMap,
                null, null, null, null, null, null, null);
    }

    /**
     * @param itemType         看板上「編輯」時可以直接更換這個項目的類別 (景點/餐廳/住宿/交通...), 傳 null 或空字串代表不更動
     * @param fromLocation     交通項目專用: 起始點名稱
     * @param fromAddress      交通項目專用: 起始地址; 有填就直接採用, 沒填但有起始點名稱的話後端會自動查詢帶入
     * @param toLocation       交通項目專用: 目的地名稱; 有填會順便重新查詢目的地座標, 讓這個項目在地圖上的位置
     *                         (沿用單一經緯度欄位) 對應到「抵達的目的地」
     * @param toAddress        交通項目專用: 目的地地址; 有填就直接採用, 沒填但有目的地名稱的話後端會自動查詢帶入
     * @param transportMethod  交通項目專用: 交通工具 (高鐵/飛機/遊覽車/渡輪/計程車...)
     * @param commuteDuration  交通項目專用: 通勤時間 (自由文字, 例如「約1小時30分」)
     */
    public void updateItemDetails(int IIID, String customName, Integer stayDurationMin, String locationHint,
                                  String timeSlot, String note, Boolean showOnMap,
                                  String itemType, String fromLocation, String fromAddress,
                                  String toLocation, String toAddress, String transportMethod, String commuteDuration) {
        ItineraryItem item = itineraryItemDAO.findById(IIID);
        if (item == null) throw new IllegalArgumentException("找不到這個項目");

        item.setCustomName(customName);
        item.setStayDurationMin(stayDurationMin);
        if (timeSlot != null) {
            item.setTimeSlot(timeSlot.isBlank() ? null : timeSlot);
        }
        if (note != null) {
            item.setNote(note.isBlank() ? null : note);
        }
        if (showOnMap != null) {
            item.setShowOnMap(showOnMap);
        }
        if (itemType != null && !itemType.isBlank()) {
            item.setItemType(itemType);
        }

        // 如果這個項目本來就連結公司 POI 資料庫, 停留時間也順便同步回 POI 本身,
        // 這樣以後別的行程用到同一個 POI, 停留時間預設值也會是最新修正過的, 邏輯跟下面的座標同步一致。
        if (stayDurationMin != null && item.getPID() != null) {
            Poi poi = poiDAO.findById(item.getPID());
            if (poi != null) {
                poi.setSuggestedStayMin(stayDurationMin);
                poiDAO.save(poi);
            }
        }

        if (locationHint != null && !locationHint.isBlank()) {
            GoogleMapsClient.GeocodeResult geo = googleMapsClient.resolveLocationHint(locationHint);
            if (geo != null) {
                item.setLatitude(BigDecimal.valueOf(geo.latitude));
                item.setLongitude(BigDecimal.valueOf(geo.longitude));

                // 如果這個項目本來就連結公司 POI 資料庫, 這次重新定位順便把 POI 本身的經緯度也修正,
                // 這樣以後別的行程用到同一個 POI 也會是準確的座標, 不用每次都要重新定位一次。
                if (item.getPID() != null) {
                    Poi poi = poiDAO.findById(item.getPID());
                    if (poi != null) {
                        poi.setLatitude(BigDecimal.valueOf(geo.latitude));
                        poi.setLongitude(BigDecimal.valueOf(geo.longitude));
                        poiDAO.save(poi);
                    }
                }
            }
        }

        // ---- 交通項目專屬欄位 (只有「交通」編輯表單才會傳這些參數進來, 一般景點/餐廳/住宿不會傳, 全部維持 null 不更動) ----
        String geocodeCountry = null; // 需要自動查地址/座標時才去反查一次行程所在國家, 不需要就不多查
        if (fromLocation != null) {
            item.setFromLocation(fromLocation.isBlank() ? null : fromLocation.trim());
        }
        if (toLocation != null) {
            item.setToLocation(toLocation.isBlank() ? null : toLocation.trim());
        }
        if (transportMethod != null) {
            item.setTransportMethod(transportMethod.isBlank() ? null : transportMethod.trim());
        }
        if (commuteDuration != null) {
            item.setCommuteDuration(commuteDuration.isBlank() ? null : commuteDuration.trim());
        }

        if (fromAddress != null && !fromAddress.isBlank()) {
            item.setFromAddress(fromAddress.trim());
        } else if (fromLocation != null && !fromLocation.isBlank()) {
            // 起始地址沒填, 依起始點名稱自動查詢帶入
            geocodeCountry = resolveCountryForItem(item);
            String auto = googleMapsClient.resolveAddressForName(fromLocation.trim(), geocodeCountry);
            if (auto != null) item.setFromAddress(auto);
        }

        if (toAddress != null && !toAddress.isBlank()) {
            item.setToAddress(toAddress.trim());
        } else if (toLocation != null && !toLocation.isBlank()) {
            // 目的地地址沒填, 依目的地名稱自動查詢帶入
            if (geocodeCountry == null) geocodeCountry = resolveCountryForItem(item);
            String auto = googleMapsClient.resolveAddressForName(toLocation.trim(), geocodeCountry);
            if (auto != null) item.setToAddress(auto);
        }

        // 交通項目在地圖上的位置沿用單一經緯度欄位, 代表「抵達的目的地」: 目的地名稱有異動就順便重新查一次座標,
        // 這樣「顯示在地圖上」這個開關才會真的對應到有意義的位置, 不會空有勾選卻沒有座標可畫。
        if (toLocation != null && !toLocation.isBlank()) {
            if (geocodeCountry == null) geocodeCountry = resolveCountryForItem(item);
            String query = String.join(" ", toLocation.trim(), geocodeCountry != null ? geocodeCountry : "").trim();
            GoogleMapsClient.GeocodeResult geo = googleMapsClient.findPlace(query, geocodeCountry);
            if (geo == null) geo = googleMapsClient.geocode(query, geocodeCountry);
            if (geo != null) {
                item.setLatitude(BigDecimal.valueOf(geo.latitude));
                item.setLongitude(BigDecimal.valueOf(geo.longitude));
            }
        }

        itineraryItemDAO.save(item);
        recalculateRoutes(item.getIDID());
    }

    // 交通項目自動查詢地址/座標時, 找這個項目所屬行程的國家 (優先用項目自己判斷出的國家, 沒有才反查行程層級的國家),
    // 限定搜尋範圍避免同名地點查到國外去
    private String resolveCountryForItem(ItineraryItem item) {
        if (item.getItemCountry() != null && !item.getItemCountry().isBlank()) return item.getItemCountry();
        ItineraryDay day = itineraryDayDAO.findById(item.getIDID());
        if (day != null) {
            Itinerary itinerary = itineraryDAO.findById(day.getITID());
            if (itinerary != null) return firstToken(itinerary.getCountry());
        }
        return null;
    }

    public void removeItem(int IIID, int IDID) {
        // 先清掉這天的路段快取 (route_segment 的外鍵沒設 CASCADE, 有算過拉車距離的項目直接刪會被擋)
        routeSegmentDAO.deleteByDay(IDID);
        itineraryItemDAO.deleteById(IIID);
        recalculateRoutes(IDID);
    }

    /**
     * 自動整理某一天的行程順序與餐別時段, 套用線控慣用的預設規則:
     *   1. 餐廳類項目沒有手動指定時段的話, 依這天「第幾個出現的餐廳」自動判斷: 第1個=早餐, 第2個=午餐, 第3個(含)以後=晚餐
     *   2. 住宿類項目一律排在這天最後面 (不管 AI 解析或使用者原本把它插在哪裡)
     *   3. 其餘項目 (景點/交通/自費/自由活動) 維持原本的相對順序
     * 只會補上「還沒有時段」的餐廳標記, 已經手動編輯過時段的項目不會被覆蓋掉;
     * 住宿排最後這條規則則一律套用, 確保「今天最後一站是飯店」這個慣例。
     */
    public void autoArrangeDay(int IDID) {
        autoArrangeDay(IDID, "meal_time");
    }

    /**
     * 套用到「整個行程」(所有天), 而不是只有目前這一天。內部就是把每一天各自跑一次 autoArrangeDay。
     */
    public void autoArrangeItinerary(int ITID, String mode) {
        for (ItineraryDay day : itineraryDayDAO.findByItinerary(ITID)) {
            autoArrangeDay(day.getIDID(), mode);
        }

        Itinerary itinerary = itineraryDAO.findById(ITID);
        if (itinerary != null) {
            itinerary.setArrangeMode("meal_time".equals(mode) ? "meal_time" : "all_last");
            itineraryDAO.save(itinerary);
        }
    }

    /**
     * @param mode "meal_time" (預設): 早餐固定第一個, 午餐/晚餐依累計時間排到接近 12:00/18:00, 住宿排最後
     *             "all_last": 不管時間, 餐廳跟住宿全部依原本順序排到這一天的最後面 (景點/交通/自費維持在前面)
     */
    public void autoArrangeDay(int IDID, String mode) {
        List<ItineraryItem> items = itineraryItemDAO.findByDay(IDID);
        if (items.isEmpty()) return;

        if ("all_last".equals(mode)) {
            List<ItineraryItem> anchors = new ArrayList<>();
            List<ItineraryItem> mealsAndHotels = new ArrayList<>();
            for (ItineraryItem item : items) {
                if ("meal".equals(item.getItemType()) || "hotel".equals(item.getItemType())) {
                    mealsAndHotels.add(item);
                } else {
                    anchors.add(item);
                }
            }
            List<ItineraryItem> arranged = new ArrayList<>(anchors);
            arranged.addAll(mealsAndHotels);
            for (int i = 0; i < arranged.size(); i++) {
                arranged.get(i).setSortOrder(i);
                itineraryItemDAO.save(arranged.get(i));
            }
            recalculateRoutes(IDID);
            return;
        }

        ItineraryDay day = itineraryDayDAO.findById(IDID);
        java.time.LocalTime dayStart = (day != null && day.getStartTime() != null) ? day.getStartTime() : java.time.LocalTime.of(9, 0);

        List<ItineraryItem> hotels = new ArrayList<>();
        List<ItineraryItem> mealsToPlace = new ArrayList<>(); // 所有餐廳, 依原本出現順序 (這是明確的「重新整理」動作, 不管之前有沒有標過時段, 都強制重新判斷位置)
        List<ItineraryItem> anchors = new ArrayList<>();      // 其餘項目 (景點/交通/自費/自由活動), 當作時間軸骨架

        for (ItineraryItem item : items) {
            if ("hotel".equals(item.getItemType())) {
                hotels.add(item);
            } else if ("meal".equals(item.getItemType())) {
                mealsToPlace.add(item);
            } else {
                anchors.add(item);
            }
        }

        // 依序標記: 第1個餐廳=早餐, 第2個=午餐, 第3個(以後)=晚餐
        for (int i = 0; i < mealsToPlace.size(); i++) {
            mealsToPlace.get(i).setTimeSlot(i == 0 ? "breakfast" : (i == 1 ? "lunch" : "dinner"));
        }

        // 早餐固定放最前面, 午餐/晚餐依累計停留時間算出最接近目標時間 (12:00 / 18:00) 的位置插進去
        List<ItineraryItem> arranged = new ArrayList<>(anchors);
        for (ItineraryItem meal : mealsToPlace) {
            int insertIndex;
            if ("breakfast".equals(meal.getTimeSlot())) {
                insertIndex = 0;
            } else {
                java.time.LocalTime target = "lunch".equals(meal.getTimeSlot())
                        ? java.time.LocalTime.of(12, 0) : java.time.LocalTime.of(18, 0);
                insertIndex = findIndexForTargetTime(arranged, dayStart, target);
            }
            arranged.add(Math.min(insertIndex, arranged.size()), meal);
        }

        arranged.addAll(hotels); // 住宿固定排最後

        for (int i = 0; i < arranged.size(); i++) {
            ItineraryItem item = arranged.get(i);
            item.setSortOrder(i);
            itineraryItemDAO.save(item);
        }

        recalculateRoutes(IDID);
    }

    // 依「目前已排好的項目序列」累加停留時間, 找出最接近目標時間 (12:00/18:00) 該插在第幾個位置
    // (只用停留時間估算, 沒有把拉車時間算進去, 是簡化過的推算, 不是精準排程)
    private int findIndexForTargetTime(List<ItineraryItem> sequence, java.time.LocalTime dayStart, java.time.LocalTime target) {
        java.time.LocalTime current = dayStart;
        for (int i = 0; i < sequence.size(); i++) {
            ItineraryItem it = sequence.get(i);
            int dur = it.getStayDurationMin() != null ? it.getStayDurationMin() : defaultStayMinutes(it.getItemType());
            java.time.LocalTime end = current.plusMinutes(dur);
            if (!end.isBefore(target)) {
                return i + 1; // 這一項結束時已經超過目標時間, 插在它後面
            }
            current = end;
        }
        return sequence.size();
    }

    private int defaultStayMinutes(String itemType) {
        if (itemType == null) return 60;
        return switch (itemType) {
            case "attraction" -> 90;
            case "meal" -> 60;
            case "hotel" -> 0;
            case "transport" -> 0;
            default -> 60;
        };
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * 拖曳排序後呼叫: orderedItemIds 是前端拖完之後的新順序 (IIID 陣列)
     */
    public void reorderItems(int IDID, List<Integer> orderedItemIds) {
        for (int i = 0; i < orderedItemIds.size(); i++) {
            itineraryItemDAO.updateSortOrder(orderedItemIds.get(i), i);
        }
        recalculateRoutes(IDID);
    }

    /**
     * 拖曳上方「Day 分頁」排序後呼叫: orderedDayIds 是前端拖完之後的新順序 (IDID 陣列)。
     * 只重新分配 day_number (第幾天的標籤), 每一天原本裡面排的景點/餐廳等項目還是跟著同一個
     * IDID 走, 等於整天的內容被搬到新的位置, 而不是搬動裡面個別的項目。
     * day_date 如果行程有設定出發日期, 也一併依新順序重算, 讓天數跟日期保持連續對應。
     */
    public void reorderDays(int ITID, List<Integer> orderedDayIds) {
        if (orderedDayIds == null) return;
        Itinerary itinerary = itineraryDAO.findById(ITID);
        LocalDate startDate = itinerary != null ? itinerary.getStartDate() : null;

        for (int i = 0; i < orderedDayIds.size(); i++) {
            ItineraryDay day = itineraryDayDAO.findById(orderedDayIds.get(i));
            if (day == null || day.getITID() != ITID) continue; // 安全檢查: 避免拖到別的行程的 IDID
            day.setDayNumber(i + 1);
            if (startDate != null) {
                day.setDayDate(startDate.plusDays(i));
            }
            itineraryDayDAO.save(day);
        }
    }

    /**
     * 排序異動後, 清掉舊的路段快取並重新請 RouteService 算一次
     * (實際的地圖/距離計算邏輯放在 RouteService, 方便未來替換 Google Maps API)
     *
     * 預設會保留每一段先前已經手動指定過的通勤方式 (見 preserveSegmentOverrides), 不然使用者每次
     * 拖曳排序、加項目、刪項目, 剛剛手動選好的走路/開車就會被整天重算蓋掉。
     */
    private void recalculateRoutes(int IDID) {
        recalculateRoutes(IDID, true, true);
    }

    private void recalculateRoutes(int IDID, boolean preserveSegmentOverrides) {
        recalculateRoutes(IDID, preserveSegmentOverrides, true);
    }

    /**
     * @param cascadeToNextDay 這一天的最後一項如果是住宿, 可能會被「下一天」當成預設出發點帶入
     *                         (見 findCarryOverHotel), 所以算完這一天順便讓下一天也重新算一次路線,
     *                         不然下一天要等自己也被異動過才會抓到最新的住宿銜接資訊。
     *                         只會往前推「一天」, 不會整個行程都連鎖重算 (避免一次編輯觸發一長串重算,
     *                         天數多的行程會變慢), 所以這裡固定傳 false 給遞迴呼叫、避免無限往後連鎖。
     */
    private void recalculateRoutes(int IDID, boolean preserveSegmentOverrides, boolean cascadeToNextDay) {
        java.util.Map<String, String> overrides = java.util.Map.of();
        if (preserveSegmentOverrides) {
            overrides = routeSegmentDAO.findByDay(IDID).stream()
                    .filter(seg -> seg.getTransportMode() != null)
                    .collect(java.util.stream.Collectors.toMap(
                            seg -> seg.getFromItemId() + "-" + seg.getToItemId(),
                            com.example.UsefulTravel.entity.RouteSegment::getTransportMode,
                            (a, b) -> a));
        }

        routeSegmentDAO.deleteByDay(IDID);
        List<ItineraryItem> items = itineraryItemDAO.findByDay(IDID);

        // 如果前一天最後一項是住宿, 而且今天不是它, 把它當成「今天的出發地」虛擬接到清單最前面一起算路線,
        // 這樣今天第一個真正的行程項目才會有「從昨晚住宿出發」的拉車距離/時間可以顯示, 感覺才連貫。
        // 這個借來的項目本身還是屬於昨天, 不會被存進今天的 itinerary_item, 只是暫時借用它的座標算這一段路線。
        ItineraryItem carryOverHotel = findCarryOverHotel(IDID);
        List<ItineraryItem> itemsForRouting = items;
        if (carryOverHotel != null && !items.isEmpty()) {
            itemsForRouting = new java.util.ArrayList<>();
            itemsForRouting.add(carryOverHotel);
            itemsForRouting.addAll(items);
        }

        ItineraryDay day = itineraryDayDAO.findById(IDID);
        // "auto" 代表不強制整天用同一種方式, 讓 RouteService 針對每一段依實際距離用 AI 判斷推薦
        String transportMode = day != null && day.getTransportMode() != null ? day.getTransportMode() : "auto";
        routeService.calculateAndSaveSegments(IDID, itemsForRouting, transportMode, overrides);

        if (cascadeToNextDay && day != null) {
            itineraryDayDAO.findByItinerary(day.getITID()).stream()
                    .filter(d -> d.getDayNumber() == day.getDayNumber() + 1)
                    .findFirst()
                    .ifPresent(nextDay -> recalculateRoutes(nextDay.getIDID(), true, false));
        }
    }

    /**
     * 找出「前一天最後一項住宿」, 用來當作今天的預設出發點 (見上面 recalculateRoutes 的說明, 以及看板地圖上
     * 今天第一個點前面多出來的那個床 emoji 標記)。符合以下所有條件才會回傳, 其餘情況一律回傳 null (代表沒有可以帶入的):
     *   1. 這不是行程的第一天 (day_number > 1), 且真的有找到「前一天」這個 ItineraryDay。
     *   2. 前一天有排項目, 而且最後一項的類別是「住宿」。
     *   3. 那個住宿項目有座標 (不然沒辦法算路線、也沒辦法畫在地圖上)。
     *   4. 今天如果已經自己排了項目, 且第一項剛好就是「同一間」住宿 (同一個 PID, 或名稱完全相同) 的話,
     *      代表使用者已經手動處理過這個銜接了, 不用再多此一舉虛擬帶入一次。
     */
    public ItineraryItem findCarryOverHotel(int IDID) {
        ItineraryDay day = itineraryDayDAO.findById(IDID);
        if (day == null || day.getDayNumber() <= 1) return null;

        ItineraryDay prevDay = itineraryDayDAO.findByItinerary(day.getITID()).stream()
                .filter(d -> d.getDayNumber() == day.getDayNumber() - 1)
                .findFirst().orElse(null);
        if (prevDay == null) return null;

        List<ItineraryItem> prevItems = itineraryItemDAO.findByDay(prevDay.getIDID());
        if (prevItems.isEmpty()) return null;

        ItineraryItem lastOfPrevDay = prevItems.get(prevItems.size() - 1); // findByDay 已經照 sort_order 排好
        if (!"hotel".equals(lastOfPrevDay.getItemType())) return null;
        if (lastOfPrevDay.getLatitude() == null || lastOfPrevDay.getLongitude() == null) return null;

        List<ItineraryItem> todayItems = itineraryItemDAO.findByDay(IDID);
        if (!todayItems.isEmpty()) {
            ItineraryItem firstToday = todayItems.get(0);
            boolean samePoi = lastOfPrevDay.getPID() != null && lastOfPrevDay.getPID().equals(firstToday.getPID());
            boolean sameName = lastOfPrevDay.getCustomName() != null
                    && lastOfPrevDay.getCustomName().equals(firstToday.getCustomName());
            if (samePoi || sameName) return null;
        }
        return lastOfPrevDay;
    }

    /**
     * 智慧景點推薦: 找出公司 POI 資料庫裡, 落在「fromItem → toItem」順路範圍內的其他景點/餐廳/休息站
     *
     * 判斷邏輯改成「拉車時間」而不是直線距離：
     *   1) 先用直線距離做粗篩 (haversine), 把候選點縮小到一個合理範圍內, 避免每個候選點都打 Google API 太慢太貴。
     *   2) 針對粗篩後的候選點, 呼叫 Distance Matrix API 拿「A→候選點」「候選點→B」的實際開車時間,
     *      跟「A→B」的實際開車時間比較：候選點路線總時間必須在 A→B 直達時間的 1.5 倍以內才算「順路」。
     *      例如 A→B 直達 60 分鐘, 那 A→C→B 加起來最多只能 90 分鐘, 才會推薦 C。
     *   3) 沒有設定 Google Maps API Key 時退回舊的直線距離估算法 (迂迴不超過 1.5 倍距離), 至少還能用。
     */
    public List<com.example.UsefulTravel.entity.Poi> suggestPoiBetween(int AID, int IDID, int fromIIID, int toIIID) {
        ItineraryItem fromItem = itineraryItemDAO.findById(fromIIID);
        ItineraryItem toItem = itineraryItemDAO.findById(toIIID);
        if (fromItem == null || toItem == null || fromItem.getPID() == null || toItem.getPID() == null) {
            return List.of();
        }

        com.example.UsefulTravel.entity.Poi fromPoi = poiDAO.findById(fromItem.getPID());
        com.example.UsefulTravel.entity.Poi toPoi = poiDAO.findById(toItem.getPID());
        if (fromPoi == null || toPoi == null || fromPoi.getLatitude() == null || toPoi.getLatitude() == null) {
            return List.of();
        }

        double fLat = fromPoi.getLatitude().doubleValue(), fLng = fromPoi.getLongitude().doubleValue();
        double tLat = toPoi.getLatitude().doubleValue(), tLng = toPoi.getLongitude().doubleValue();
        double directKm = haversineKm(fLat, fLng, tLat, tLng);
        final double RATIO_LIMIT = 1.5; // 拉車時間 (或退回時的直線距離) 不能超過直達的 1.5 倍

        // 已經在這天的項目不重複推薦
        java.util.Set<Integer> alreadyInDay = itineraryItemDAO.findByDay(IDID).stream()
                .map(ItineraryItem::getPID).filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        List<com.example.UsefulTravel.entity.Poi> roughCandidates = poiDAO.findByAgencyAndCountry(AID, fromPoi.getCountry(), null).stream()
                .filter(p -> p.getLatitude() != null)
                .filter(p -> !alreadyInDay.contains(p.getPID()))
                .filter(p -> p.getPID() != fromPoi.getPID() && p.getPID() != toPoi.getPID())
                .filter(p -> directKm >= 0.3) // 兩點幾乎同位置時, 直線距離太小算比例沒意義, 整批跳過
                .sorted(java.util.Comparator.comparingDouble(p -> {
                    double cLat = p.getLatitude().doubleValue(), cLng = p.getLongitude().doubleValue();
                    return haversineKm(fLat, fLng, cLat, cLng) + haversineKm(cLat, cLng, tLat, tLng);
                }))
                .limit(12) // 粗篩留前 12 名再去查真實拉車時間, 避免每個候選點都打 Google API
                .collect(java.util.stream.Collectors.toList());

        if (roughCandidates.isEmpty() || directKm < 0.3) return List.of();

        // 拿真實開車時間 (A→B 直達) 當基準；拿不到 (沒設定 API key 或呼叫失敗) 就退回直線距離估算
        GoogleMapsClient.DistanceResult directDrive = googleMapsClient.getDrivingDistance(fLat, fLng, tLat, tLng);

        if (directDrive != null && directDrive.durationMin > 0) {
            int baseMin = directDrive.durationMin;
            int limitMin = (int) Math.round(baseMin * RATIO_LIMIT);

            return roughCandidates.stream()
                    .map(p -> {
                        double cLat = p.getLatitude().doubleValue(), cLng = p.getLongitude().doubleValue();
                        GoogleMapsClient.DistanceResult leg1 = googleMapsClient.getDrivingDistance(fLat, fLng, cLat, cLng);
                        GoogleMapsClient.DistanceResult leg2 = googleMapsClient.getDrivingDistance(cLat, cLng, tLat, tLng);
                        Integer viaMin = (leg1 != null && leg2 != null) ? leg1.durationMin + leg2.durationMin : null;
                        return new Object[]{p, viaMin};
                    })
                    .filter(pair -> pair[1] != null && (Integer) pair[1] <= limitMin)
                    .sorted(java.util.Comparator.comparingInt(pair -> (Integer) pair[1]))
                    .limit(5)
                    .map(pair -> (com.example.UsefulTravel.entity.Poi) pair[0])
                    .collect(java.util.stream.Collectors.toList());
        }

        // 退回直線距離估算 (沒有 Google Maps API 可用時)
        return roughCandidates.stream()
                .filter(p -> {
                    double cLat = p.getLatitude().doubleValue(), cLng = p.getLongitude().doubleValue();
                    double viaKm = haversineKm(fLat, fLng, cLat, cLng) + haversineKm(cLat, cLng, tLat, tLng);
                    return viaKm <= directKm * RATIO_LIMIT;
                })
                .limit(5)
                .collect(java.util.stream.Collectors.toList());
    }

    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private boolean shouldGeocode(String itemType, String timeSlot, String customName) {
        if ("transport".equals(itemType)) return false; // 飛機不需要座標
        if ("meal".equals(itemType) && "breakfast".equals(timeSlot)
                && (customName == null || customName.contains("飯店") || customName.contains("早餐"))) {
            return false; // 純飯店內早餐，跟飯店同一個點，不用額外標記
        }
        return true; // 其餘（含所有具名餐廳）都要定位
    }

    public void updateSegmentTransportMode(int IDID, int RSID, String mode) {
        routeSegmentDAO.updateTransportMode(RSID, mode);
        // 只重算這一段, 不用整天重算
        routeService.recalculateSingleSegment(RSID, mode);
    }
}