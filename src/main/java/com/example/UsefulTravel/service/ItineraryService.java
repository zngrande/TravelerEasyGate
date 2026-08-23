package com.example.UsefulTravel.service;

import com.example.UsefulTravel.DAO.AiImportDAO;
import com.example.UsefulTravel.DAO.CountryCityCodeDAO;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ItineraryService - 行程建立與積木式排版的核心跨表邏輯
 */
@Service
public class ItineraryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ItineraryService.class);

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
    private final CountryCityCodeDAO countryCityCodeDAO;

    @Autowired
    public ItineraryService(ItineraryDAO itineraryDAO, ItineraryDayDAO itineraryDayDAO,
                            ItineraryItemDAO itineraryItemDAO, ItineraryItemOptionDAO itineraryItemOptionDAO,
                            RouteSegmentDAO routeSegmentDAO,
                            RouteService routeService, PoiDAO poiDAO, AiImportDAO aiImportDAO,
                            GoogleMapsClient googleMapsClient, AnthropicClient anthropicClient,
                            ObjectMapper objectMapper, CountryCityCodeDAO countryCityCodeDAO) {
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
        this.countryCityCodeDAO = countryCityCodeDAO;
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
        return createItinerary(AID, createdBy, title, country, region, daysCount, startDate, null);
    }

    public Itinerary createItinerary(int AID, int createdBy, String title, String country, String region,
                                     int daysCount, LocalDate startDate, String description) {
        Itinerary itinerary = new Itinerary(AID, createdBy, title, country, daysCount);
        itinerary.setRegion(region);
        itinerary.setStartDate(startDate);
        if (startDate != null) {
            itinerary.setEndDate(startDate.plusDays(daysCount - 1));
        }
        itinerary.setDescription((description != null && !description.isBlank()) ? description : null);
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
        return createItineraryWithAiPlan(AID, createdBy, title, country, region, daysCount, startDate, null);
    }

    public Itinerary createItineraryWithAiPlan(int AID, int createdBy, String title, String country, String region,
                                               int daysCount, LocalDate startDate, String description) {
        return createItineraryWithAiPlan(AID, createdBy, title, country, region, daysCount, startDate, description,
                false, false);
    }

    /**
     * 「AI 安排行程」+ 建立當下就知道去程/回程班機表單資料的版本 —— 用來判斷第一天/最後一天是不是已經有
     * 掛去程/回程班機, 有的話那一天就完全不強制補「剛好 3 餐 + 1 間住宿」(見下方 day1HasFlight/lastDayHasFlight
     * 用法說明), 因為出發/抵達當天的餐食、住宿常常不在這個行程的規劃範圍內 (例如出發前已經在家吃過、
     * 或抵達當地已經是回程班機、不需要再排住宿)。
     *
     * 這裡只是「先算出第一天/最後一天有沒有掛班機」這個布林值——實際把班機項目插入行程看板，
     * 仍然要等這個方法整個跑完 (包含下面的 autoArrangeItinerary) 之後，由呼叫端另外呼叫 attachFlightItems()，
     * 原因跟 attachFlightItems() 的 Javadoc 說明的呼叫順序限制一致，這裡不能提前插入。
     *
     * outDepDay/retDepDay 用法、每個航段判斷「有沒有填」的規則, 都跟 attachFlightItems() 完全一致
     * (見 resolveFlightDayNumbers)，去程沒填天數預設算第一天、回程沒填天數預設算最後一天。
     */
    public Itinerary createItineraryWithAiPlan(int AID, int createdBy, String title, String country, String region,
                                               int daysCount, LocalDate startDate, String description,
                                               List<String> outDepAirport, List<String> outDepTime,
                                               List<String> outArrAirport, List<String> outArrTime,
                                               List<String> outDepDay,
                                               List<String> retDepAirport, List<String> retDepTime,
                                               List<String> retArrAirport, List<String> retArrTime,
                                               List<String> retDepDay) {
        Set<Integer> outboundDays = resolveFlightDayNumbers(daysCount, outDepAirport, outDepTime,
                outArrAirport, outArrTime, outDepDay, 1);
        Set<Integer> returnDays = resolveFlightDayNumbers(daysCount, retDepAirport, retDepTime,
                retArrAirport, retArrTime, retDepDay, daysCount);
        boolean day1HasFlight = outboundDays.contains(1) || returnDays.contains(1);
        boolean lastDayHasFlight = outboundDays.contains(daysCount) || returnDays.contains(daysCount);
        return createItineraryWithAiPlan(AID, createdBy, title, country, region, daysCount, startDate, description,
                day1HasFlight, lastDayHasFlight);
    }

    private Itinerary createItineraryWithAiPlan(int AID, int createdBy, String title, String country, String region,
                                               int daysCount, LocalDate startDate, String description,
                                               boolean day1HasFlight, boolean lastDayHasFlight) {
        Itinerary itinerary = createItinerary(AID, createdBy, title, country, region, daysCount, startDate, description);

        List<Poi> candidates = poiDAO.findByAgencyAndCountry(AID, country, region);
        if (candidates.isEmpty() && region != null && !region.isBlank()) {
            // 地區篩選完全找不到候選景點時, 先退回成只用國家篩選再試一次 ——
            // 使用者填的地區/城市名稱 (例如「佛羅倫斯、威尼斯、比薩、米蘭、羅馬」) 常常跟
            // poi.city 實際存的字串沒辦法字面 exact match 上, 但這個國家在資料庫裡其實有大量景點,
            // 不應該只因為城市名稱顆粒度對不上就整個放棄、建出空白行程。
            candidates = poiDAO.findByAgencyAndCountry(AID, country, null);
        }
        if (candidates.isEmpty()) return itinerary; // 這個國家在資料庫裡完全沒有景點, 保持空白行程讓使用者自己排

        List<ItineraryDay> days = itineraryDayDAO.findByItinerary(itinerary.getITID());

        try {
            // 候選景點清單如果很大 (例如 region 篩不到、退回整個國家層級, 或本來就是熱門大國) 全部塞給 AI
            // 容易讓 prompt 太長、AI 也更容易把輸出格式搞亂 (JSON 解析失敗、或被 max_tokens 截斷) ——
            // 依類別各自設上限, 用等距抽樣縮小成一份「給 AI 排程用」的子清單 (見 buildAiCandidatePool 說明)。
            // 後面的 candidateByPid / 逐天餐廳飯店自動補位, 仍然用完整的 candidates 清單, 不受這個上限影響。
            List<Poi> aiCandidatePool = buildAiCandidatePool(candidates);
            Map<Integer, List<Integer>> plan = planDaysWithAi(country, region, daysCount, aiCandidatePool, description);
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

            // AI 排出來的初稿不保證每天都有 3 餐、剛好 1 間住宿——使用者反映過兩個常見的「怪」狀況：
            // (1) 明明候選餐廳數量足夠, AI 卻常常一天只排 1 餐, 不是 3 餐都排;
            // (2) 飯店在不同天之間跳來跳去, 例如 Day1/Day3 住 A 飯店、Day2 卻換成 B 飯店, 中間被打斷又換回來。
            // 系統提示詞裡雖然已經有明確要求「每天 3 餐」「同一間要連續住, 不能來回切換」, 但不能只靠 AI 自律,
            // 這裡用程式碼再逐天檢查、強制補齊/修正一次, 不管 AI 有沒有乖乖照規則排, 結果都會是穩定的。
            List<Poi> restaurantCandidates = candidates.stream()
                    .filter(p -> "餐廳".equals(p.getCategory())).collect(java.util.stream.Collectors.toList());
            List<Poi> hotelCandidates = candidates.stream()
                    .filter(p -> "飯店".equals(p.getCategory())).collect(java.util.stream.Collectors.toList());

            // (1) 逐天補到剛好 3 餐: 候選餐廳夠的話輪流從候選清單裡挑 (允許重複使用, 跟原本「數量不足以合理重複」
            //     的規則一致); 候選餐廳掛零的極端狀況才用純文字預留項目 (不連結 POI、不查座標、不顯示在地圖上)。
            int restaurantRoundRobin = 0;
            for (ItineraryDay day : days) {
                // 第一天/最後一天如果已經掛了去程/回程班機, 完全不強制補這一天的餐食——
                // 出發/抵達當天有沒有餐食是使用者自己的行程重點, AI 排出來的初稿是什麼就維持什麼 (含完全沒排)。
                if ((day.getDayNumber() == 1 && day1HasFlight)
                        || (day.getDayNumber() == daysCount && lastDayHasFlight)) continue;
                long mealCount = itineraryItemDAO.findByDay(day.getIDID()).stream()
                        .filter(item -> "meal".equals(item.getItemType())).count();
                String[] fallbackLabels = {"早餐", "午餐", "晚餐"};
                while (mealCount < 3) {
                    if (restaurantCandidates.isEmpty()) {
                        addPlaceholderItem(day.getIDID(), "meal", fallbackLabels[(int) Math.min(mealCount, 2)]);
                    } else {
                        Poi pick = restaurantCandidates.get(restaurantRoundRobin % restaurantCandidates.size());
                        restaurantRoundRobin++;
                        addItem(day.getIDID(), pick.getPID(), "meal", pick.getName(), pick.getSuggestedStayMin());
                    }
                    mealCount++;
                }
            }

            // (2) 逐天確保剛好 1 間住宿: 沒有的話補一間 (候選掛零一樣退回純文字預留項目);
            //     如果 AI 同一天排了不只 1 間, 只留下第一間, 其餘刪掉, 避免同一天出現兩間飯店。
            for (ItineraryDay day : days) {
                // 理由跟上面餐食一致: 出發/抵達當天有掛班機的話, 住宿也完全不強制。
                if ((day.getDayNumber() == 1 && day1HasFlight)
                        || (day.getDayNumber() == daysCount && lastDayHasFlight)) continue;
                List<ItineraryItem> hotelItems = itineraryItemDAO.findByDay(day.getIDID()).stream()
                        .filter(item -> "hotel".equals(item.getItemType())).collect(java.util.stream.Collectors.toList());
                if (hotelItems.isEmpty()) {
                    if (hotelCandidates.isEmpty()) {
                        addPlaceholderItem(day.getIDID(), "hotel", "住宿");
                    } else {
                        Poi pick = hotelCandidates.get(0);
                        addItem(day.getIDID(), pick.getPID(), "hotel", pick.getName(), pick.getSuggestedStayMin());
                    }
                } else if (hotelItems.size() > 1) {
                    for (int i = 1; i < hotelItems.size(); i++) {
                        itineraryItemDAO.deleteById(hotelItems.get(i).getIIID());
                    }
                }
            }

            // (3) 修正「A、B、A 來回切換」這種怪異安排: 如果第 i 天跟第 i+2 天住同一間, 但中間第 i+1 天卻是
            //     別間, 這極可能是 AI 亂跳、不是真的換城市——把第 i+1 天也改成跟前後一致, 讓同一間飯店的
            //     入住天數變成連續區塊, 不會被打斷又接回來 (不影響 AABBB 這種正常的「換過去就不換回來」分段)。
            normalizeHotelContiguity(days);

            // 加完之後跑一次自動整理 (meal_time 模式): 依序標出早/中/晚餐、住宿排到當天最後面, 排序更像正常行程
            autoArrangeItinerary(itinerary.getITID(), "meal_time");
        } catch (Exception e) {
            // AI 沒設定 API Key / 呼叫失敗 / 回應解析失敗都不應該讓「建立行程」整個失敗,
            // 保留已經建立好的空白行程骨架, 讓使用者可以照原本流程手動編排 ——
            // 但一定要留下 log, 不然「AI 排程失敗」永遠只會看到畫面上那句籠統的提示, 沒辦法從外面判斷
            // 真正原因是 API Key 沒設定、AI 回應被截斷、還是候選景點清單太大讓 AI 輸出格式跑掉。
            LOGGER.warn("AI 安排行程失敗 (ITID={}, country={}, region={}, daysCount={}, candidates={}): {}",
                    itinerary.getITID(), country, region, daysCount, candidates.size(), e.toString(), e);
        }

        return itinerary;
    }

    // 把資料庫裡的 POI 分類 (中文: 景點/餐廳/飯店/休息站/機場/交通/購物, 使用者要求資料庫維持中文,
    // 不要轉成英文) 轉成行程項目的分類 (attraction/meal/hotel/..., 這個是 itinerary_item.item_type
    // 欄位, 跟 poi.category 是兩個獨立的欄位, item_type 維持英文), 跟前端地圖上「點灰色建議標記
    // 直接加入行程」用的判斷邏輯一致 (見 board.html)
    private String mapPoiCategoryToItemType(String poiCategory) {
        if (poiCategory == null) return "attraction";
        return switch (poiCategory) {
            case "餐廳" -> "meal";
            case "飯店" -> "hotel";
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

    // 修正「AI 安排行程」排出來的住宿在天數之間來回跳動的怪狀況 (見 createItineraryWithAiPlan 呼叫端說明)：
    // 掃過每一天的住宿 (呼叫這個之前已經確保每天剛好 0 或 1 筆), 如果第 i 天、第 i+2 天是同一間, 但中間
    // 第 i+1 天卻是別間, 代表這極可能是 AI 亂跳、不是真的換城市, 把第 i+1 天也改成跟前後一致。
    private void normalizeHotelContiguity(List<ItineraryDay> days) {
        ItineraryItem[] hotelByDay = new ItineraryItem[days.size()];
        for (int i = 0; i < days.size(); i++) {
            hotelByDay[i] = itineraryItemDAO.findByDay(days.get(i).getIDID()).stream()
                    .filter(item -> "hotel".equals(item.getItemType()))
                    .findFirst().orElse(null);
        }
        for (int i = 1; i < hotelByDay.length - 1; i++) {
            ItineraryItem prev = hotelByDay[i - 1];
            ItineraryItem curr = hotelByDay[i];
            ItineraryItem next = hotelByDay[i + 1];
            if (prev == null || curr == null || next == null) continue;
            if (samePlace(prev, next) && !samePlace(prev, curr)) {
                curr.setPID(prev.getPID());
                curr.setCustomName(prev.getCustomName());
                curr.setStayDurationMin(prev.getStayDurationMin());
                itineraryItemDAO.save(curr);
                hotelByDay[i] = curr; // 更新暫存陣列, 避免後面比對用到修正前的舊資料
            }
        }
    }

    private boolean samePlace(ItineraryItem a, ItineraryItem b) {
        if (a.getPID() != null && b.getPID() != null) return a.getPID().equals(b.getPID());
        return java.util.Objects.equals(a.getCustomName(), b.getCustomName());
    }

    // ------------------------------------------------------------
    // 建立行程時「行程重點資訊」填的去程/回程班機 → 自動轉成行程項目
    // ------------------------------------------------------------

    /**
     * 建立行程 (不管是「建立行程並進入看板」的空白行程, 還是「AI 安排行程」排完初稿之後) 都會呼叫這個：
     * 如果使用者在「行程重點資訊」填了去程/回程班機的機場/時間, 各自轉成 item_type=transport 的項目,
     * 去程整批固定放第一天最前面 (原本第一天已經有的項目全部往後推), 回程整批固定加在最後一天最後面。
     * 每個方向都支援「+新增航段」多筆 (例如轉機), 用同一個表單欄位名稱重複送出多筆, 對應到同一個 index
     * 位置的出發機場/出發時間/抵達機場/抵達時間組成一個航段, 依填寫順序依序插入 (不會打亂順序)。
     * 兩個方向各自獨立判斷: 只填了去程沒填回程 (或反過來) 也可以, 某一個航段列四個欄位都沒填就直接跳過那一列。
     *
     * 這只是建立當下決定「初始位置放最前面/最後面」, 不是釘死不能動的特殊項目 —— 建立後這些項目
     * 跟其他項目一樣, 使用者可以照常拖曳排序、編輯、刪除。
     *
     * 呼叫時機很重要: 一定要在 createItineraryWithAiPlan() 內部的 autoArrangeItinerary() 執行完之後才呼叫
     * (也就是整個建立流程 - 包含 AI 排程 - 全部跑完, controller 拿到回傳的 Itinerary 之後才呼叫這個方法),
     * 不然去程/回程班機插入的位置會被後面的自動整理重新洗牌, 沒辦法保證「最前面/最後面」。
     */
    // outDepDay/retDepDay: 每個航段對應「第幾天」(1-based, 跟 outDepAirport 等 List 用同一個 index 對齊)。
    // 選填 —— 沒填/填錯/超出範圍的航段, 去程預設回到第一天、回程預設回到最後一天 (跟改版前的行為一致),
    // 這樣才能表達「出發當天先搭國內線轉機、隔天才搭跨國夜間班機」這種橫跨多天的去程/回程行程。
    public void attachFlightItems(int ITID,
                                  List<String> outDepAirport, List<String> outDepTime,
                                  List<String> outArrAirport, List<String> outArrTime,
                                  List<String> outDepDay,
                                  List<String> retDepAirport, List<String> retDepTime,
                                  List<String> retArrAirport, List<String> retArrTime,
                                  List<String> retDepDay) {
        List<ItineraryDay> days = itineraryDayDAO.findByItinerary(ITID); // 已依 day_number ASC 排序
        if (days.isEmpty()) return;

        attachFlightLegsAcrossDays(days, true, "去程班機", outDepAirport, outDepTime, outArrAirport, outArrTime, outDepDay);

        // 回程跟去程分開兩批處理, 就算某一段回程跟某一段去程被分到同一天, 各自的 findByDay() 也是即時查詢,
        // 不會漏算對方剛剛插入的項目。
        attachFlightLegsAcrossDays(days, false, "回程班機", retDepAirport, retDepTime, retArrAirport, retArrTime, retDepDay);
    }

    // isOutbound=true (去程): 每個航段所在那一天, 這批同一天的航段固定插在「那一天」的最前面 (依填寫順序、維持先後關係,
    // 其餘項目全部往後推); false (回程): 依填寫順序 append 在「那一天」的最後面。
    // 四個內容 List 用同一個 index 對齊組成一個航段, 缺的欄位留空; dayIndexes 是每個航段對應的天數 (見上方欄位說明)。
    private void attachFlightLegsAcrossDays(List<ItineraryDay> days, boolean isOutbound, String label,
                                            List<String> depAirports, List<String> depTimes,
                                            List<String> arrAirports, List<String> arrTimes,
                                            List<String> dayIndexes) {
        int legCount = Math.max(Math.max(listSize(depAirports), listSize(depTimes)),
                                Math.max(listSize(arrAirports), listSize(arrTimes)));
        if (legCount == 0) return;

        int defaultDayIndex = isOutbound ? 1 : days.size();

        // 用 LinkedHashMap 依「第一次出現的天數」順序分組, 同一天裡面的航段維持原本填寫的先後順序
        Map<Integer, List<ItineraryItem>> legsByDay = new LinkedHashMap<>();
        for (int i = 0; i < legCount; i++) {
            String fromAirport = listGet(depAirports, i);
            java.time.LocalTime depTime = parseTimeOrNull(listGet(depTimes, i));
            String toAirport = listGet(arrAirports, i);
            java.time.LocalTime arrTime = parseTimeOrNull(listGet(arrTimes, i));
            if (isBlank(fromAirport) && isBlank(toAirport) && depTime == null && arrTime == null) continue; // 這個航段整列都沒填, 跳過

            int dayIndex = parseDayIndexOrDefault(listGet(dayIndexes, i), defaultDayIndex, days.size());
            ItineraryDay targetDay = days.get(dayIndex - 1);

            // 只有一段就直接用「去程班機」；有多段 (轉機/跨日) 才加編號「去程班機1」「去程班機2」方便分辨先後順序
            String segmentLabel = legCount > 1 ? (label + (i + 1)) : label;
            String customName = buildFlightLabel(segmentLabel, fromAirport, toAirport);
            ItineraryItem item = new ItineraryItem(targetDay.getIDID(), null, "transport", customName, 0); // sort_order 最後統一算
            item.setFromLocation(isBlank(fromAirport) ? null : fromAirport.trim());
            item.setToLocation(isBlank(toAirport) ? null : toAirport.trim());
            item.setTransportMethod("飛機");
            item.setStartTime(depTime);
            item.setEndTime(arrTime);
            // 機場欄位是自由文字 (沒有連結 POI/經緯度), 沒有座標可以畫在地圖上, 關掉顯示在地圖上避免出現錯誤定位點
            item.setShowOnMap(false);

            legsByDay.computeIfAbsent(dayIndex, k -> new ArrayList<>()).add(item);
        }
        if (legsByDay.isEmpty()) return; // 每一列都沒填, 這個方向不用建立任何項目

        for (Map.Entry<Integer, List<ItineraryItem>> entry : legsByDay.entrySet()) {
            int IDID = days.get(entry.getKey() - 1).getIDID();
            List<ItineraryItem> legItems = entry.getValue();
            List<ItineraryItem> existing = itineraryItemDAO.findByDay(IDID);
            if (isOutbound) {
                for (ItineraryItem it : existing) {
                    it.setSortOrder(it.getSortOrder() + legItems.size());
                    itineraryItemDAO.save(it);
                }
                for (int i = 0; i < legItems.size(); i++) {
                    legItems.get(i).setSortOrder(i);
                    itineraryItemDAO.save(legItems.get(i));
                }
            } else {
                int nextOrder = existing.size();
                for (ItineraryItem legItem : legItems) {
                    legItem.setSortOrder(nextOrder++);
                    itineraryItemDAO.save(legItem);
                }
            }
        }
    }

    // 算出「去程」或「回程」這一個方向的班機表單資料實際會落在哪幾天 (1-based day number 的集合)——
    // 判斷「這個航段有沒有填」「第幾天沒填/填錯要退回哪個預設值」的規則, 刻意跟 attachFlightLegsAcrossDays()
    // 完全一致, 這樣才能保證「這裡算出來 day1HasFlight/lastDayHasFlight = true」跟「attachFlightItems() 之後
    // 那一天真的會出現一個 transport 項目」是同一件事、不會兜不起來。只需要天數集合、不需要真的建立
    // ItineraryItem, 所以不用像 attachFlightLegsAcrossDays() 一樣要吃 List<ItineraryDay>，直接吃 daysCount 就夠。
    private Set<Integer> resolveFlightDayNumbers(int daysCount,
                                                 List<String> depAirports, List<String> depTimes,
                                                 List<String> arrAirports, List<String> arrTimes,
                                                 List<String> dayIndexes, int defaultDayIndex) {
        Set<Integer> result = new HashSet<>();
        int legCount = Math.max(Math.max(listSize(depAirports), listSize(depTimes)),
                                Math.max(listSize(arrAirports), listSize(arrTimes)));
        for (int i = 0; i < legCount; i++) {
            String fromAirport = listGet(depAirports, i);
            java.time.LocalTime depTime = parseTimeOrNull(listGet(depTimes, i));
            String toAirport = listGet(arrAirports, i);
            java.time.LocalTime arrTime = parseTimeOrNull(listGet(arrTimes, i));
            if (isBlank(fromAirport) && isBlank(toAirport) && depTime == null && arrTime == null) continue; // 這個航段整列都沒填, 跟 attachFlightLegsAcrossDays() 一樣跳過

            int dayIndex = parseDayIndexOrDefault(listGet(dayIndexes, i), defaultDayIndex, daysCount);
            result.add(dayIndex);
        }
        return result;
    }

    // 把使用者填的「第幾天」字串轉成合法的天數 index (1-based); 沒填/不是數字/超出這個行程實際天數範圍
    // 都退回 defaultValue (去程預設第一天、回程預設最後一天), 不要讓格式錯誤直接讓建立行程失敗。
    private int parseDayIndexOrDefault(String raw, int defaultValue, int totalDays) {
        if (isBlank(raw)) return defaultValue;
        try {
            int idx = Integer.parseInt(raw.trim());
            if (idx < 1 || idx > totalDays) return defaultValue;
            return idx;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // 組出項目清單上顯示的名稱, 例如「去程班機：桃園國際機場 → 東京成田機場」; 只填一邊機場就退化成「XX 出發」/「抵達 XX」
    private String buildFlightLabel(String label, String fromAirport, String toAirport) {
        boolean hasFrom = !isBlank(fromAirport);
        boolean hasTo = !isBlank(toAirport);
        if (hasFrom && hasTo) return label + "：" + fromAirport.trim() + " → " + toAirport.trim();
        if (hasFrom) return label + "：" + fromAirport.trim() + " 出發";
        if (hasTo) return label + "：抵達 " + toAirport.trim();
        return label;
    }

    // 表單 <input type="time"> 送出的是 "HH:mm"，沒填就是空字串／null，兩種都當作沒填處理
    private java.time.LocalTime parseTimeOrNull(String time) {
        if (isBlank(time)) return null;
        try {
            return java.time.LocalTime.parse(time.trim());
        } catch (Exception e) {
            return null; // 格式不對就當沒填, 不要讓建立行程整個失敗
        }
    }

    private int listSize(List<String> list) { return list == null ? 0 : list.size(); }

    private String listGet(List<String> list, int index) { return (list != null && index < list.size()) ? list.get(index) : null; }

    // 「AI 安排行程」候選清單依類別各自的上限 —— region 篩不到候選、退回整個國家層級 (見
    // createItineraryWithAiPlan 的 fallback), 或本來就是熱門大國/多城市行程時, 候選景點動輒上百筆,
    // 全部塞進同一個 prompt 容易讓 AI 輸出格式跑掉 (JSON 解析失敗) 或被 max_tokens 截斷, 使用者只會看到
    // 籠統的「AI 排程失敗」訊息, 但完全沒有出現這個問題的線索。
    private static final int MAX_AI_ATTRACTIONS = 60;
    private static final int MAX_AI_RESTAURANTS = 30;
    private static final int MAX_AI_HOTELS = 15;

    // 把完整的候選景點清單依類別各自抽樣縮小成一份「給 AI 排程用」的子清單。用等距抽樣 (不是單純砍掉
    // 清單後半段) 讓抽到的候選盡量散布在整個清單範圍, 不會系統性地漏掉排序在後面的地點；候選數量本來就
    // 沒超過上限的類別完全不受影響。這個子清單只影響「AI prompt 裡列出哪些候選」, createItineraryWithAiPlan
    // 呼叫端逐天餐廳/飯店自動補位、把 AI 選到的 pid 對應回真正的 Poi, 用的都還是完整的 candidates 清單。
    private List<Poi> buildAiCandidatePool(List<Poi> candidates) {
        List<Poi> attractions = new ArrayList<>();
        List<Poi> restaurants = new ArrayList<>();
        List<Poi> hotels = new ArrayList<>();
        List<Poi> others = new ArrayList<>(); // 休息站/機場/交通/購物等其他分類, 數量通常不多, 不特別設上限
        for (Poi p : candidates) {
            String category = p.getCategory();
            if ("餐廳".equals(category)) restaurants.add(p);
            else if ("飯店".equals(category)) hotels.add(p);
            else if ("景點".equals(category)) attractions.add(p);
            else others.add(p);
        }
        List<Poi> pool = new ArrayList<>();
        pool.addAll(sampleEvenly(attractions, MAX_AI_ATTRACTIONS));
        pool.addAll(sampleEvenly(restaurants, MAX_AI_RESTAURANTS));
        pool.addAll(sampleEvenly(hotels, MAX_AI_HOTELS));
        pool.addAll(others);
        return pool;
    }

    // 等距抽樣: 清單本來就沒超過上限就整份原樣回傳; 超過的話依固定間距抽出 max 筆, 讓抽樣結果涵蓋
    // 整個清單的頭尾範圍 (而不是永遠只拿排序在最前面或最後面的那一段)。
    private List<Poi> sampleEvenly(List<Poi> list, int max) {
        if (list.size() <= max || max <= 0) return list;
        List<Poi> sampled = new ArrayList<>();
        double step = (double) list.size() / max;
        for (int i = 0; i < max; i++) {
            sampled.add(list.get((int) (i * step)));
        }
        return sampled;
    }

    // 把候選景點清單丟給 AI, 請它只從清單裡挑選 PID 並安排每一天要去哪些, 回傳 {天數 -> [PID,...]}
    // description: 使用者在「建立新行程」頁面「行程重點資訊」填的行程說明 (選填, 例如「第一天到東京,
    // 第二天去河口湖...」), 有填的話一併丟給 AI 當作額外的規劃參考, 讓排出來的初稿更貼近使用者想法。
    private Map<Integer, List<Integer>> planDaysWithAi(String country, String region, int daysCount, List<Poi> candidates, String description) throws Exception {
        String system = """
            你是旅遊行程規劃助手, 負責幫旅行社從「已有的景點/餐廳/飯店資料庫」裡挑選並安排出一份 N 天的行程初稿。
            使用者會給你這個國家/地區在資料庫裡「所有可用」的候選清單 (每筆有 pid / name / category / stay_min),
            以及總共要安排幾天。你的任務是決定每一天要排哪幾個地點、排幾個, 只能使用候選清單裡出現過的 pid,
            絕對不可以自己生出候選清單沒有的地點或 pid。

            規則:
            - category=景點 的排每天 2~4 個當作主要行程。
            - category=餐廳 的每天應該排剛好 3 個 (早餐、午餐、晚餐都要有), 不要只排 1 個或 2 個就結束那一天；
              只有候選餐廳數量真的太少 (整個候選清單裡少於 3 間不同的餐廳) 才可以少於 3 個。
            - category=飯店 的每天恰好安排 1 個。同一趟行程如果只有 1 間候選飯店, 就整趟都排這一間;
              如果有多間候選飯店, 決定要換到某一間之後就要連續排到不需要再換為止才能換下一間,
              絕對不可以「換過去又換回原本那間」來回跳動 (例如 Day1=A, Day2=B, Day3=A 這種安排是禁止的；
              Day1=A, Day2=A, Day3=B 這種「換過去就不再換回來」的連續分段才是允許的)。
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
        if (description != null && !description.isBlank()) {
            userContent.append("\n使用者填寫的行程重點/期望安排 (請優先參考, 但還是只能挑選候選清單裡有的 pid): ")
                    .append(expandLocationCodesForAi(description.trim()));
        }
        userContent.append("\n候選景點清單 (JSON 陣列, 每筆是 pid/name/category/stay_min):\n");
        userContent.append("[");
        for (int i = 0; i < candidates.size(); i++) {
            Poi poi = candidates.get(i);
            if (i > 0) userContent.append(",");
            userContent.append("{\"pid\":").append(poi.getPID())
                    .append(",\"name\":\"").append(poi.getName() != null ? poi.getName().replace("\"", "") : "")
                    .append("\",\"category\":\"").append(poi.getCategory() != null ? poi.getCategory() : "景點")
                    .append("\",\"stay_min\":").append(poi.getSuggestedStayMin() != null ? poi.getSuggestedStayMin() : 60)
                    .append("}");
        }
        userContent.append("]");

        // maxTokens 從 4000 提高到 8000: 候選清單雖然已經在呼叫端 (buildAiCandidatePool) 依類別做過上限抽樣,
        // 但天數多 (例如 10 天) 加上每天要排 2~4 個景點 + 3 餐 + 1 住宿, 完整 JSON 輸出還是可能逼近舊上限,
        // 一旦被 max_tokens 截斷, 回應會變成不完整的 JSON, 解析直接失敗、整趟 AI 排程等於白跑。
        String response = anthropicClient.complete(system, userContent.toString(), 8000);
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

    // 使用者填在「行程說明」的自由文字裡, 如果打的是國家/城市代碼 (例如「JP五天四夜」「先去TYO再去OSA」)
    // 而不是中文全名, 直接丟給 AI 雖然大多時候 AI 自己也看得懂常見代碼, 但這裡還是先做一次明確的展開,
    // 把辨識得出來的代碼都在後面補上中文全名的括號註記 (例如「JP五天四夜」變成「JP（日本）五天四夜」),
    // 讓 AI 拿到的提示文字不會曖昧不清 (不影響候選景點清單本身──候選清單早在這之前就已經依表單上的
    // 「目的地國家/地區」欄位篩好了, 這裡只是讓 AI 更看得懂使用者這段自由文字裡指的是哪裡, 幫助它排出
    // 更符合期望的順序, 不會因為這裡的展開而多出或少掉候選清單以外的地點)。
    // 只在原始存進資料庫的 itinerary.description 之外, 另外組一份「展開後」的文字給 AI prompt 用,
    // 使用者畫面上看到、之後編輯行程時看到的行程說明本身完全不會被這裡的展開結果覆蓋或竄改。
    private String expandLocationCodesForAi(String description) {
        Map<String, String> codeToName = new HashMap<>();
        for (var c : countryCityCodeDAO.findByType("country")) {
            if (c.getCode() != null && c.getName() != null) codeToName.put(c.getCode().toUpperCase(), c.getName());
        }
        for (var c : countryCityCodeDAO.findByType("city")) {
            if (c.getCode() != null && c.getName() != null) codeToName.put(c.getCode().toUpperCase(), c.getName());
        }
        if (codeToName.isEmpty()) return description;

        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\b[A-Za-z]{2,4}\\b").matcher(description);
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;
        while (matcher.find()) {
            String token = matcher.group();
            String name = codeToName.get(token.toUpperCase());
            result.append(description, lastEnd, matcher.end());
            lastEnd = matcher.end();
            if (name == null) continue;
            // 使用者自己已經在代碼後面用括號寫了說明 (例如「JP（日本）」), 就不用重複註記
            int after = matcher.end();
            if (after < description.length() && (description.charAt(after) == '（' || description.charAt(after) == '(')) {
                continue;
            }
            result.append("（").append(name).append("）");
        }
        result.append(description.substring(lastEnd));
        return result.toString();
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

    // AI/手動輸入的 item_type (英文) 對應到 POI 資料庫的 category (transport/highlight 不是實體地點, 不能加入)。
    // 使用者要求 poi.category 維持中文, 這裡要用跟 poi/new.html、poi/edit.html、poi/list.html、
    // PoiController、AiParseService 一致的中文 category 值 (景點/餐廳/飯店), 不能用英文,
    // 不然新建立的 POI 會跟畫面上的類型篩選/自動完成對不上
    private String mapItemTypeToPoiCategory(String itemType) {
        if (itemType == null) return null;
        return switch (itemType) {
            case "attraction" -> "景點";
            case "meal" -> "餐廳";
            case "hotel" -> "飯店";
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
            if (poi != null) {
                if (poi.getLatitude() != null) {
                    item.setLatitude(poi.getLatitude());
                    item.setLongitude(poi.getLongitude());
                }
                // 使用者要求: 從景點資料庫加入的項目要帶出該景點自己設定的建議停留時間, 呼叫端 (前端「加入行程」
                // 按鈕) 目前不會傳 stayDurationMin 進來, 一律是 null, 這裡補上這個預設值; 如果呼叫端有明確
                // 指定 (例如 AI 解析出來的預估停留時間), 還是以呼叫端傳進來的值為準, 不會被這裡蓋掉。
                if (stayDurationMin == null && poi.getSuggestedStayMin() != null) {
                    item.setStayDurationMin(poi.getSuggestedStayMin());
                }
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
                null, null, null, null, null, null, null, null, null);
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
     * @param startTime        交通項目專用: 出發時間 ("HH:mm", 選填); 傳 null 代表不更動, 傳空字串會清空。
     *                         建立行程時「去程/回程班機」表單填的時間就是存在這裡 (見 attachFlightLegsAcrossDays),
     *                         但建立之後原本完全沒有地方可以編輯——這裡補上編輯入口, 讓使用者事後也能補填/修正。
     * @param endTime          交通項目專用: 抵達時間 ("HH:mm", 選填), 用法同 startTime
     */
    public void updateItemDetails(int IIID, String customName, Integer stayDurationMin, String locationHint,
                                  String timeSlot, String note, Boolean showOnMap,
                                  String itemType, String fromLocation, String fromAddress,
                                  String toLocation, String toAddress, String transportMethod, String commuteDuration,
                                  String startTime, String endTime) {
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
        if (startTime != null) {
            item.setStartTime(startTime.isBlank() ? null : parseTimeOrNull(startTime));
        }
        if (endTime != null) {
            item.setEndTime(endTime.isBlank() ? null : parseTimeOrNull(endTime));
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
        // 使用者要求: 兩個行程點距離在 1 公里以內要預設走路 (RouteService.recommendMode() 本來就有這個規則),
        // 但這條規則實際上一直沒有真的生效: ItineraryDay.transportMode 欄位不管是資料庫欄位預設值還是
        // entity 的預設值都是 "driving"（不是 null), 前端也已經把「整天強制切換走路/開車」的下拉選單拿掉、
        // 改成每一段各自獨立選 (board.html 不再呼叫 /day/{IDID}/transport-mode), 所以這裡原本的判斷式
        // 幾乎每次都會拿到 "driving" 而不是 "auto"，導致 recommendMode() 的距離判斷永遠被跳過、每一段都
        // 被強制當開車算。既然「整天強制」這個功能在現在的畫面上已經沒有入口可以觸發, 這裡固定改成 "auto",
        // 讓每一段預設都套用 recommendMode() 的距離規則 (<=1公里走路, 否則開車); 使用者在看板上對某一段
        // 手動選過的走路/開車, 已經由上面的 overrides 機制保留下來, 不會被這裡蓋掉。
        String transportMode = "auto";
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