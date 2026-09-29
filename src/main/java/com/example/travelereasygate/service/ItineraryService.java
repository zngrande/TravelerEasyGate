package com.example.travelereasygate.service;

import com.example.travelereasygate.DAO.AiImportDAO;
import com.example.travelereasygate.DAO.CountryCityCodeDAO;
import com.example.travelereasygate.DAO.ItineraryComponentDAO;
import com.example.travelereasygate.DAO.ItineraryDAO;
import com.example.travelereasygate.DAO.ItineraryDayDAO;
import com.example.travelereasygate.DAO.ItineraryItemDAO;
import com.example.travelereasygate.DAO.ItineraryItemOptionDAO;
import com.example.travelereasygate.DAO.PoiDAO;
import com.example.travelereasygate.DAO.QuotationDAO;
import com.example.travelereasygate.DAO.QuotationLineDAO;
import com.example.travelereasygate.DAO.RouteSegmentDAO;
import com.example.travelereasygate.entity.Itinerary;
import com.example.travelereasygate.entity.ItineraryComponent;
import com.example.travelereasygate.entity.ItineraryDay;
import com.example.travelereasygate.entity.ItineraryItem;
import com.example.travelereasygate.entity.ItineraryItemOption;
import com.example.travelereasygate.entity.Poi;
import com.example.travelereasygate.entity.Quotation;
import com.example.travelereasygate.entity.RouteSegment;
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
    private final QuotationDAO quotationDAO;
    private final QuotationLineDAO quotationLineDAO;
    private final ItineraryComponentDAO itineraryComponentDAO;

    @Autowired
    public ItineraryService(ItineraryDAO itineraryDAO, ItineraryDayDAO itineraryDayDAO,
                            ItineraryItemDAO itineraryItemDAO, ItineraryItemOptionDAO itineraryItemOptionDAO,
                            RouteSegmentDAO routeSegmentDAO,
                            RouteService routeService, PoiDAO poiDAO, AiImportDAO aiImportDAO,
                            GoogleMapsClient googleMapsClient, AnthropicClient anthropicClient,
                            ObjectMapper objectMapper, CountryCityCodeDAO countryCityCodeDAO,
                            QuotationDAO quotationDAO, QuotationLineDAO quotationLineDAO,
                            ItineraryComponentDAO itineraryComponentDAO) {
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
        this.quotationDAO = quotationDAO;
        this.quotationLineDAO = quotationLineDAO;
        this.itineraryComponentDAO = itineraryComponentDAO;
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
        // (String) 轉型是必要的: 下面新增了 dayCities (List<String>) 多載後, 傳 null 字面值本身在
        // description(String) 跟 dayCities(List<String>) 兩個多載之間會編譯不過 (型別無法推斷選哪一個)
        return createItinerary(AID, createdBy, title, country, region, daysCount, startDate, (String) null);
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

    /**
     * Patch 27: 「建立新行程」頁面的「行程說明」自由文字欄位改成逐天指定城市的下拉選單 —— 這個多載接
     * dayCities (index 0 對應第 1 天、index 1 對應第 2 天...), 建立每一天的骨架時順便把這天指定的城市
     * 存進 ItineraryDay.plannedCities。沒有指定城市的天 (通常是班機/轉機日, 前端會自動停用選單, 送出
     * 空字串) 存 NULL, 「AI 安排行程」看到 NULL 會完全跳過這天, 不強制排任何景點/餐食/住宿
     * (見 createItineraryWithAiPlan 說明)。
     */
    public Itinerary createItinerary(int AID, int createdBy, String title, String country, String region,
                                     int daysCount, LocalDate startDate, List<String> dayCities) {
        Itinerary itinerary = new Itinerary(AID, createdBy, title, country, daysCount);
        itinerary.setRegion(region);
        itinerary.setStartDate(startDate);
        if (startDate != null) {
            itinerary.setEndDate(startDate.plusDays(daysCount - 1));
        }
        itineraryDAO.save(itinerary);

        for (int d = 1; d <= daysCount; d++) {
            LocalDate dayDate = startDate != null ? startDate.plusDays(d - 1) : null;
            ItineraryDay day = new ItineraryDay(itinerary.getITID(), d, dayDate, null);
            String cities = (dayCities != null && d - 1 < dayCities.size()) ? dayCities.get(d - 1) : null;
            day.setPlannedCities((cities != null && !cities.isBlank()) ? cities.trim() : null);
            itineraryDayDAO.save(day);
        }
        return itinerary;
    }

    public List<ItineraryDay> getDays(int ITID) {
        return itineraryDayDAO.findByItinerary(ITID);
    }

    /**
     * 「AI 安排行程」: 跟一般建立行程一樣先產生 Day1~DayN 骨架, 但接著會用 AI 從「這個國家在公司
     * POI 資料庫裡已有的景點/餐廳/飯店」中挑選、安排進每一天, 不是憑空生出資料庫沒有的地點。
     * 用在「建立新行程」頁面的「AI 安排行程」按鈕 (跟旁邊「建立行程並進入看板」的差別只在於這個會先幫忙排好初稿)。
     *
     * Patch 27: 原本整趟行程共用一段自由文字「行程說明」給 AI 參考, 改成逐天指定城市 (dayCities,
     * 用法跟 createItinerary(..., List<String> dayCities) 完全一致)。這個改動同時修正兩個問題:
     *   1) 使用者填的行程說明文字複雜時 (提到候選清單沒有的地點/城市), AI 偶爾會在 JSON 前後夾帶
     *      解說文字、甚至完全跑題, 造成「AI 排程失敗」——逐天城市是結構化資料, 不會有這個問題。
     *   2) 沒有指定城市的天 (前端只會讓「去程班機最後一天」「回程班機第一天」可以選城市, 其餘班機/轉機日
     *      完全不能選) 會拿到「零候選」, AI 跟後面逐天餐食/住宿補位都完全不會排任何東西進這天, 不再有
     *      班機轉機日被排進一整天觀光行程的問題; 同時每一天的候選景點都先依這天指定的城市篩過, 不會
     *      再發生「不同城市的餐廳/飯店在天數之間亂跳」的狀況 (詳見 planDaysWithAiPerDay 說明)。
     *
     * @return 建好的 Itinerary。如果這個國家在資料庫裡完全找不到候選景點、或 AI 呼叫失敗,
     *         仍然會回傳建立好的行程 (退回成跟原本一樣的空白行程), 呼叫端可以用 hasAnyItem() 判斷要不要提示使用者。
     *
     * 使用者反映「AI 排程排不出東西, 追查後發現是因為沒填寫逐天城市指定——這應該要是選填才對」:
     * 前端「逐天城市指定」的說明文字明明寫「全部欄位皆選填」, 但這裡原本把「這天沒填城市」一律當成
     * 「班機/轉機日」處理 (候選清單直接給空的, 這天完全不排任何東西)——對真正的班機/轉機日 (前端會
     * 自動停用下拉選單、隱藏欄位, 使用者根本填不了) 這樣處理是對的; 但對使用者自己選擇不填的一般
     * 日期 (前端下拉選單還是有顯示、可以選, 使用者只是選擇不填), 就會被誤判成班機日, 整天排不出任何
     * 東西, 卻沒有任何提示。
     *
     * 修正: 新增 flightDayNumbers 參數 (由呼叫端 ItineraryController 用 computeFlightDayNumbers()
     * 算出來, 規則跟 attachFlightItems()/前端 computeDisabledFlightDays() 完全一致), 用來分辨
     * 「這天沒填城市」到底是真正的班機/轉機日, 還是使用者自己選擇不填的一般日期——只有真正的班機/
     * 轉機日才維持原本「完全跳過, 不排任何東西」的行為; 一般日期沒填城市則退回整個國家/地區的候選
     * 景點 (等同 Patch 27 之前的行為), 不會再整天排不出東西。
     */
    /**
     * Patch 82: 使用者反映「AI 沒有找到景點資料」這個提示訊息太籠統, 每次都要另外拜託使用者去挖伺服器 log
     * 才能分辨「資料庫真的沒有這個國家的景點 (資料問題)」還是「候選景點有找到、但 AI 沒排出結果 (AI 呼叫
     * 本身失敗)」——這兩種情況處理方式完全不同 (前者要去補景點資料/確認帳號, 後者是系統暫時性問題)。
     * 這裡把 createItineraryWithAiPlan() 一開始查候選景點的邏輯獨立抽成這個方法, 讓 Controller 在
     * aiFoundNothing 時可以直接呼叫、把明確的原因直接顯示在畫面上的提示訊息裡, 使用者自己就能判斷,
     * 不用每次都要來回要求對方去撈 log。
     *
     * @return 明確的原因說明 (資料庫查無資料 / 候選有找到但 AI 沒排出結果 / 查詢本身出例外), 給畫面提示用。
     */
    public String diagnoseAiPlanEmptyReason(int AID, String country, String region) {
        List<Poi> candidates;
        try {
            candidates = poiDAO.findByAgencyAndCountry(AID, country, region);
            if (candidates.isEmpty() && region != null && !region.isBlank()) {
                candidates = poiDAO.findByAgencyAndCountry(AID, country, null);
            }
        } catch (Exception e) {
            return "查詢景點資料庫時發生錯誤 (" + e.getClass().getSimpleName() + ": " + e.getMessage()
                    + ")——這是資料庫查詢本身出問題, 不是 AI 排程失敗, 請聯絡系統管理員確認資料庫狀態。";
        }
        if (candidates.isEmpty()) {
            return "資料庫裡完全查不到「" + country + (region != null && !region.isBlank() ? " / " + region : "")
                    + "」相關的景點資料 (貴帳號自建 + 平台共用庫合計候選數 = 0)。"
                    + "這是資料庫裡真的沒有資料, 不是 AI 排程邏輯的問題——請確認「景點管理」頁用同樣的國家"
                    + "篩選是否也查不到任何東西 (如果查得到, 麻煩回報這個不一致給我們), 或聯絡管理員確認"
                    + "這個帳號有沒有建立/複製過這個國家的景點。";
        }
        return "資料庫裡有找到 " + candidates.size() + " 筆「" + country + "」候選景點, 但 AI 沒有從裡面排出任何"
                + "結果——這比較像是 AI 呼叫失敗或回應格式跑掉這類暫性問題, 不是景點資料不足, 可以先試著重新"
                + "按一次「AI 安排行程」; 如果重試後還是一樣, 麻煩把伺服器 log 裡「AI 安排行程」開頭的那幾行"
                + "WARN/ERROR 訊息提供給我們, 才能確定 AI 呼叫實際失敗的原因。";
    }

    public Itinerary createItineraryWithAiPlan(int AID, int createdBy, String title, String country, String region,
                                               int daysCount, LocalDate startDate, List<String> dayCities,
                                               Set<Integer> flightDayNumbers) {
        Itinerary itinerary = createItinerary(AID, createdBy, title, country, region, daysCount, startDate, dayCities);

        List<Poi> candidates;
        try {
            candidates = poiDAO.findByAgencyAndCountry(AID, country, region);
            if (candidates.isEmpty() && region != null && !region.isBlank()) {
                // 地區篩選完全找不到候選景點時, 先退回成只用國家篩選再試一次 ——
                // 使用者選的地區/城市 (例如「佛羅倫斯、威尼斯、比薩、米蘭、羅馬」) 常常跟
                // poi.city 實際存的字串沒辦法字面 exact match 上, 但這個國家在資料庫裡其實有大量景點,
                // 不應該只因為城市名稱顆粒度對不上就整個放棄、建出空白行程。
                candidates = poiDAO.findByAgencyAndCountry(AID, country, null);
            }
        } catch (Exception e) {
            // 使用者反映「AI安排行程報錯」(畫面直接跳系統錯誤頁, 不是回傳空白行程) ——這裡原本完全沒有
            // try/catch 保護, 是整個 createItineraryWithAiPlan() 唯一一段查資料庫查候選景點就有可能丟出
            // 例外、卻沒被下面那個大 try/catch 涵蓋到的地方 (下面那個 try 是從決定「怎麼分天篩選候選」才
            // 開始, 這兩行候選查詢在那之前)。跟下面 AI 呼叫失敗的處理方式一致: 記錄下來, 保留已經建立好的
            // 空白行程骨架, 不要讓整個「建立行程」request 直接失敗變成系統錯誤頁。
            LOGGER.warn("AI 安排行程：查詢候選景點失敗 (ITID={}, country={}, region={}): {}",
                    itinerary.getITID(), country, region, e.toString(), e);
            return itinerary;
        }
        if (candidates.isEmpty()) {
            // 使用者反映「AI 排行程」整個排不出東西、連左側手動加景點的快選面板也是空的, 但「景點管理」頁
            // 用同樣的國家/地區搜尋卻查得到資料——追查時發現查無候選這個分支之前完全沒有留 log, 沒辦法
            // 事後對照 Railway log 確認當下實際查詢用的 AID/country/region 是什麼、是不是真的候選數為 0
            // (而不是候選其實查得到、問題出在後面的 AI 呼叫本身)。這裡補一行 log, 下次同樣情況再發生時,
            // 從 log 就能直接看到這次到底查了什麼條件、查到幾筆, 不用再靠猜的。
            LOGGER.info("AI 安排行程：查無候選景點, 建立空白行程 (ITID={}, AID={}, country={}, region={})",
                    itinerary.getITID(), AID, country, region);
            return itinerary; // 這個國家在資料庫裡完全沒有景點, 保持空白行程讓使用者自己排
        }
        // 候選景點查詢有找到東西時也留一筆 log (跟上面查無候選那筆搭配), 這樣如果之後行程還是排不出來,
        // 從 log 就能分辨「候選本來就是 0」還是「候選有找到、但後面的 AI 呼叫沒選出東西」這兩種完全不同
        // 的情況, 不用每次都重新來回猜測。
        LOGGER.info("AI 安排行程：候選景點查詢完成 (ITID={}, AID={}, country={}, region={}, candidates={})",
                itinerary.getITID(), AID, country, region, candidates.size());

        List<ItineraryDay> days = itineraryDayDAO.findByItinerary(itinerary.getITID());

        try {
            // 逐天依「這天指定的城市」把候選清單再篩一次: 沒有指定城市的天 (通常是班機/轉機日) 拿到空清單,
            // AI 跟後面的餐食/住宿補位都會完全跳過這天; 有指定城市的天, 候選只會是「這個城市底下」的景點/
            // 餐廳/飯店, 不會混進別的城市的地點, 徹底解決 Patch 26 之前「餐廳/飯店在天數之間跨城市亂跳」的問題。
            //
            // 使用者反映「有選地區, 但完全沒有填『逐天城市指定』時, AI 排出來的每一天會把所有城市的景點/
            // 餐廳混在一起, 感覺像隨便排」——根因就是上面這段: 完全沒指定城市的天原本一律退回「整個國家/
            // 地區」的候選景點 (見下面 else 分支), 等於每一天都可以選到任何城市, 完全沒有「先在城市 A 玩
            // 幾天、再移動到城市 B」的概念。
            // 修正: 先算出「完全沒指定城市、也不是真正班機/轉機日」的天 (autoDayNumbers), 依「使用者選地區
            // 時的先後順序」(itinerary.region 這個「、」串接欄位, 前端 multi-select 是照選取順序 push 進去
            // 的) 把這些天自動分配給不同城市——天數 >= 城市數就依天數比例分段, 每個城市連續住差不多天數;
            // 城市數 > 天數就必須有幾天塞 2 個城市才裝得下 (使用者要求上限 2 個、盡量 1 個), 詳見
            // distributeCitiesAcrossDays() 說明。只影響「完全沒有指定城市」的天, 只要使用者自己在「逐天
            // 城市指定」填了任何一天、或這天本來就是真正的班機/轉機日, 都完全不受這個自動分配影響。
            Set<Integer> autoDayNumbers = new java.util.TreeSet<>();
            for (ItineraryDay day : days) {
                boolean isFlightDay = flightDayNumbers != null && flightDayNumbers.contains(day.getDayNumber());
                if (splitCityTokens(day.getPlannedCities()).isEmpty() && !isFlightDay) {
                    autoDayNumbers.add(day.getDayNumber());
                }
            }
            Map<Integer, List<String>> autoAssignedCities =
                    distributeCitiesAcrossDays(splitCityTokens(region), new ArrayList<>(autoDayNumbers));

            Map<Integer, List<String>> cityTokensByDay = new HashMap<>();
            Map<Integer, List<Poi>> candidatesByDay = new HashMap<>();
            for (ItineraryDay day : days) {
                List<String> tokens = splitCityTokens(day.getPlannedCities());
                List<Poi> dayCandidates;
                if (!tokens.isEmpty()) {
                    dayCandidates = filterByCityTokens(candidates, tokens);
                } else if (flightDayNumbers != null && flightDayNumbers.contains(day.getDayNumber())) {
                    // 真正的班機/轉機日 (前端自動停用、使用者填不了): 維持原本「完全跳過」的行為
                    dayCandidates = List.of();
                } else if (autoAssignedCities.containsKey(day.getDayNumber())) {
                    // 自動分配到的城市: 直接當成這天指定的城市使用 (連同下面 cityTokensByDay 一起換掉),
                    // 才能真的做到「這幾天只排這個城市」, 而不是所有城市混在一起。
                    tokens = autoAssignedCities.get(day.getDayNumber());
                    dayCandidates = filterByCityTokens(candidates, tokens);
                    if (dayCandidates.isEmpty()) {
                        // 自動分配到的城市名稱在資料庫裡比對不到任何景點 (例如地名寫法跟 poi.city 對不上),
                        // 保底退回整個國家/地區候選, 不要讓這天完全排不出東西。
                        dayCandidates = candidates;
                    }
                } else {
                    // 沒有選任何地區 (region 是空的) 時退回整個國家/地區的候選景點, 不要整天排不出任何
                    // 東西 (見上方 method 說明)
                    dayCandidates = candidates;
                }
                cityTokensByDay.put(day.getDayNumber(), tokens);
                candidatesByDay.put(day.getDayNumber(), dayCandidates);
            }

            // AI prompt 用的候選清單依類別各自再抽樣一次上限 (單一天候選數量通常已經比整個國家的候選少很多,
            // 但候選很多的大城市還是可能超過上限, 保險起見沿用跟原本一樣的抽樣邏輯, 見 buildAiCandidatePool)。
            // 逐天餐廳/飯店自動補位仍然用 candidatesByDay 裡完整的清單, 不受這個上限影響。
            Map<Integer, List<Poi>> aiPoolByDay = new HashMap<>();
            for (ItineraryDay day : days) {
                aiPoolByDay.put(day.getDayNumber(), buildAiCandidatePool(candidatesByDay.get(day.getDayNumber())));
            }

            Map<Integer, List<Integer>> plan = planDaysWithAiPerDay(country, days, cityTokensByDay, aiPoolByDay);
            if (plan.isEmpty()) return itinerary; // AI 沒排出任何結果, 一樣退回空白行程

            Map<Integer, Poi> candidateByPid = new HashMap<>();
            for (Poi poi : candidates) candidateByPid.put(poi.getPID(), poi);

            for (ItineraryDay day : days) {
                List<Integer> pids = plan.get(day.getDayNumber());
                if (pids == null) continue;
                // 防呆: 只採用「這天自己的 AI 候選池」裡出現過的 pid —— 就算 AI 沒有乖乖照系統提示詞的規則、
                // 把別天的候選 pid 排進這天, 這裡也會直接濾掉, 不會讓景點錯誤地出現在不屬於它的城市那一天。
                Set<Integer> allowedPids = aiPoolByDay.get(day.getDayNumber()).stream()
                        .map(Poi::getPID).collect(java.util.stream.Collectors.toSet());

                // 使用者反映 AI 排的當天行程動線常常「一下最南一下最北」——因為 AI 只拿得到景點的名稱/類別,
                // 完全沒有座標資訊, 只能憑名稱瞎猜先後順序。景點「要選哪些」交給 AI 判斷沒問題, 但「這天要
                // 先去哪、再去哪」改成用實際經緯度算最近鄰路徑決定, 比純文字猜測準確很多。只重排 category=景點
                // 這一類, 餐廳/飯店維持 AI 給的相對順序不動 (下面的自動整理只看「第幾個遇到的餐廳」決定
                // 午餐/晚餐, 跟景點怎麼排序無關；飯店固定會被 autoArrangeDay 排到當天最後, 也不受影響)。
                List<Poi> attractionsInOrder = new ArrayList<>();
                List<Poi> othersInOrder = new ArrayList<>();
                for (Integer pid : pids) {
                    if (!allowedPids.contains(pid)) continue;
                    Poi poi = candidateByPid.get(pid); // AI 幻覺出資料庫沒有的 PID 就直接跳過, 不要硬加
                    if (poi == null) continue;
                    if ("景點".equals(poi.getCategory())) attractionsInOrder.add(poi);
                    else othersInOrder.add(poi);
                }
                for (Poi poi : orderAttractionsByProximity(attractionsInOrder)) {
                    addItem(day.getIDID(), poi.getPID(), mapPoiCategoryToItemType(poi.getCategory()),
                            poi.getName(), poi.getSuggestedStayMin());
                }
                for (Poi poi : othersInOrder) {
                    addItem(day.getIDID(), poi.getPID(), mapPoiCategoryToItemType(poi.getCategory()),
                            poi.getName(), poi.getSuggestedStayMin());
                }
            }

            // AI 排出來的初稿不保證每天都有正確的三餐、剛好 1 間住宿——使用者反映過幾個常見的「怪」狀況：
            // (1) 明明候選餐廳數量足夠, AI 卻常常一天只排 1 餐, 不是每餐都排;
            // (2) 飯店在不同天之間跳來跳去, 例如 Day1/Day3 住 A 飯店、Day2 卻換成 B 飯店, 中間被打斷又換回來;
            // (3) 早餐被排成資料庫裡真正的某間餐廳, 但大部分旅客早餐都直接吃飯店內附的早餐, 不需要特地排一間。
            // 系統提示詞裡雖然已經有明確要求, 但不能只靠 AI 自律, 這裡用程式碼再逐天檢查、強制補齊/修正一次,
            // 不管 AI 有沒有乖乖照規則排, 結果都會是穩定的。所有補位迴圈都限定在「這天指定城市」的候選範圍內
            // (candidatesByDay), 不再共用整個國家的候選清單/全域輪替計數器——這是修正「不同城市的餐廳每兩天
            // 一輪重複出現」這個問題的關鍵。

            // (1) 早餐固定用「飯店內早餐」預留項目 (不連結 POI, 不查資料庫、不會出現在地圖上), 每天最前面
            //     一定先加這個, 讓 autoArrangeDay「這天第 1 個餐廳 = 早餐」的判斷穩定套用到它身上, 不會
            //     被 AI 自己排的某間餐廳搶走。接著只需要再確保「午餐、晚餐」各 1 個 (剛好 2 個「真的」餐廳);
            //     AI 如果沒乖乖照系統提示詞只排 2 個真的餐廳, 這裡也會把多出來的直接刪掉。
            //     輪替計數器依「城市組合」各自獨立 (restaurantRoundRobinByCity), 不會因為換了城市而互相干擾。
            Map<String, Integer> restaurantRoundRobinByCity = new HashMap<>();
            for (ItineraryDay day : days) {
                List<Poi> dayCandidates = candidatesByDay.get(day.getDayNumber());
                if (dayCandidates.isEmpty()) continue; // 沒有指定城市 (班機/轉機日) → 完全不強制補餐食

                // Patch 85: 使用者要求「第一天不需要安排早餐」——行程第一天是從國內出發搭去程班機的日子,
                // 人根本還沒入住任何飯店, 「飯店內早餐」這個預留項目對這天沒有意義 (使用者出發前吃什麼跟
                // 這份行程無關)。只跳過 day_number == 1 這一天, 其餘天數 (包含最後一天/回程班機那天) 維持
                // 原本邏輯不變, 一樣會有早餐——使用者這次沒有要求連回程那天也拿掉早餐。
                if (day.getDayNumber() != 1) {
                    addBreakfastPlaceholder(day.getIDID());
                }

                String cityKey = cityKey(cityTokensByDay.get(day.getDayNumber()));
                List<Poi> restaurantCandidates = dayCandidates.stream()
                        .filter(p -> "餐廳".equals(p.getCategory())).collect(java.util.stream.Collectors.toList());

                // 「真的」餐廳 (排除剛加的早餐預留項目, 早餐 PID 一定是 null) 數量超過 2 個, 通常是 AI 沒乖乖
                // 照系統提示詞只排 2 個, 只留前 2 個 (依原本排序當作午餐/晚餐), 其餘刪掉——確保最後結果一定是
                // 「早餐 (預留) + 午餐 + 晚餐」剛好 3 筆。
                List<ItineraryItem> realMeals = itineraryItemDAO.findByDay(day.getIDID()).stream()
                        .filter(item -> "meal".equals(item.getItemType()) && item.getPID() != null)
                        .collect(java.util.stream.Collectors.toList());
                if (realMeals.size() > 2) {
                    // Patch 84: 這裡要刪的這幾筆餐廳是透過上面 addItem() 加進來的 (line 354-357)——
                    // addItem() 自己內部每加一筆就會呼叫一次 recalculateRoutes(), 等於這個時候
                    // route_segment 表已經有連到這些項目的路段快取列, 直接刪除會被外鍵擋下來 (跟
                    // removeItem()/trimDaysExceedingCutoff() 踩過的是同一個地雷, 見那兩個地方的說明)。
                    // 刪除前先清掉這天的路段快取——後面 autoArrangeItinerary() 跑完會重新算出正確的一份,
                    // 不會少算。
                    routeSegmentDAO.deleteByDay(day.getIDID());
                    for (int i = 2; i < realMeals.size(); i++) {
                        itineraryItemDAO.deleteById(realMeals.get(i).getIIID());
                    }
                }
                int realMealCount = Math.min(realMeals.size(), 2);

                int rr = restaurantRoundRobinByCity.getOrDefault(cityKey, 0);
                while (realMealCount < 2) {
                    if (restaurantCandidates.isEmpty()) {
                        addPlaceholderItem(day.getIDID(), "meal", realMealCount == 0 ? "午餐" : "晚餐");
                    } else {
                        Poi pick = restaurantCandidates.get(rr % restaurantCandidates.size());
                        rr++;
                        addItem(day.getIDID(), pick.getPID(), "meal", pick.getName(), pick.getSuggestedStayMin());
                    }
                    realMealCount++;
                }
                restaurantRoundRobinByCity.put(cityKey, rr);
            }

            // (2) 逐天確保住宿——但最後一天 (dayNumber == daysCount) 當天通常直接離開/搭機返程, 不需要再
            //     多排一晚住宿, 不管 AI 有沒有自己排了飯店都直接刪掉, 也不會延續前一天的飯店。其餘天數:
            //     沒有的話補一間 (候選掛零一樣退回純文字預留項目); 如果 AI 同一天排了不只 1 間, 只留下第一間,
            //     其餘刪掉, 避免同一天出現兩間飯店。連續天數如果指定的城市 (組合) 相同, 直接沿用前一天選到的
            //     同一間飯店 (lastHotel/lastHotelCityKey)——這個「依城市決定要不要延續」的判斷本身就是確定性的,
            //     不會像以前那樣需要事後用 normalizeHotelContiguity() 掃描修正 A-B-A 來回跳動, 這裡從一開始
            //     就不會排出這種結果。
            String lastHotelCityKey = null;
            Poi lastHotel = null;
            for (ItineraryDay day : days) {
                List<Poi> dayCandidates = candidatesByDay.get(day.getDayNumber());
                if (dayCandidates.isEmpty()) continue; // 沒有指定城市 (班機/轉機日) → 完全不強制補住宿

                List<ItineraryItem> hotelItems = itineraryItemDAO.findByDay(day.getIDID()).stream()
                        .filter(item -> "hotel".equals(item.getItemType())).collect(java.util.stream.Collectors.toList());

                if (day.getDayNumber() == daysCount) {
                    // 行程最後一天: 不管候選/AI 有沒有排, 一律不留住宿項目。
                    // Patch 84: 這幾筆住宿是透過上面 addItem() 加進來的, 同樣會被 recalculateRoutes()
                    // 已經算好的 route_segment 外鍵擋住刪除 (見上面「真的餐廳超過 2 個」那段的說明), 刪除
                    // 前先清掉這天的路段快取。
                    if (!hotelItems.isEmpty()) {
                        routeSegmentDAO.deleteByDay(day.getIDID());
                    }
                    for (ItineraryItem hotelItem : hotelItems) {
                        itineraryItemDAO.deleteById(hotelItem.getIIID());
                    }
                    continue;
                }

                String cityKey = cityKey(cityTokensByDay.get(day.getDayNumber()));
                List<Poi> hotelCandidates = dayCandidates.stream()
                        .filter(p -> "飯店".equals(p.getCategory())).collect(java.util.stream.Collectors.toList());

                // lastHotel 在這個迴圈裡會被重新指派 (見下面 else if / else 分支), 不是 effectively final,
                // 不能直接在下面的 lambda (anyMatch) 裡參照——另外綁一個這一輪迴圈專用的 final 區域變數。
                Poi lastHotelForLambda = lastHotel;
                if (hotelItems.isEmpty()) {
                    if (cityKey.equals(lastHotelCityKey) && lastHotelForLambda != null
                            && hotelCandidates.stream().anyMatch(h -> h.getPID() == lastHotelForLambda.getPID())) {
                        addItem(day.getIDID(), lastHotel.getPID(), "hotel", lastHotel.getName(), lastHotel.getSuggestedStayMin());
                    } else if (!hotelCandidates.isEmpty()) {
                        Poi pick = hotelCandidates.get(0);
                        addItem(day.getIDID(), pick.getPID(), "hotel", pick.getName(), pick.getSuggestedStayMin());
                        lastHotel = pick;
                        lastHotelCityKey = cityKey;
                    } else {
                        addPlaceholderItem(day.getIDID(), "hotel", "住宿");
                        lastHotel = null;
                        lastHotelCityKey = cityKey;
                    }
                } else {
                    if (hotelItems.size() > 1) {
                        // Patch 84: 同樣是 route_segment 外鍵擋刪除的地雷, 見上面兩處說明。
                        routeSegmentDAO.deleteByDay(day.getIDID());
                        for (int i = 1; i < hotelItems.size(); i++) {
                            itineraryItemDAO.deleteById(hotelItems.get(i).getIIID());
                        }
                    }
                    ItineraryItem kept = hotelItems.get(0);
                    lastHotel = kept.getPID() != null ? candidateByPid.get(kept.getPID()) : null;
                    lastHotelCityKey = cityKey;
                }
            }

            // 加完之後跑一次自動整理 (meal_time 模式): 依序標出早/中/晚餐 (並固定 08:00/12:00/18:00 為
            // 用餐時間錨點, 見 autoArrangeDay 說明)、住宿排到當天最後面, 排序更像正常行程。
            autoArrangeItinerary(itinerary.getITID(), "meal_time");

            // 最後再逐天裁掉排太晚的行程 (景點/餐食開始時間超過晚上 20:30 就直接刪掉), 確保「每天行程最多
            // 只到晚上 8:30」——一定要放在 autoArrangeItinerary() 之後, 因為要靠它排好的最終順序、以及
            // 剛剛固定下來的用餐時間錨點, 才能準確估算每個項目大概幾點開始。
            trimDaysExceedingCutoff(days);
        } catch (Exception e) {
            // AI 沒設定 API Key / 呼叫失敗 / 回應解析失敗都不應該讓「建立行程」整個失敗,
            // 保留已經建立好的空白行程骨架, 讓使用者可以照原本流程手動編排 ——
            // 但一定要留下 log, 不然「AI 排程失敗」永遠只會看到畫面上那句籠統的提示, 沒辦法從外面判斷
            // 真正原因是 API Key 沒設定、AI 回應被截斷、還是候選景點清單太大讓 AI 輸出格式跑掉。
            //
            // 使用者反映「AI解析沒出現行程」/「SQL有資料,可是行程沒有出現」——追查後發現: 上面這一整段
            // (從逐天篩候選開始, 到補餐食/補住宿、trimDaysExceedingCutoff 為止) 完全沒有分批提交的保護,
            // 每一次 addItem()/deleteById() 都是各自獨立 commit。如果例外是在處理到「第 2 天」才發生
            // (例如某天候選名單剛好觸發 AI 回應格式跑掉), 進到這裡的時候, 第 1 天其實已經真的加好項目、
            // 存進資料庫了, 但這裡原本什麼清理都沒做就直接把這個 (半殘的) itinerary 回傳出去——結果就是
            // 使用者打開行程一看, 有些天有內容、有些天卻完全空白, 誤以為是「畫面沒把資料庫裡的東西讀出來」,
            // 但其實資料庫裡那幾天真的就是空的。既然上面註解寫的設計本意是「失敗就退回成一個乾淨的空白
            // 行程」, 這裡補上真正的清理: 把這次呼叫已經加進任何一天的項目全部刪掉 (連同會擋刪除的拉車
            // 距離快取), 確保回傳出去的一定是「整個行程都是空的」, 不會再出現「有些天有、有些天沒有」
            // 這種一半一半、容易被誤判成顯示 bug 的詭異狀態。
            for (ItineraryDay day : days) {
                routeSegmentDAO.deleteByDay(day.getIDID());
                for (ItineraryItem leftover : itineraryItemDAO.findByDay(day.getIDID())) {
                    itineraryItemDAO.deleteById(leftover.getIIID());
                }
            }
            LOGGER.warn("AI 安排行程失敗, 已清空這次嘗試加入的項目、退回乾淨的空白行程 (ITID={}, country={}, region={}, daysCount={}, candidates={}): {}",
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

    // Patch 28: 早餐一律用不連結 POI 的「飯店內早餐」預留項目 (不查資料庫、不會出現在地圖上), 不要另外
    // 排一間真正的餐廳——多數旅客早餐就是直接吃飯店附的早餐, 不需要特地安排。固定插在這一天「所有既有
    // 項目最前面」(把既有項目 sort_order 全部後移一位), 這樣 autoArrangeDay 的「這天第 1 個出現的餐廳
    // = 早餐」判斷才會穩定套用到這個項目上, 不會被 AI 自己排的其他餐廳搶走順位。
    private void addBreakfastPlaceholder(int IDID) {
        List<ItineraryItem> existing = itineraryItemDAO.findByDay(IDID);
        for (ItineraryItem item : existing) {
            item.setSortOrder(item.getSortOrder() + 1);
            itineraryItemDAO.save(item);
        }
        ItineraryItem breakfast = new ItineraryItem(IDID, null, "meal", "飯店內早餐", 0);
        breakfast.setShowOnMap(false);
        breakfast.setTimeSlot("breakfast");
        itineraryItemDAO.save(breakfast);
    }

    // 把「行程說明」「地區/城市」欄位共用的「頓號/逗號(全形/半形)/斜線/直線/空白混合分隔」字串拆成乾淨的
    // token 清單, 跟 PoiDAOImpl.splitLocationTokens 是同一套規則 (那邊是 DAO 內部私有方法, 這裡建立行程
    // 時要用同一套規則拆 ItineraryDay.plannedCities, 所以在這裡另外寫一份, 避免把 DAO 內部方法改成 public
    // 只為了給 Service 呼叫)。
    // 依景點實際經緯度做「最近鄰」路徑排序: 從清單第一個 (AI 選的第一個景點, 尊重 AI 對「今天先去哪」的
    // 判斷) 開始, 每一步都選離目前位置最近的下一個景點, 直到全部排完。這是簡化版的 TSP 貪婪解法, 不保證
    // 是理論上最短的路徑, 但比完全沒有座標依據的純文字猜測順序好非常多, 足以避免「一下最南一下最北」這種
    // 明顯不順路的安排。沒有座標的景點 (極少數, 通常是還沒補地理編碼的自訂資料) 排不進最近鄰計算, 固定
    // 放在最後面, 不影響其他有座標景點的排序結果。
    private List<Poi> orderAttractionsByProximity(List<Poi> attractions) {
        List<Poi> withCoords = new ArrayList<>();
        List<Poi> withoutCoords = new ArrayList<>();
        for (Poi p : attractions) {
            if (p.getLatitude() != null && p.getLongitude() != null) withCoords.add(p);
            else withoutCoords.add(p);
        }
        List<Poi> ordered = new ArrayList<>();
        if (withCoords.size() <= 1) {
            ordered.addAll(withCoords);
        } else {
            List<Poi> remaining = new ArrayList<>(withCoords);
            Poi current = remaining.remove(0);
            ordered.add(current);
            while (!remaining.isEmpty()) {
                Poi nearest = null;
                double bestDist = Double.MAX_VALUE;
                for (Poi candidate : remaining) {
                    double dist = haversineDistanceKm(current, candidate);
                    if (dist < bestDist) {
                        bestDist = dist;
                        nearest = candidate;
                    }
                }
                ordered.add(nearest);
                remaining.remove(nearest);
                current = nearest;
            }
        }
        ordered.addAll(withoutCoords);
        return ordered;
    }

    // Haversine 公式算兩個景點之間的直線距離 (公里)——只用來「比較誰比較近」決定造訪順序, 不是精確拉車
    // 距離 (拉車距離另外由 RouteService/Google Distance Matrix API 算), 精度對這個用途來說已經足夠。
    private double haversineDistanceKm(Poi a, Poi b) {
        double lat1 = a.getLatitude().doubleValue();
        double lon1 = a.getLongitude().doubleValue();
        double lat2 = b.getLatitude().doubleValue();
        double lon2 = b.getLongitude().doubleValue();
        double earthRadiusKm = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * earthRadiusKm * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }

    private List<String> splitCityTokens(String value) {
        if (value == null || value.isBlank()) return List.of();
        return java.util.Arrays.stream(value.split("[、,，/|\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(java.util.stream.Collectors.toList());
    }

    // 依「這天指定的城市」token 清單, 從候選景點裡篩出 city 欄位包含任一個 token 的地點
    // (跟 PoiDAOImpl.findByAgencyAndCountry 的 city LIKE %token% 語意一致)。
    private List<Poi> filterByCityTokens(List<Poi> candidates, List<String> tokens) {
        if (tokens.isEmpty()) return List.of();
        return candidates.stream()
                .filter(p -> p.getCity() != null && tokens.stream().anyMatch(t -> p.getCity().contains(t)))
                .collect(java.util.stream.Collectors.toList());
    }

    // 把一天的城市 token 清單轉成一個穩定的字串 key (排序後接起來), 用來判斷「連續兩天指定的城市組合是否
    // 完全相同」——逐天餐廳輪替計數器、飯店延續判斷都靠這個 key 分組, 不分順序 (「威尼斯、米蘭」跟
    // 「米蘭、威尼斯」視為同一組)。
    private String cityKey(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) return "";
        List<String> sorted = new ArrayList<>(tokens);
        java.util.Collections.sort(sorted);
        return String.join("、", sorted);
    }

    /**
     * 見 createItineraryWithAiPlan() 呼叫端的說明: 把 cityTokens (使用者選地區時的先後順序) 依序分配給
     * autoDayNumbers (完全沒有自己指定城市、也不是班機/轉機日的那些天, 已經照第幾天由小到大排序)。
     * 回傳 dayNumber -> 分配到的城市關鍵字清單 (通常只有 1 個, 城市數多於天數時某幾天會有 2 個)。
     *
     * 規則:
     *   - 城市數 <= 天數: 依天數比例分段, 每個城市各自佔連續的幾天 (跟一般排行程「先在 A 城市玩幾天、
     *     再移動到 B 城市」的直覺一致)。天數不整除城市數時, 用標準的 floor 除法分段, 每個城市分到的
     *     天數最多只會相差 1 天, 不保證一定是前面或後面的城市多分到那 1 天。
     *   - 城市數 > 天數: 一定有幾天要塞 2 個城市才裝得下, 用「剩下幾個城市 / 剩下幾天」動態判斷, 只有
     *     真的裝不下才讓某一天塞 2 個, 使用者要求的「最多 2 個、盡量 1 個」都會滿足 (需要塞 2 個的天數
     *     一定是最少的 max(0, 城市數-天數) 天)。如果城市數超過天數的 2 倍 (裝不下, 理論上很少見, 例如
     *     10 天的行程一次選了 25 個城市), 裝不下的城市會被塞進最後一天湊在一起 (不會漏排, 但那一天會
     *     超過「盡量 1 個」的理想, 這是天數真的不夠時沒辦法避免的取捨)。
     *   - 城市數為 0 或天數為 0: 回傳空 map, 呼叫端會退回原本「不篩城市, 用整個國家/地區候選」的行為。
     */
    private Map<Integer, List<String>> distributeCitiesAcrossDays(List<String> cityTokens, List<Integer> autoDayNumbers) {
        Map<Integer, List<String>> result = new LinkedHashMap<>();
        int n = cityTokens.size();
        int d = autoDayNumbers.size();
        if (n == 0 || d == 0) return result;

        if (n <= d) {
            for (int i = 0; i < n; i++) {
                int startIdx = (int) ((long) d * i / n);
                int endIdx = (int) ((long) d * (i + 1) / n);
                for (int idx = startIdx; idx < endIdx && idx < d; idx++) {
                    result.put(autoDayNumbers.get(idx), List.of(cityTokens.get(i)));
                }
            }
        } else {
            int cityIdx = 0;
            int remainingCities = n;
            int remainingDays = d;
            for (int dayIdx = 0; dayIdx < d; dayIdx++) {
                boolean need2 = remainingCities > remainingDays;
                int take = Math.min(need2 ? 2 : 1, remainingCities);
                List<String> assigned = new ArrayList<>();
                for (int k = 0; k < take && cityIdx < n; k++) {
                    assigned.add(cityTokens.get(cityIdx));
                    cityIdx++;
                }
                result.put(autoDayNumbers.get(dayIdx), assigned);
                remainingCities -= take;
                remainingDays--;
            }
            // 保險: 理論上跑完上面迴圈 cityIdx 應該剛好等於 n, 極端邊界 (城市數遠超過天數的 2 倍) 才會有
            // 城市還沒分配到, 全部塞進最後一天 (寧可最後一天塞很多, 也不要漏排任何一個使用者選的城市)。
            if (cityIdx < n && !autoDayNumbers.isEmpty()) {
                int lastDay = autoDayNumbers.get(autoDayNumbers.size() - 1);
                List<String> lastAssigned = new ArrayList<>(result.getOrDefault(lastDay, new ArrayList<>()));
                while (cityIdx < n) {
                    lastAssigned.add(cityTokens.get(cityIdx));
                    cityIdx++;
                }
                result.put(lastDay, lastAssigned);
            }
        }
        return result;
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
                                  List<String> outFlightNo,
                                  List<String> outDepAirport, List<String> outDepTime,
                                  List<String> outArrAirport, List<String> outArrTime,
                                  List<String> outDepDay,
                                  List<String> retFlightNo,
                                  List<String> retDepAirport, List<String> retDepTime,
                                  List<String> retArrAirport, List<String> retArrTime,
                                  List<String> retDepDay) {
        List<ItineraryDay> days = itineraryDayDAO.findByItinerary(ITID); // 已依 day_number ASC 排序
        if (days.isEmpty()) return;

        attachFlightLegsAcrossDays(days, true, "去程班機", outFlightNo, outDepAirport, outDepTime, outArrAirport, outArrTime, outDepDay);

        // 回程跟去程分開兩批處理, 就算某一段回程跟某一段去程被分到同一天, 各自的 findByDay() 也是即時查詢,
        // 不會漏算對方剛剛插入的項目。
        attachFlightLegsAcrossDays(days, false, "回程班機", retFlightNo, retDepAirport, retDepTime, retArrAirport, retArrTime, retDepDay);
    }

    /**
     * 給「建立行程」/「AI 安排行程」的 controller 用: 在真的呼叫 attachFlightItems() 插入班機項目
     * 之前, 先算出哪些天屬於「班機/轉機日」——規則要跟 attachFlightLegsAcrossDays() 一模一樣 (見
     * resolveFlightLegDayNumbers 說明), 也跟前端 itinerary/new.html 的 computeDisabledFlightDays()
     * 完全一致: 去程/回程各自涵蓋到的天數集合, 只留「去程最後一天」「回程第一天」不算班機日 (那兩天
     * 使用者還是可以指定城市), 其餘班機/轉機涵蓋到的天數都算。
     *
     * createItineraryWithAiPlan() 用這個結果分辨「這天沒填城市」到底是真正的班機/轉機日 (維持原本
     * 完全跳過的行為), 還是使用者自己選擇不填的一般日期 (退回整個國家/地區的候選景點)——見該方法
     * 開頭的說明。
     */
    public Set<Integer> computeFlightDayNumbers(int totalDays,
                                                List<String> outFlightNo, List<String> outDepAirport, List<String> outDepTime,
                                                List<String> outArrAirport, List<String> outArrTime, List<String> outDepDay,
                                                List<String> retFlightNo, List<String> retDepAirport, List<String> retDepTime,
                                                List<String> retArrAirport, List<String> retArrTime, List<String> retDepDay) {
        Set<Integer> outboundDays = resolveFlightLegDayNumbers(totalDays, 1,
                outFlightNo, outDepAirport, outDepTime, outArrAirport, outArrTime, outDepDay);
        Set<Integer> returnDays = resolveFlightLegDayNumbers(totalDays, totalDays,
                retFlightNo, retDepAirport, retDepTime, retArrAirport, retArrTime, retDepDay);

        Set<Integer> flightDays = new HashSet<>();
        flightDays.addAll(outboundDays);
        flightDays.addAll(returnDays);
        if (!outboundDays.isEmpty()) flightDays.remove(java.util.Collections.max(outboundDays));
        if (!returnDays.isEmpty()) flightDays.remove(java.util.Collections.min(returnDays));
        return flightDays;
    }

    // 算出「這個方向 (去程/回程) 的班機/轉機涵蓋到哪些天」: 規則要跟 attachFlightLegsAcrossDays() 完全
    // 一致 (只要出發機場/出發時間/抵達機場/抵達時間/航班編號其中一個有填就算「這個航段有填」;「第幾天」
    // 沒填/不是數字/超出範圍就退回 defaultDayIndex), 這樣才能在真的插入班機項目之前, 就先知道哪幾天
    // 屬於班機/轉機日。
    private Set<Integer> resolveFlightLegDayNumbers(int totalDays, int defaultDayIndex,
                                                    List<String> flightNumbers,
                                                    List<String> depAirports, List<String> depTimes,
                                                    List<String> arrAirports, List<String> arrTimes,
                                                    List<String> dayIndexes) {
        Set<Integer> result = new HashSet<>();
        int legCount = Math.max(Math.max(listSize(depAirports), listSize(depTimes)),
                Math.max(listSize(arrAirports), listSize(arrTimes)));
        for (int i = 0; i < legCount; i++) {
            String fromAirport = listGet(depAirports, i);
            String depTime = listGet(depTimes, i);
            String toAirport = listGet(arrAirports, i);
            String arrTime = listGet(arrTimes, i);
            String flightNo = listGet(flightNumbers, i);
            if (isBlank(fromAirport) && isBlank(toAirport) && isBlank(depTime) && isBlank(arrTime) && isBlank(flightNo)) continue;
            int dayIndex = parseDayIndexOrDefault(listGet(dayIndexes, i), defaultDayIndex, totalDays);
            result.add(dayIndex);
        }
        return result;
    }

    // isOutbound=true (去程): 每個航段所在那一天, 這批同一天的航段固定插在「那一天」的最前面 (依填寫順序、維持先後關係,
    // 其餘項目全部往後推); false (回程): 依填寫順序 append 在「那一天」的最後面。
    // 四個內容 List 用同一個 index 對齊組成一個航段, 缺的欄位留空; dayIndexes 是每個航段對應的天數 (見上方欄位說明)。
    // flightNumbers: 航班名稱/編號 (例如 CI100), 選填, 跟其他 List 用同一個 index 對齊組成一個航段。
    private void attachFlightLegsAcrossDays(List<ItineraryDay> days, boolean isOutbound, String label,
                                            List<String> flightNumbers,
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
            String flightNo = listGet(flightNumbers, i);
            if (isBlank(fromAirport) && isBlank(toAirport) && depTime == null && arrTime == null && isBlank(flightNo)) continue; // 這個航段整列都沒填, 跳過

            int dayIndex = parseDayIndexOrDefault(listGet(dayIndexes, i), defaultDayIndex, days.size());
            ItineraryDay targetDay = days.get(dayIndex - 1);

            // 顯示名稱只是「編號 出發地→目的地」，不再加方向前綴文字——方向改存到下面的 flightDirection
            // 欄位。只有起始點/目的地/編號全部沒填這個理論上不會發生的邊界情況，才會用到 fallbackLabel
            // (同一批多段時加編號方便萬一真的發生時看得出先後順序)。
            String fallbackLabel = legCount > 1 ? (label + (i + 1)) : label;
            String customName = buildTransportName(flightNo, fromAirport, toAirport, fallbackLabel);
            ItineraryItem item = new ItineraryItem(targetDay.getIDID(), null, "transport", customName, 0); // sort_order 最後統一算
            item.setFromLocation(isBlank(fromAirport) ? null : fromAirport.trim());
            item.setToLocation(isBlank(toAirport) ? null : toAirport.trim());
            item.setTransportMethod("飛機");
            item.setTransportNumber(isBlank(flightNo) ? null : flightNo.trim());
            item.setFlightDirection(isOutbound ? "outbound" : "return");
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
                // Patch 73: 使用者反映「AI排行程還是會將飛機時間無視」——追查後發現這裡是真正的根因之一:
                // 原本不管三七二十一, 一律把去程班機塞在這一天「最前面」, 把這天所有既有項目 (包含
                // autoArrangeItinerary() 早就排好、寫死 08:00 開始時間的「飯店內早餐」項目) 全部往後推。
                // 但早餐這種「出發前, 人根本還在家裡/國內, 跟班機/目的地完全無關」的項目, 排序上其實應該
                // 留在班機『前面』才符合真正的時間先後——被無條件推到班機後面之後, 會造成兩個連鎖問題:
                //   (1) board.html 的時間軸是照 sort_order 逐項畫出來的, 早餐自己寫死的 08:00 顯示時間
                //       卻出現在班機 (例如 12:00 起飛) 後面, 畫面上時間看起來「倒退」;
                //   (2) trimItemsAroundFlights() 判斷「這個項目排在班機前面/後面」完全是看 sort_order
                //       位置, 不是看實際時間——早餐一旦被推到班機後面, 就會被誤判成「已經下飛機」,
                //       連帶讓它後面接的景點 (完全沒有寫死時間, 只能靠往前累加估算) 從早餐的結束時間
                //       (09:00 左右) 開始估算, 而不是從班機真正抵達的時間開始估算——這正是使用者截圖裡
                //       「班機 15:00 才降落, 但清水寺卻排在 09:00」的成因。
                // 修正: 只把「這批航段裡最早出發時間之前, 已經有寫死開始時間、且沒有晚於這個出發時間」的
                // 既有餐食 (目前只有早餐符合) 留在班機前面, 其餘 (景點/自費/沒有時間資訊的項目, 本來就
                // 該算在班機之後) 才照舊往後推、插進班機後面。找不到任何一段航班有填出發時間時 (使用者
                // 沒填), 沒有依據可以判斷, 維持原本「全部塞最前面」的行為, 不冒然重排。
                java.time.LocalTime earliestDeparture = legItems.stream()
                        .map(ItineraryItem::getStartTime)
                        .filter(java.util.Objects::nonNull)
                        .min(java.time.LocalTime::compareTo)
                        .orElse(null);
                int insertOffset = 0;
                if (earliestDeparture != null) {
                    while (insertOffset < existing.size()) {
                        ItineraryItem candidate = existing.get(insertOffset);
                        if ("meal".equals(candidate.getItemType()) && candidate.getStartTime() != null
                                && !candidate.getStartTime().isAfter(earliestDeparture)) {
                            insertOffset++;
                        } else {
                            break;
                        }
                    }
                }
                for (int i = insertOffset; i < existing.size(); i++) {
                    ItineraryItem it = existing.get(i);
                    it.setSortOrder(it.getSortOrder() + legItems.size());
                    itineraryItemDAO.save(it);
                }
                for (int i = 0; i < legItems.size(); i++) {
                    legItems.get(i).setSortOrder(insertOffset + i);
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

    /**
     * 使用者這次要求: 有填去程/回程班機時, 要能算出「抵達機場 → 當天第一個景點」(去程) 跟
     * 「前一個景點 → 出發機場」(回程) 的拉車距離/時間。回程如果剛好是那一天的第一筆項目 (那天沒有排
     * 任何景點就直接去機場), 就改成算「前一天最後一項住宿 → 出發機場」——這個規則直接沿用既有的
     * findCarryOverHotel()/recalculateRoutes() 機制 (原本就是設計來處理「這天第一項的『前一站』要接到
     * 前一天住宿」這種情境), 不需要另外重寫一次。
     *
     * 不管有沒有轉機/跨天, 只處理「整趟行程去程方向最後一段航班」跟「回程方向第一段航班」這兩筆——
     * 轉機中間的航段本身是機場對機場銜接, 不需要 (也沒有意義) 算拉車距離; 使用者原文的簡化版規則
     * 「無論有幾班飛機、有無跨天, 直接判斷去程最後一筆/回程第一筆」正好就是這裡的做法。
     *
     * 呼叫時機很重要: 一定要在 attachFlightItems()、hideMealsOverlappingFlights() 都執行完之後才呼叫——
     * 前者要等所有航段都插入完成才能正確判斷「最後一段/第一段」是哪一筆, 後者可能刪掉跟班機時間重疊的
     * 餐食, 會影響到「當天第一個景點」/「前一個景點」實際上是哪一筆項目。
     */
    public void calculateAirportTransferSegments(int ITID) {
        List<ItineraryDay> days = itineraryDayDAO.findByItinerary(ITID); // 已依 day_number ASC 排序
        if (days.isEmpty()) return;

        String tripCountry = null;
        Itinerary itinerary = itineraryDAO.findById(ITID);
        if (itinerary != null) tripCountry = firstToken(itinerary.getCountry());

        // 依「第幾天 → 這天內的 sort_order」順序整趟掃過去, 找出「去程班機最後一筆」「回程班機第一筆」
        // (attachFlightLegsAcrossDays()/AiParseService.confirmImport() 都會設定 flightDirection 欄位,
        // 不再靠 customName 字首文字判斷方向——顯示名稱不管使用者怎麼編輯都不影響這裡的判斷)。
        ItineraryItem lastOutboundLeg = null;
        ItineraryItem firstReturnLeg = null;
        for (ItineraryDay day : days) {
            for (ItineraryItem item : itineraryItemDAO.findByDay(day.getIDID())) {
                if (!"transport".equals(item.getItemType()) || !"飛機".equals(item.getTransportMethod())) continue;
                if ("outbound".equals(item.getFlightDirection())) {
                    lastOutboundLeg = item; // 一路覆蓋下去, 掃到最後留下來的就是整趟行程最後一筆去程航段
                } else if ("return".equals(item.getFlightDirection()) && firstReturnLeg == null) {
                    firstReturnLeg = item; // 只在第一次遇到時記錄, 之後不再覆蓋, 就是整趟行程第一筆回程航段
                }
            }
        }

        Set<Integer> daysToRecalculate = new HashSet<>();

        // 這個方法現在也會在每次打開看板頁時呼叫一次 (見 ItineraryController.board()), 才能讓這個 patch
        // 上線之前就已經建立好的舊行程也自動補上這個功能, 不用重新建立行程。加這個判斷是為了避免每次開
        // 看板都重打一次 Google API (geocode + recalculateRoutes 都要打 Google 的服務)——已經算過、有座標
        // 又已經是 showOnMap=true 的話, 直接跳過, 之後這個項目本身有異動時 (拖曳排序/編輯) 本來就會走既有
        // 的 recalculateRoutes(), 不需要每次進頁面都重算。
        if (lastOutboundLeg != null && (lastOutboundLeg.getLatitude() == null || !Boolean.TRUE.equals(lastOutboundLeg.getShowOnMap()))
                && !isBlank(lastOutboundLeg.getToLocation())) {
            // 去程: 交通項目的座標本來就是拿來代表「目的地」(既有慣例, 見 updateItemDetails 對交通項目
            // toLocation 的處理), 抵達機場剛好就是這個航段的目的地, 直接沿用同一套慣例存座標即可,
            // 讓後面的 recalculateRoutes() 能正常算出「機場 → 下一個項目」的距離。
            GoogleMapsClient.GeocodeResult geo = geocodeAirport(lastOutboundLeg.getToLocation(), tripCountry);
            if (geo != null) {
                lastOutboundLeg.setLatitude(BigDecimal.valueOf(geo.latitude));
                lastOutboundLeg.setLongitude(BigDecimal.valueOf(geo.longitude));
                lastOutboundLeg.setShowOnMap(true); // 使用者要求要在地圖上看到「機場→第一個景點」這段路線
                itineraryItemDAO.save(lastOutboundLeg);
                daysToRecalculate.add(lastOutboundLeg.getIDID());
            }
        }

        if (firstReturnLeg != null && (firstReturnLeg.getLatitude() == null || !Boolean.TRUE.equals(firstReturnLeg.getShowOnMap()))
                && !isBlank(firstReturnLeg.getFromLocation())) {
            // 回程: 這裡刻意跟一般交通項目的慣例 (座標=目的地) 不同, 改存「出發機場」(fromLocation) 的
            // 座標——因為這一段真正要算的是「前一個景點/前一天住宿 → 出發機場」的距離, 不是飛機降落後的
            // 目的地 (回程的目的地通常是完全不同國家/城市的返程機場, 拿來算距離沒有意義)。這個項目本來
            // 就是 showOnMap=false, 不會顯示在地圖上, 所以座標語意跟其他交通項目不一致不會造成地圖顯示錯誤。
            GoogleMapsClient.GeocodeResult geo = geocodeAirport(firstReturnLeg.getFromLocation(), tripCountry);
            if (geo != null) {
                firstReturnLeg.setLatitude(BigDecimal.valueOf(geo.latitude));
                firstReturnLeg.setLongitude(BigDecimal.valueOf(geo.longitude));
                firstReturnLeg.setShowOnMap(true); // 使用者要求要在地圖上看到「最後一個景點→機場」這段路線
                itineraryItemDAO.save(firstReturnLeg);
                daysToRecalculate.add(firstReturnLeg.getIDID());
            }
        }

        // 兩筆有可能剛好落在同一天 (例如只有一天的行程), 用 Set 去重, 每天最多重算一次;
        // recalculateRoutes() 本身就會處理「前一天住宿當起點」的銜接 (findCarryOverHotel()),
        // 回程班機剛好是那一天第一筆項目時, 這裡不用另外特判, 既有邏輯會自動生效。
        for (int idid : daysToRecalculate) {
            recalculateRoutes(idid);
        }
    }

    // 查詢機場座標: 機場名稱是自由文字 (使用者在「行程重點資訊」打字/自動完成填的, 例如「東京成田機場」),
    // 沿用既有 findPlace() 優先、查不到再退回 geocode() 的做法, 跟 updateItemDetails 處理交通項目
    // 目的地欄位時是同一套邏輯, 保持查詢行為一致。
    private GoogleMapsClient.GeocodeResult geocodeAirport(String airportName, String countryHint) {
        String query = isBlank(countryHint) ? airportName : (airportName + " " + countryHint);
        GoogleMapsClient.GeocodeResult geo = googleMapsClient.findPlace(query, countryHint);
        if (geo == null) geo = googleMapsClient.geocode(query, countryHint);
        return geo;
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

    // 組出項目清單上顯示的名稱：直接是「航班/車次編號 出發地→目的地」(例如「JX362 高雄小港機場 →
    // 沖繩那霸國際空港」)，不再加「去程班機：」/「回程班機：」/「交通：」這種前綴文字。
    //
    // 使用者反映這種前綴文字很累贅（起始點/目的地/航班編號都已經是各自獨立的欄位了，看板卡片上還要
    // 多讀一段「去程班機：」才是真正的內容）。以前之所以硬把方向塞進顯示名稱裡，是因為
    // calculateAirportTransferSegments()（機場↔景點拉車距離）、updateItemDetails() 對回程班機座標的
    // 特殊處理、TemplateMergeService 的「參考航班」摘要，全部都是用 customName.startsWith("去程班機"/
    // "回程班機") 來判斷方向——沒有其他地方存這個資訊。現在改成 ItineraryItem.flightDirection
    // ("outbound"/"return"/null) 這個結構化欄位單獨記錄方向，跟顯示名稱脫鉤，上面幾個地方都已經改讀
    // 這個欄位，顯示名稱只單純負責「好讀」，使用者要怎麼編輯都不會影響方向判斷。
    // fallbackLabel: 只有在起始點/目的地/編號全部沒填 (理論上不太會發生, attachFlightLegsAcrossDays()/
    // addTransportItem() 呼叫端都已經先過濾掉全空的航段) 才會用到, 避免顯示名稱整個是空字串。
    private String buildTransportName(String flightNo, String fromAirport, String toAirport, String fallbackLabel) {
        boolean hasFrom = !isBlank(fromAirport);
        boolean hasTo = !isBlank(toAirport);
        String route;
        if (hasFrom && hasTo) route = fromAirport.trim() + " → " + toAirport.trim();
        else if (hasFrom) route = fromAirport.trim() + " 出發";
        else if (hasTo) route = "抵達 " + toAirport.trim();
        else route = null;

        String routeWithFlightNo;
        if (!isBlank(flightNo)) {
            routeWithFlightNo = route != null ? (flightNo.trim() + " " + route) : flightNo.trim();
        } else {
            routeWithFlightNo = route;
        }

        return routeWithFlightNo != null ? routeWithFlightNo : fallbackLabel;
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

    // Patch 27: 把「逐天候選清單」丟給 AI, 請它針對每一天分別從「那一天自己的候選清單」裡挑選 PID,
    // 回傳 {天數 -> [PID,...]}。跟 Patch 26 之前的版本最大的差異: 不再是一份全國家共用的候選清單 +
    // 一段自由文字「行程說明」當參考, 而是每一天各自帶著自己的候選清單 (已經依這天指定的城市篩過),
    // AI 只需要決定「這一天的候選裡面, 要挑哪幾個、排幾個」, 不用自己判斷地理/路線先後順序——順序已經由
    // 使用者在「建立新行程」頁面逐天指定城市 (含同天多城市時的點選先後順序) 決定好了。
    // 這個結構化的輸入方式從根本上避免了 Patch 26 之前那種「AI 被自由文字裡候選清單沒有的地名搞混、
    // 在 JSON 前後夾帶解說文字甚至拒答」的問題, 也讓沒有指定城市的天 (candidates 是空陣列) 保證拿到
    // pids=[] 的結果, 不會被排入任何行程。
    private Map<Integer, List<Integer>> planDaysWithAiPerDay(String country, List<ItineraryDay> days,
                                                             Map<Integer, List<String>> cityTokensByDay, Map<Integer, List<Poi>> candidatesByDay) throws Exception {
        String system = """
            你是旅遊行程規劃助手, 負責幫旅行社從「已有的景點/餐廳/飯店資料庫」裡挑選並安排出一份多天的行程初稿。
            使用者已經先幫每一天指定好「這天要去哪個/哪些城市」, 並且已經依城市把候選景點/餐廳/飯店篩好、
            分別附在每一天底下 (每筆候選有 pid / name / category / stay_min)。你的任務是針對「每一天」,
            決定這天要排哪幾個地點、排幾個, 只能使用「這一天自己底下列出來的候選清單」裡的 pid, 絕對不可以
            把某一天的候選 pid 排進別天, 也不可以自己生出候選清單沒有的地點或 pid。
            沒有列出候選清單的天 (candidates 是空陣列, 代表這天是交通/轉機日, 使用者沒有指定城市) 一律輸出
            空的 pids 陣列, 不要排任何東西進這天。

            規則 (只套用在「候選清單不是空的」的天):
            - category=景點 的排每天 2~4 個當作主要行程。
            - category=餐廳 的每天應該排剛好 2 個 (午餐、晚餐), 不要只排 1 個就結束那一天；只有候選餐廳
              數量真的太少 (這天候選清單裡少於 2 間不同的餐廳) 才可以少於 2 個。早餐固定另外用飯店內早餐
              處理, 不需要、也不要在這裡排早餐用的餐廳。
            - category=飯店 的每天恰好安排 1 個 (行程最後一天例外, 不需要安排飯店, 因為當天直接離開/返程)。
            - 同一個 pid 不要在同一天重複出現; 每個地點只放在最適合的一天就好, 不要漏掉候選清單裡看起來
              明顯必去的知名景點。
            - 如果同一天指定了不只一個城市, 這天的 candidates 裡已經涵蓋這幾個城市的地點; 請依使用者指定
              城市的先後順序安排造訪順序 (先安排先指定的城市, 再安排後指定的城市)。
            - 只能輸出一個 JSON 物件, 不要有任何其他文字 (不要加開頭問候語、不要加結尾說明、不要用 markdown
              code fence 包起來), 格式如下:
              {"days":[{"day":1,"pids":[12,7,45]},{"day":2,"pids":[]}]}
              day 是第幾天 (從 1 開始), pids 是這一天依造訪順序排列的候選 pid 陣列, 沒有候選清單的天輸出
              空陣列。務必針對「每一天」都輸出一筆, 不要漏掉任何一天。
            """;

        StringBuilder userContent = new StringBuilder();
        userContent.append("國家: ").append(country != null ? country : "未指定");
        userContent.append("\n總天數: ").append(days.size());
        userContent.append("\n逐天城市與候選清單:\n");
        for (ItineraryDay day : days) {
            int dayNumber = day.getDayNumber();
            List<String> cities = cityTokensByDay.get(dayNumber);
            List<Poi> dayCandidates = candidatesByDay.get(dayNumber);
            userContent.append("Day ").append(dayNumber).append(": ");
            // 使用者反映「沒填逐天城市指定, AI 排不出東西」修正: 這裡改成看這天實際有沒有候選清單
            // (dayCandidates), 不是單看有沒有填城市 (cities) ——一般日期沒填城市時, candidatesByDay
            // 已經退回整個國家/地區的候選景點 (見 createItineraryWithAiPlan 說明), 這裡也要照樣把
            // 候選清單列給 AI, 只有真正的班機/轉機日 (candidates 真的是空的) 才輸出「未指定城市」。
            if (dayCandidates == null || dayCandidates.isEmpty()) {
                userContent.append("(未指定城市, 交通/轉機日, candidates=[])\n");
                continue;
            }
            String cityLabel = (cities != null && !cities.isEmpty())
                    ? String.join("、", cities)
                    : "未指定 (可從整個目的地國家/地區的候選中挑選)";
            userContent.append("城市=").append(cityLabel).append(", candidates=[");
            for (int i = 0; i < dayCandidates.size(); i++) {
                Poi poi = dayCandidates.get(i);
                if (i > 0) userContent.append(",");
                userContent.append("{\"pid\":").append(poi.getPID())
                        .append(",\"name\":\"").append(poi.getName() != null ? poi.getName().replace("\"", "") : "")
                        .append("\",\"category\":\"").append(poi.getCategory() != null ? poi.getCategory() : "景點")
                        .append("\",\"stay_min\":").append(poi.getSuggestedStayMin() != null ? poi.getSuggestedStayMin() : 60)
                        .append("}");
            }
            userContent.append("]\n");
        }

        // maxTokens 從 4000 提高到 8000: 逐天分別列出候選清單雖然讓每一天各自的候選數量比以前的全國家
        // 共用清單小很多, 但天數多 (例如 10 天) 時整份 prompt/輸出的總量還是可能逼近舊上限,
        // 一旦被 max_tokens 截斷, 回應會變成不完整的 JSON, 解析直接失敗、整趟 AI 排程等於白跑。
        String response = anthropicClient.complete(system, userContent.toString(), 8000);
        String cleaned = stripCodeFence(response);
        JsonNode root;
        try {
            // 保險起見先擷取「第一個 { 到最後一個 }」這段再解析, 不要求 AI 的回應必須整段從頭到尾都是乾淨 JSON
            // (即使系統提示詞已經明講「只能輸出一個 JSON 物件, 不要有任何其他文字」, AI 偶爾還是會夾帶解說文字)。
            root = objectMapper.readTree(extractJsonObject(cleaned));
        } catch (Exception ex) {
            // 特地把 AI 原始回應的前 2000 字一起包進例外訊息裡: 呼叫端 createItineraryWithAiPlan() 的
            // catch 區塊只會記錄 e.toString(), 如果沒有這裡先把原始內容塞進例外訊息, log 只會看到
            // 「JSON 解析失敗」這種籠統訊息, 完全看不出 AI 實際上回了什麼、是被截斷還是夾雜了其他文字。
            throw new Exception("AI 回應無法解析成 JSON (country=" + country
                    + "), 原始回應前 2000 字: " + truncate(cleaned, 2000), ex);
        }

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

    // 把 AI 回應裡「第一個 { 到最後一個 }」這段擷取出來 (含頭尾), 濾掉系統提示詞已經明講不該出現、但 AI
    // 偶爾還是會夾帶的解說文字/前後綴。找不到成對的 { } 就原樣退回 (交給 objectMapper.readTree 自己報錯,
    // 錯誤訊息會被上層包進例外裡, 不會憑空消失)。
    private String extractJsonObject(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end < 0 || end < start) return text;
        return text.substring(start, end + 1);
    }

    // 截斷過長的字串放進例外訊息/log 裡用, 避免一次把整段很長的 AI 回應塞進 log 檔案
    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...(截斷)";
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
    /**
     * 上鎖。使用者要求「只有建立這個行程的使用者（建立者）可以鎖定跟解鎖」——是否為建立者由呼叫端
     * (ItineraryController) 檢查, 這裡只負責實際寫入鎖定狀態; 已經是鎖定狀態的話直接視為成功
     * (保護重複點擊, 不需要重新蓋一次 lockedAt)。
     */
    public boolean lockItinerary(int ITID, int UID) {
        Itinerary itinerary = itineraryDAO.findById(ITID);
        if (itinerary == null) return false;
        if (itinerary.isLocked()) return true; // 已經鎖定, 視為成功 (idempotent)
        itinerary.setLocked(true);
        itinerary.setLockedBy(UID);
        itinerary.setLockedAt(java.time.LocalDateTime.now());
        itineraryDAO.save(itinerary);
        return true;
    }

    /** 解鎖。是否為建立者由呼叫端 (ItineraryController) 檢查, 這裡只負責實際清除鎖定狀態。 */
    public void unlockItinerary(int ITID) {
        Itinerary itinerary = itineraryDAO.findById(ITID);
        if (itinerary == null) return;
        itinerary.setLocked(false);
        itinerary.setLockedBy(null);
        itinerary.setLockedAt(null);
        itineraryDAO.save(itinerary);
    }

    /**
     * 檢查這個行程目前是否可以被編輯。
     *
     * 使用者要求「鎖定完不能更改」——上鎖後不管是誰 (含建立者/上鎖的人自己在內) 都不能再編輯行程內容,
     * 要先解鎖才能繼續編輯。這跟舊版邏輯不一樣: 舊版是「協作防呆」性質 (上鎖只是為了避免兩人同時改
     * 同一個行程互相覆蓋, 上鎖的人自己還是可以繼續編輯), 新版是「明確定案、鎖住不給改」性質, 解鎖
     * 這個動作本身不受這裡影響 (解鎖不算「編輯行程內容」, 由 ItineraryController 的 /unlock 端點
     * 另外用「是不是建立者」單獨判斷, 不會被這裡卡住變成怎麼樣都解不了鎖)。
     */
    public boolean isEditableBy(int ITID) {
        Itinerary itinerary = itineraryDAO.findById(ITID);
        if (itinerary == null) return false;
        return !itinerary.isLocked();
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

    /**
     * 個別行程項目「切換某張圖片要不要匯出企劃書」(同一個景點/餐廳可能綁定多張照片)。
     * 使用者要求圖片預設全部輸出，所以這裡存的是「使用者排除掉的圖片」而不是「選了哪幾張」——
     * 沒被排除 (excludedImageIds 沒有這個 IAID) 就會匯出，第一次點某張圖片是把它加進排除清單
     * (從「輸出」變成「不輸出」)，再點一次則是從排除清單移除 (變回「輸出」)。
     */
    public void toggleItemImageExport(int IIID, int IAID) {
        ItineraryItem item = itineraryItemDAO.findById(IIID);
        if (item == null) return;
        revertToDraftIfCompletedByDay(item.getIDID()); // 見上方「標記已完成後再編輯要退回草稿」的說明

        java.util.LinkedHashSet<Integer> excluded = new java.util.LinkedHashSet<>(item.getExcludedImageIdSet());
        if (!excluded.remove(IAID)) {
            excluded.add(IAID);
        }
        item.setExcludedImageIds(excluded.isEmpty() ? null
                : excluded.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")));
        itineraryItemDAO.save(item);
    }

    /**
     * 沒有連結公司景點資料庫的項目 (PID 為 null), 儲存/更新自己暫存的介紹說明 (ai_description)。
     * 這種項目沒有共用的 POI 紀錄可以寫回, 直接存回這個行程項目自己身上就好——如果使用者事後把這個
     * 項目「加入景點資料庫」連結上 POI, 之後就會改走 PoiService.updateDescription() 那套存回共用
     * 資料庫的流程 (見 addItemToPoi() 也會優先拿這個欄位的內容當新 POI 的初始介紹說明)。
     */
    public void updateItemAiDescription(int IIID, String description) {
        ItineraryItem item = itineraryItemDAO.findById(IIID);
        if (item == null) throw new IllegalArgumentException("找不到這個項目");
        revertToDraftIfCompletedByDay(item.getIDID());
        item.setAiDescription(description == null || description.isBlank() ? null : description);
        itineraryItemDAO.save(item);
    }

    /**
     * 「編輯行程基本資料」頁面儲存: 只更新行程名稱/國家/地區/天數/出發日期這幾個基本欄位。
     * 使用者要求「行程不用重新安排」——每一天已經排好的景點/餐飲/住宿完全不動；已經加入看板的去程/回程
     * 班機項目也完全不動 (這裡不會呼叫 attachFlightItems，天然就「保留」了，不需要額外處理)。
     * 天數如果改多了，在最後面補上對應數量的空白天 (day_number 依序遞增、沒有任何內容，理由跟
     * addBlankDay() 一樣，需要使用者自己手動排)。
     * 天數如果改少了，會真的從最後面刪除多出來的天 (連同底下的項目一起刪掉)——前端在送出表單前已經跳出
     * 確認對話框告知使用者「第X天有幾個行程項目，確定要刪除嗎」，這裡收到的請求就是使用者已經確認過的,
     * 不再重複警告。天數不變則什麼都不動。
     */
    public void updateBasicInfo(int ITID, String title, String country, String region,
                                int daysCount, LocalDate startDate) {
        Itinerary itinerary = itineraryDAO.findById(ITID);
        if (itinerary == null) return;

        itinerary.setTitle(title);
        itinerary.setCountry(country);
        itinerary.setRegion(region);
        itinerary.setStartDate(startDate);

        List<ItineraryDay> days = itineraryDayDAO.findByItinerary(ITID);
        int currentMaxDay = 0;
        for (ItineraryDay day : days) {
            if (day.getDayNumber() > currentMaxDay) currentMaxDay = day.getDayNumber();
        }

        if (daysCount > currentMaxDay) {
            for (int d = currentMaxDay + 1; d <= daysCount; d++) {
                LocalDate dayDate = startDate != null ? startDate.plusDays(d - 1) : null;
                itineraryDayDAO.save(new ItineraryDay(ITID, d, dayDate, null));
            }
        } else if (daysCount < currentMaxDay) {
            // 使用者要求天數改少時要真的刪除多出來的天 (使用者已經在前端確認過); 從最後一天開始刪,
            // 重用跟看板「刪除這一天」按鈕一樣的 deleteDay() (含清 route_segment、CASCADE 刪除底下項目),
            // 因為是從尾端刪, deleteDay() 內建的「後面天數往前遞補」邏輯不會找到任何東西可以遞補, 不影響結果。
            for (ItineraryDay day : days) {
                if (day.getDayNumber() > daysCount) {
                    deleteDay(day.getIDID());
                }
            }
        }

        // deleteDay()/上面新增空白天都已經各自把 itinerary 存過一次, 這裡重新抓最新的 itinerary 物件
        // 再做最後的欄位設定, 避免拿到已經過期的物件蓋掉 deleteDay() 剛剛存的 daysCount/endDate。
        itinerary = itineraryDAO.findById(ITID);
        if (itinerary == null) return;
        itinerary.setTitle(title);
        itinerary.setCountry(country);
        itinerary.setRegion(region);
        itinerary.setStartDate(startDate);

        int finalDaysCount = daysCount; // 改少的情況下現在真的會刪除, 所以就是使用者填的數字了
        itinerary.setDaysCount(finalDaysCount);

        if (startDate != null) {
            itinerary.setEndDate(finalDaysCount > 0 ? startDate.plusDays(finalDaysCount - 1) : startDate);
        }

        // 見上方「標記已完成後再編輯要退回草稿」的說明——這裡已經拿到最新的 itinerary 物件, 順便一起改,
        // 不用再多查一次資料庫
        if ("completed".equals(itinerary.getStatus())) {
            itinerary.setStatus("draft");
        }

        itineraryDAO.save(itinerary);
    }

    /**
     * 看板 Day 分頁旁邊的「刪除這一天」: 真的刪除這一天以及底下所有項目 (itinerary_item 對 itinerary_day
     * 設了 ON DELETE CASCADE, 底下項目/選項會跟著自動清掉), 並把後面的天數依序往前遞補一位 (例如刪掉
     * Day2, 原本的 Day3/4/5 變成 Day2/3/4), 讓 Day 編號維持連續不留空隙。
     * 有 startDate 的話, 遞補後的每一天日期也會跟著重新算 (公式跟 createItinerary()/addBlankDay() 一致:
     * dayDate = startDate + (新的 dayNumber - 1) 天), 讓日期繼續對應「第幾天」；沒有 startDate 就維持 null。
     * 同步把 itinerary.daysCount -1、重算 endDate。
     */
    public void deleteDay(int IDID) {
        ItineraryDay dayToDelete = itineraryDayDAO.findById(IDID);
        if (dayToDelete == null) return;
        int ITID = dayToDelete.getITID();
        int deletedDayNumber = dayToDelete.getDayNumber();
        revertToDraftIfCompleted(ITID); // 見上方「標記已完成後再編輯要退回草稿」的說明 (要在刪除前查, 不然之後就找不到 ITID 了)

        // route_segment 對 itinerary_item 的外鍵沒有 ON DELETE CASCADE, 要先清掉這天算過的拉車距離快取,
        // 不然刪除這天 (連帶 CASCADE 刪除底下的 itinerary_item) 會在這一關被擋住, 跟 deleteItinerary() 一樣的道理。
        routeSegmentDAO.deleteByDay(IDID);
        itineraryDayDAO.deleteById(IDID); // CASCADE 帶走底下所有 itinerary_item / itinerary_item_option

        Itinerary itinerary = itineraryDAO.findById(ITID);
        List<ItineraryDay> remainingDays = itineraryDayDAO.findByItinerary(ITID);
        int maxDayNumber = 0;
        for (ItineraryDay day : remainingDays) {
            if (day.getDayNumber() > deletedDayNumber) {
                int newDayNumber = day.getDayNumber() - 1;
                day.setDayNumber(newDayNumber);
                if (itinerary != null && itinerary.getStartDate() != null) {
                    day.setDayDate(itinerary.getStartDate().plusDays(newDayNumber - 1));
                }
                itineraryDayDAO.save(day);
                if (newDayNumber > maxDayNumber) maxDayNumber = newDayNumber;
            } else if (day.getDayNumber() > maxDayNumber) {
                maxDayNumber = day.getDayNumber();
            }
        }

        if (itinerary != null) {
            itinerary.setDaysCount(maxDayNumber);
            if (itinerary.getStartDate() != null) {
                itinerary.setEndDate(maxDayNumber > 0
                        ? itinerary.getStartDate().plusDays(maxDayNumber - 1)
                        : itinerary.getStartDate());
            }
            itineraryDAO.save(itinerary);
        }
    }

    /**
     * 看板「天數旁邊 +」直接加一天空白天：加在最後面 (day_number = 目前最大值 + 1)，不影響既有天數的
     * 內容/排序，也不會觸發任何自動排程/AI 邏輯——新加的這一天完全空白，需要使用者自己手動排內容。
     * 有 startDate 的話依序往後推算這天的 dayDate、同步更新 itinerary.endDate；沒有 startDate 就留 null，
     * 邏輯跟 createItinerary() 建立骨架時一致。同步把 itinerary.daysCount 往上調整，讓「編輯行程基本資料」
     * 頁面看到的天數跟看板實際天數保持一致。
     */
    public ItineraryDay addBlankDay(int ITID) {
        Itinerary itinerary = itineraryDAO.findById(ITID);
        if (itinerary == null) return null;

        List<ItineraryDay> days = itineraryDayDAO.findByItinerary(ITID);
        int maxDayNumber = 0;
        for (ItineraryDay day : days) {
            if (day.getDayNumber() > maxDayNumber) maxDayNumber = day.getDayNumber();
        }
        int newDayNumber = maxDayNumber + 1;

        LocalDate dayDate = null;
        if (itinerary.getStartDate() != null) {
            dayDate = itinerary.getStartDate().plusDays(newDayNumber - 1);
        }

        ItineraryDay newDay = new ItineraryDay(ITID, newDayNumber, dayDate, null);
        itineraryDayDAO.save(newDay);

        if (newDayNumber > itinerary.getDaysCount()) {
            itinerary.setDaysCount(newDayNumber);
        }
        if (itinerary.getStartDate() != null) {
            itinerary.setEndDate(itinerary.getStartDate().plusDays(newDayNumber - 1));
        }
        // 見上方「標記已完成後再編輯要退回草稿」的說明——已經拿到 itinerary 物件, 順便一起改
        if ("completed".equals(itinerary.getStatus())) {
            itinerary.setStatus("draft");
        }
        itineraryDAO.save(itinerary);

        return newDay;
    }

    public void deleteItinerary(int ITID) {
        aiImportDAO.clearResultItinerary(ITID); // 先解除外鍵參照, 不然刪除會被擋
        // 同樣道理: route_segment 對 itinerary_item 的外鍵沒設 CASCADE,
        // 要先把這個行程底下每一天算過的拉車距離快取清掉, 不然整串 CASCADE 刪除會在 itinerary_item 這關被擋住
        for (ItineraryDay day : itineraryDayDAO.findByItinerary(ITID)) {
            routeSegmentDAO.deleteByDay(day.getIDID());
        }
        // 使用者要求: 這個行程如果已經有報價單 (含「簡易報價單/快速抓價錢」填過的價格), 刪除行程時
        // 要一併刪掉, 不能留下孤兒的報價資料。quotation/quotation_line 資料庫層級雖然已經設了
        // ON DELETE CASCADE (db/migration_quotation.sql), 但這個專案過去發生過「migration 沒有真的
        // 在使用者實際的資料庫上執行過」的情況 (見交付紀錄), 所以這裡不依賴資料庫層級的 cascade 一定有效,
        // 直接在程式碼層級主動刪除每一版報價單的明細跟主檔, 確保不管資料庫有沒有套用 cascade 都會正確清掉。
        for (Quotation quotation : quotationDAO.findByItinerary(ITID)) {
            quotationLineDAO.deleteByQuotation(quotation.getQID());
            quotationDAO.delete(quotation);
        }
        itineraryDAO.deleteById(ITID);
    }

    // 首頁「釘選」行程 (像 LINE 聊天列表往右滑釘選)：切換 pinned 狀態，回傳切換後的新狀態。
    // pinnedAt 跟 pinned 一定同步更新: 釘選時記錄現在時間 (讓多筆釘選的行程之間有排序依據),
    // 取消釘選時清成 null，DAO 那邊 findByAgency() 會依 pinned DESC, pinnedAt DESC 排序。
    public boolean togglePin(int ITID) {
        Itinerary itinerary = itineraryDAO.findById(ITID);
        if (itinerary == null) return false;
        boolean newState = !itinerary.isPinned();
        itineraryDAO.updatePinnedState(ITID, newState, newState ? java.time.LocalDateTime.now() : null);
        return newState;
    }

    // 首頁「複製行程」：把整個行程 (基本資料 + 每一天 + 每天的項目/候選點 + 拉車距離快取 + 掛的報價元件)
    // 複製成一份全新的草稿行程，方便「同一條路線、下一團客人只是日期/人數不同」這種情境不用重新排一次。
    //
    // 不複製的東西：is_locked/locked_by/locked_at (新行程一定是未鎖定狀態)、pinned/pinnedAt (新行程
    // 預設不釘選)、status 一律重設回 draft (即使原本已經 completed，複製出來的也是全新草稿要重新走流程)、
    // 報價單 (quotation) 本身不複製——每一團的報價通常會不一樣，複製報價反而容易讓使用者誤用到舊金額，
    // 需要報價的話請到新行程用「編輯報價」重新抓一次或手動輸入。
    public Itinerary duplicateItinerary(int ITID, int UID) {
        Itinerary original = itineraryDAO.findById(ITID);
        if (original == null) throw new IllegalArgumentException("找不到這個行程");

        Itinerary copy = new Itinerary();
        copy.setAID(original.getAID());
        copy.setCreatedBy(UID);
        copy.setTitle(original.getTitle() + "（複製）");
        copy.setCountry(original.getCountry());
        copy.setRegion(original.getRegion());
        copy.setDaysCount(original.getDaysCount());
        copy.setStartDate(original.getStartDate());
        copy.setEndDate(original.getEndDate());
        copy.setGroupSize(original.getGroupSize());
        copy.setStatus("draft");
        copy.setTemplateStyle(original.getTemplateStyle());
        copy.setArrangeMode(original.getArrangeMode());
        copy.setDescription(original.getDescription());
        itineraryDAO.save(copy);

        // 舊 IIID -> 新 IIID 的對應表, 複製 route_segment (存的是「項目之間」的拉車距離) 要靠這個
        // 把舊的 from_item_id/to_item_id 換算成新行程底下對應的項目 ID, 一定要等這一天的項目全部複製完才能用。
        Map<Integer, Integer> itemIdMap = new HashMap<>();

        for (ItineraryDay day : itineraryDayDAO.findByItinerary(ITID)) {
            ItineraryDay dayCopy = new ItineraryDay();
            dayCopy.setITID(copy.getITID());
            dayCopy.setDayNumber(day.getDayNumber());
            dayCopy.setDayDate(day.getDayDate());
            dayCopy.setTheme(day.getTheme());
            dayCopy.setPlannedCities(day.getPlannedCities());
            dayCopy.setStartTime(day.getStartTime());
            dayCopy.setTransportMode(day.getTransportMode());
            itineraryDayDAO.save(dayCopy);

            for (ItineraryItem item : itineraryItemDAO.findByDay(day.getIDID())) {
                ItineraryItem itemCopy = new ItineraryItem();
                itemCopy.setIDID(dayCopy.getIDID());
                itemCopy.setPID(item.getPID());
                itemCopy.setItemType(item.getItemType());
                itemCopy.setCustomName(item.getCustomName());
                itemCopy.setSortOrder(item.getSortOrder());
                itemCopy.setStartTime(item.getStartTime());
                itemCopy.setEndTime(item.getEndTime());
                itemCopy.setStayDurationMin(item.getStayDurationMin());
                itemCopy.setNote(item.getNote());
                itemCopy.setLatitude(item.getLatitude());
                itemCopy.setLongitude(item.getLongitude());
                itemCopy.setItemCountry(item.getItemCountry());
                itemCopy.setItemRegion(item.getItemRegion());
                itemCopy.setTimeSlot(item.getTimeSlot());
                itemCopy.setShowOnMap(item.getShowOnMap());
                itemCopy.setFromLocation(item.getFromLocation());
                itemCopy.setFromAddress(item.getFromAddress());
                itemCopy.setToLocation(item.getToLocation());
                itemCopy.setToAddress(item.getToAddress());
                itemCopy.setTransportMethod(item.getTransportMethod());
                itemCopy.setCommuteDurationMin(item.getCommuteDurationMin());
                itemCopy.setExcludedImageIds(item.getExcludedImageIds());
                itineraryItemDAO.save(itemCopy);
                itemIdMap.put(item.getIIID(), itemCopy.getIIID());

                for (ItineraryItemOption opt : itineraryItemOptionDAO.findByItem(item.getIIID())) {
                    ItineraryItemOption optCopy = new ItineraryItemOption();
                    optCopy.setIIID(itemCopy.getIIID());
                    optCopy.setName(opt.getName());
                    optCopy.setLatitude(opt.getLatitude());
                    optCopy.setLongitude(opt.getLongitude());
                    optCopy.setSelected(opt.isSelected());
                    itineraryItemOptionDAO.save(optCopy);
                }
            }

            // 這一天的拉車距離快取: 一定要等這一天所有項目都複製完 (itemIdMap 補齊對應) 才能轉換
            // from_item_id/to_item_id, 所以放在項目的 for 迴圈外面、還在同一天的 for 迴圈裡面。
            for (RouteSegment seg : routeSegmentDAO.findByDay(day.getIDID())) {
                Integer newFrom = itemIdMap.get(seg.getFromItemId());
                Integer newTo = itemIdMap.get(seg.getToItemId());
                if (newFrom == null || newTo == null) continue; // 理論上不會發生, 保險起見跳過避免存進錯誤的參照
                RouteSegment segCopy = new RouteSegment(dayCopy.getIDID(), newFrom, newTo,
                        seg.getDistanceKm(), seg.getDurationMin(), seg.isBacktrack());
                segCopy.setTransportMode(seg.getTransportMode());
                segCopy.setCalculatedAt(seg.getCalculatedAt());
                routeSegmentDAO.save(segCopy);
            }
        }

        // 元件庫掛在這個行程上的報價元件 (遊覽車/導遊/保險等固定資源) 一併複製過去, 數量/單價覆寫也一起帶
        for (ItineraryComponent ic : itineraryComponentDAO.findByItinerary(ITID)) {
            itineraryComponentDAO.save(new ItineraryComponent(copy.getITID(), ic.getCPID(), ic.getDayNumber(),
                    ic.getQuantity(), ic.getPriceOverride()));
        }

        return copy;
    }

    // 更新某一天的出發時間 (時間軸看板的起點, 例如「早上7點出發」)
    public void updateDayStartTime(int IDID, java.time.LocalTime startTime) {
        ItineraryDay day = itineraryDayDAO.findById(IDID);
        if (day != null) {
            revertToDraftIfCompleted(day.getITID()); // 見上方「標記已完成後再編輯要退回草稿」的說明
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
            revertToDraftIfCompleted(day.getITID()); // 見上方「標記已完成後再編輯要退回草稿」的說明
            String normalized = "walking".equalsIgnoreCase(transportMode) ? "walking"
                    : "auto".equalsIgnoreCase(transportMode) ? "auto" : "driving";
            day.setTransportMode(normalized);
            itineraryDayDAO.save(day);
            recalculateRoutes(IDID, false); // false = 不保留每段的手動覆寫, 全部依新設定重算
        }
    }

    // Patch 88: 使用者反映「行程時間不用限制最晚時間（自動安排行程再限制 但還是能自己加行程）」以及
    // 「有時候儲存會把行程刪除 或是行程內資料刪除 不知道為什麼」——追查後發現就是這裡: patch 79 把
    // trimItemsAroundFlights()/trimDaysExceedingCutoff() 這兩個「刪除不合理項目」的自我修復移到這個
    // 全站共用的最底層讀取方法, 原意是讓所有呼叫端 (看板 API、Word 匯出、報價單、企劃書合併) 都自動
    // 受惠, 但副作用是: 只要「讀取」這一天的項目 (不管是打開看板、切換分頁、還是單純重新整理畫面),
    // 就會無條件重新套用「回程班機出發前 90 分鐘」「每天最晚 20:30」這些限制, 把任何剛好落在這些
    // 範圍內的項目直接刪掉——不管這個項目是 AI 排的還是使用者剛手動加上去的。這正是「明明只是儲存/
    // 整理畫面, 行程卻自己被刪掉一部分」的根本原因: 使用者手動加的晚間項目、或者手動調整過時間的項目,
    // 只要之後任何一次讀取這一天 (包含使用者自己完全沒操作、只是切換到別天再切回來) 都可能被這裡當成
    // 「不合理」而清掉。
    //
    // 修正: 拿掉這裡的自我修復, 只單純讀取、不再做任何刪除。這兩個方法依然存在、依然有效, 只是呼叫的
    // 責任收回到真正代表「使用者主動要求自動安排」的地方——見 ItineraryController 的
    // createWithAiPlan()/createItineraryWithAiPlan() (建立行程時的 AI 初稿) 以及 /day/{IDID}/auto-arrange、
    // /{id}/auto-arrange (看板上「自動整理」「依早中晚餐時段」按鈕) 這幾個端點, 都已經各自明確呼叫這
    // 兩個方法 (見那幾個地方的說明), 不需要再靠這裡的讀取端兜底。使用者手動新增/編輯的項目, 只要不是
    // 透過這些「自動安排」的按鈕, 就不會再無緣無故被清掉。
    public List<ItineraryItem> getItems(int IDID) {
        return itineraryItemDAO.findByDay(IDID);
    }

    // 給看板顯示「兩點之間拉車距離/時間」與迴頭路警示用
    public List<com.example.travelereasygate.entity.RouteSegment> getRoutes(int IDID) {
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

    // 使用者要求: 看板上的「完成行程」按鈕在行程已經是 completed 狀態時要變成「退回草稿」, 讓使用者可以
    // 明確地按一下、自己主動決定要把這個已完成的行程改回草稿狀態才繼續編輯——是跟下面那組「編輯內容時
    // 自動退回草稿」的安全網互補的另一個入口 (使用者可能還沒編輯任何東西, 純粹想先把狀態改回草稿),
    // 對應 ItineraryController 的 POST /itinerary/{id}/revert-to-draft。
    public void revertToDraft(int ITID) {
        Itinerary itinerary = itineraryDAO.findById(ITID);
        if (itinerary == null) throw new IllegalArgumentException("找不到這個行程");
        itinerary.setStatus("draft");
        itineraryDAO.save(itinerary);
    }

    // Patch 89: 使用者要求「在行程排班看板旁邊的標題可以點兩下直接編輯」——原本只有「編輯行程基本資料」
    // 那個完整表單頁面可以改名稱, 這裡新增一個只改標題的輕量端點, 給看板頁面點兩下標題就地編輯用。
    public void updateTitle(int ITID, String title) {
        Itinerary itinerary = itineraryDAO.findById(ITID);
        if (itinerary == null) throw new IllegalArgumentException("找不到這個行程");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("行程名稱不能空白");
        itinerary.setTitle(title.trim());
        itineraryDAO.save(itinerary);
    }

    // ---------------------------------------------------------------------------------------------
    // 使用者反映:「標記已完成後，再編輯還是會顯示已完成，應該要變成返回草稿之類的」——原本 markCompleted()
    // 把狀態改成 completed 之後，看板上任何編輯動作 (改名稱/停留時間/時段、新增刪除項目、拖曳排序、
    // 切換交通方式、自動整理、編輯行程基本資料...) 完全不會去動 status 欄位，所以行程被改過內容之後，
    // 首頁/看板上顯示的狀態卻還停在「已完成」——跟使用者的認知不符：「已完成」應該代表「這份行程已經
    // 定案、不會再變動了」，一旦又跑回來改內容，就代表這份還沒真的定案，應該要自動退回「草稿」狀態，
    // 提醒自己 (或提醒團隊其他人) 這份行程其實又被動過，需要重新確認一輪、再按一次「完成行程」才會
    // 再變回「已完成」。
    //
    // 下面這組 private 方法統一處理這件事，只有目前真的是 completed 狀態才會存檔改回 draft (本來就是
    // draft/其他狀態就什麼都不做，避免每次編輯都多一次不必要的 UPDATE)。提供三種情境的入口，因為看板
    // 上大部分編輯動作收到的是 IDID (某一天) 或 IIID (單一項目)，不是直接拿到 ITID (整個行程)：
    //   revertToDraftIfCompleted(ITID)         - 已經有 ITID 的情境 (行程層級的動作)
    //   revertToDraftIfCompletedByDay(IDID)    - 只有 IDID 的情境, 會自己查 ItineraryDay 找出 ITID
    //   revertToDraftIfCompletedByItem(IIID)   - 只有 IIID 的情境, 會自己查 ItineraryItem 再轉呼叫上面那個
    // 這三個方法呼叫的時間點都要在真正刪除資料之前 (例如 deleteDay/removeItem)，不然刪掉之後就查不到
    // 對應的 ITID 了。
    private void revertToDraftIfCompleted(Integer ITID) {
        if (ITID == null) return;
        Itinerary itinerary = itineraryDAO.findById(ITID);
        if (itinerary != null && "completed".equals(itinerary.getStatus())) {
            itinerary.setStatus("draft");
            itineraryDAO.save(itinerary);
        }
    }

    private void revertToDraftIfCompletedByDay(Integer IDID) {
        if (IDID == null) return;
        ItineraryDay day = itineraryDayDAO.findById(IDID);
        if (day != null) revertToDraftIfCompleted(day.getITID());
    }

    private void revertToDraftIfCompletedByItem(Integer IIID) {
        if (IIID == null) return;
        ItineraryItem item = itineraryItemDAO.findById(IIID);
        if (item != null) revertToDraftIfCompletedByDay(item.getIDID());
    }
    // ---------------------------------------------------------------------------------------------

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
        revertToDraftIfCompletedByDay(item.getIDID()); // 見上方「標記已完成後再編輯要退回草稿」的說明

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
        // 自動生成景點介紹說明: 這個項目如果已經有 AI 解析原文帶來的簡介 (ai_description), 直接拿來當
        // 新 POI 的介紹說明, 不用再另外呼叫 AI 生成一份 (原文本來就有寫, 沒理由捨棄不用、憑空生成別的
        // 內容, 也省一次 API 呼叫)；沒有的話才維持原本「用備註當提示, 請 AI 生成」的做法。寫進 POI 之後
        // 這個暫存欄位就功成身退, 清空它, 之後這個項目的介紹說明一律以資料庫版本為準。
        boolean hasAiDescription = item.getAiDescription() != null && !item.getAiDescription().isBlank();
        java.util.concurrent.CompletableFuture<String> descriptionFuture = hasAiDescription
                ? java.util.concurrent.CompletableFuture.completedFuture(item.getAiDescription())
                : java.util.concurrent.CompletableFuture.supplyAsync(
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
        if (hasAiDescription) {
            item.setAiDescription(null); // 已經寫進 POI 資料庫了, 之後改以資料庫版本為準
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
     * 新增一筆交通類項目 (item_type=transport) 到某一天的行程尾端, 沿用跟 attachFlightLegsAcrossDays
     * 一樣的顯示名稱規則 (buildTransportName: 有填航班/車次編號就顯示「編號 出發地→目的地」)。目前給
     * AI 解析匯入 (AiParseService.confirmImport) 呼叫, 讓 AI 從原始文件裡解析出來的航班/交通資訊也能用
     * 跟手動輸入去程/回程班機一致的方式呈現。
     * @param flightDirection 這筆項目是不是整趟行程的去程/回程班機: "outbound" / "return" / null
     *                        (不是去程/回程班機, 例如行程中段的城市內接駁、非首末天的一般交通項目)。
     */
    public ItineraryItem addTransportItem(int IDID, String flightDirection, String transportMethod, String transportNumber,
                                          String fromLocation, String toLocation,
                                          java.time.LocalTime startTime, java.time.LocalTime endTime, String note) {
        List<ItineraryItem> existing = itineraryItemDAO.findByDay(IDID);
        int nextOrder = existing.size();

        String customName = buildTransportName(transportNumber, fromLocation, toLocation, "交通");
        ItineraryItem item = new ItineraryItem(IDID, null, "transport", customName, nextOrder);
        item.setFromLocation(isBlank(fromLocation) ? null : fromLocation.trim());
        item.setToLocation(isBlank(toLocation) ? null : toLocation.trim());
        item.setTransportMethod(isBlank(transportMethod) ? "交通" : transportMethod.trim());
        item.setTransportNumber(isBlank(transportNumber) ? null : transportNumber.trim());
        item.setFlightDirection(flightDirection);
        item.setStartTime(startTime);
        item.setEndTime(endTime);
        item.setNote(note);
        // 出發/抵達地點是自由文字 (沒有連結 POI/經緯度), 沒有座標可以畫在地圖上, 關掉顯示在地圖上避免出現錯誤定位點
        item.setShowOnMap(false);

        itineraryItemDAO.save(item);
        return item;
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
        revertToDraftIfCompletedByDay(IDID); // 見上方「標記已完成後再編輯要退回草稿」的說明
        List<ItineraryItem> existing = itineraryItemDAO.findByDay(IDID);
        int nextOrder = existing.size();

        ItineraryItem item = new ItineraryItem(IDID, PID, itemType, customName, nextOrder);
        item.setStayDurationMin(stayDurationMin);
        item.setItemCountry(itemCountry);
        item.setItemRegion(itemRegion);
        item.setTimeSlot(timeSlot);
        // 使用者要求: 餐食類項目 (餐廳) 預設不要顯示在地圖上, 地圖上密密麻麻全部都是吃飯的點反而看不清楚
        // 景點路線——這裡只是改「新增當下」的預設值, 使用者還是可以事後用看板項目列上的地圖圖示按鈕
        // (toggleShowOnMap) 自己打開某一筆餐廳的地圖顯示。ItineraryItem entity 本身的欄位預設值維持
        // true 不變 (景點/住宿等其他類別還是照舊預設顯示), 只在這裡針對 itemType=meal 覆寫成 false。
        if ("meal".equals(itemType)) {
            item.setShowOnMap(false);
        }

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
     * @param aiDescription AI 解析原文裡針對這個地點寫的簡介/介紹文字, 沒有就傳 null。只是暫存在這個
     *                      行程項目自己身上 (ItineraryItem.aiDescription), 顯示/匯出時會優先於資料庫裡
     *                      poi.description 的版本, 不會反過來覆寫共用的 POI 資料庫——只有使用者之後在
     *                      行程編輯畫面手動存檔修改介紹說明, 才會真的寫回 poi 資料表 (見
     *                      PoiService.updateDescription, 屆時也會把這個欄位清空, 改以資料庫版本為準)。
     */
    public ItineraryItem addItem(int IDID, Integer PID, String itemType, String customName, Integer stayDurationMin,
                                 String itemCountry, String itemRegion, String timeSlot, String aiDescription) {
        ItineraryItem item = addItem(IDID, PID, itemType, customName, stayDurationMin, itemCountry, itemRegion, timeSlot);
        if (aiDescription != null && !aiDescription.isBlank()) {
            item.setAiDescription(aiDescription);
            itineraryItemDAO.save(item);
        }
        return item;
    }

    /**
     * 新增「自訂項目」: 不連結公司 POI 資料庫, 但會自動地理編碼取得座標, 讓地圖照樣顯示這個點
     * 支援名稱用「或」分隔多個選項 (例如飯店常見的「A飯店或B飯店或C飯店」), 每個候選都會分別地理編碼,
     * 預設選第一個, 之後可以用 selectItemOption() 切換
     */
    public ItineraryItem addCustomItem(int IDID, String itemType, String rawName, Integer stayDurationMin, String locationHint) {
        revertToDraftIfCompletedByDay(IDID); // 見上方「標記已完成後再編輯要退回草稿」的說明
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
        // 使用者要求: 餐食類項目預設不要顯示在地圖上 (見 addItem() 同樣的說明), 自訂項目 (例如手動輸入的
        // 「A餐廳或B餐廳」候選名稱) 也要套用同一個預設值, 不是只有連結資料庫的餐廳才處理。
        if ("meal".equals(itemType)) {
            item.setShowOnMap(false);
        }
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
        revertToDraftIfCompletedByItem(IIID); // 見上方「標記已完成後再編輯要退回草稿」的說明
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
                null, null, null, null, null, null, null, null, null, null, null);
    }

    /**
     * @param itemType         看板上「編輯」時可以直接更換這個項目的類別 (景點/餐廳/住宿/交通...), 傳 null 或空字串代表不更動
     * @param fromLocation     交通項目專用: 起始點名稱
     * @param fromAddress      交通項目專用: 起始地址; 有填就直接採用, 沒填但有起始點名稱的話後端會自動查詢帶入
     * @param toLocation       交通項目專用: 目的地名稱; 有填會順便重新查詢目的地座標, 讓這個項目在地圖上的位置
     *                         (沿用單一經緯度欄位) 對應到「抵達的目的地」
     * @param toAddress        交通項目專用: 目的地地址; 有填就直接採用, 沒填但有目的地名稱的話後端會自動查詢帶入
     * @param transportMethod  交通項目專用: 交通工具 (高鐵/飛機/遊覽車/渡輪/計程車...)
     * @param transportNumber  交通項目專用: 航班/車次編號 (例如 CI100), 傳 null 代表不更動, 傳空字串會清空
     * @param commuteDuration  交通項目專用 (舊欄位, 已停用): 通勤時間 (自由文字, 例如「約1小時30分」)——
     *                         畫面已經改用下面的 commuteDurationMin, 這個參數保留只是不動舊呼叫點, 不會再有畫面傳值進來
     * @param startTime        交通項目專用: 出發時間 ("HH:mm", 選填); 傳 null 代表不更動, 傳空字串會清空。
     *                         建立行程時「去程/回程班機」表單填的時間就是存在這裡 (見 attachFlightLegsAcrossDays),
     *                         但建立之後原本完全沒有地方可以編輯——這裡補上編輯入口, 讓使用者事後也能補填/修正。
     * @param endTime          交通項目專用: 抵達時間 ("HH:mm", 選填), 用法同 startTime
     * @param commuteDurationMin 交通項目專用: 通勤時間 (分鐘, 數字), 取代上面的 commuteDuration 自由文字欄位,
     *                            用來帶入行程時間表計算 (見 board.html renderTimeline() 的說明); 跟 stayDurationMin
     *                            一樣是「傳了就直接覆蓋、包含清成 null」的語意, 不是「不為 null 才更動」
     */
    public void updateItemDetails(int IIID, String customName, Integer stayDurationMin, String locationHint,
                                  String timeSlot, String note, Boolean showOnMap,
                                  String itemType, String fromLocation, String fromAddress,
                                  String toLocation, String toAddress, String transportMethod, String transportNumber,
                                  String commuteDuration, String startTime, String endTime, Integer commuteDurationMin) {
        ItineraryItem item = itineraryItemDAO.findById(IIID);
        if (item == null) throw new IllegalArgumentException("找不到這個項目");
        revertToDraftIfCompletedByDay(item.getIDID()); // 見上方「標記已完成後再編輯要退回草稿」的說明

        item.setCustomName(customName);
        item.setStayDurationMin(stayDurationMin);
        item.setCommuteDurationMin(commuteDurationMin);
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
        if (transportNumber != null) {
            item.setTransportNumber(transportNumber.isBlank() ? null : transportNumber.trim());
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

        // 使用者反映「更改餐廳的時間, 時段會消失」修好後 (board.html 那邊改成不去動非交通項目的
        // startTime/endTime) 接著反映「時間不會更動」——情境是: 編輯餐廳/景點/住宿這種非交通類別項目的
        // 「停留時間」, 期待卡片上顯示的時間徽章 (開始→結束) 能跟著調整, 不是維持上一次自動整理排序
        // (autoArrangeDay()) 算出來的舊結束時間不變。因為 board.html 現在對「本來就不是交通類別」的項目,
        // 每次存檔都會把目前的 startTime/endTime 原封不動送回來 (見那邊的說明——是為了不要清空它們),
        // 上面兩段就只是把這個沒變的舊 endTime 又寫回去一次而已, 不會反映新的停留時間。
        // 修正: 交通項目的 startTime/endTime 是各自獨立編輯的欄位 (不是從停留時間推算, 這裡不動它),
        // 但非交通類別項目只要「有停留時間、也已經有開始時間」(代表這個項目已經被排定在時間軸上),
        // 就重新推算 endTime = startTime + 這次的停留時間, 讓時間徽章即時反映剛剛改的停留時間,
        // 不用整天重新按一次「自動整理」才會更新。
        boolean isTransportItem = "transport".equals(item.getItemType());
        if (!isTransportItem && stayDurationMin != null && item.getStartTime() != null) {
            item.setEndTime(item.getStartTime().plusMinutes(stayDurationMin));
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
        // 回程班機是唯一的例外: calculateAirportTransferSegments() 建立/補算這個項目的座標時, 刻意存的是
        // 「出發機場」(fromLocation) 不是「目的地」(toLocation)——如果這裡沒有排除, 使用者之後只要編輯這個
        // 項目任何欄位存檔一次 (包含單純按「顯示在地圖上」切換), 就會被這段既有邏輯蓋回抵達機場的座標,
        // 「最後一個景點→出發機場」這段地圖路線就會跟著跑掉。判斷方式跟 calculateAirportTransferSegments()
        // 找「回程班機」用的是同一套 (transportMethod === 飛機 + flightDirection == "return")。
        boolean isReturnFlightLeg = "飛機".equals(item.getTransportMethod())
                && "return".equals(item.getFlightDirection());
        if (isReturnFlightLeg) {
            if (fromLocation != null && !fromLocation.isBlank()) {
                if (geocodeCountry == null) geocodeCountry = resolveCountryForItem(item);
                GoogleMapsClient.GeocodeResult geo = geocodeAirport(fromLocation.trim(), geocodeCountry);
                if (geo != null) {
                    item.setLatitude(BigDecimal.valueOf(geo.latitude));
                    item.setLongitude(BigDecimal.valueOf(geo.longitude));
                }
            }
        } else if (toLocation != null && !toLocation.isBlank()) {
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

    // 取得一個行程項目可以拿來算距離的座標: 優先用項目自己存的座標 (交通/班機項目沒有連結 POI, 但
    // 有自己的經緯度), 沒有才退回查連結的 POI——跟 RouteService.resolveCoordinates() 是同一套邏輯,
    // 這裡另外重複一份是因為 RouteService 沒有對外公開這個方法, 兩邊各自服務不同的呼叫情境
    // (這裡是「順路景點推薦」, RouteService 是「相鄰項目拉車距離」), 沒有強行合併成共用方法。
    private double[] resolveItemCoordinates(ItineraryItem item) {
        if (item.getLatitude() != null && item.getLongitude() != null) {
            return new double[]{item.getLatitude().doubleValue(), item.getLongitude().doubleValue()};
        }
        if (item.getPID() != null) {
            Poi poi = poiDAO.findById(item.getPID());
            if (poi != null && poi.getLatitude() != null) {
                return new double[]{poi.getLatitude().doubleValue(), poi.getLongitude().doubleValue()};
            }
        }
        return null;
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
        revertToDraftIfCompletedByDay(IDID); // 見上方「標記已完成後再編輯要退回草稿」的說明
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
            // 見上方「標記已完成後再編輯要退回草稿」的說明——已經拿到 itinerary 物件, 順便一起改
            if ("completed".equals(itinerary.getStatus())) {
                itinerary.setStatus("draft");
            }
            itinerary.setArrangeMode("meal_time".equals(mode) ? "meal_time" : "all_last");
            itineraryDAO.save(itinerary);
        }
    }

    /**
     * @param mode "meal_time" (預設): 早餐固定第一個, 午餐/晚餐依累計時間排到接近 12:00/18:00, 住宿排最後
     *             "all_last": 只有餐廳依原本順序排到這一天的最後面 (景點/交通/自費/住宿都維持原本順序,
     *             不動——住宿本來就習慣落在一天的尾端附近, 結果會是「行程/景點 → 住宿 → 餐廳」)
     */
    public void autoArrangeDay(int IDID, String mode) {
        List<ItineraryItem> items = itineraryItemDAO.findByDay(IDID);
        if (items.isEmpty()) return;
        revertToDraftIfCompletedByDay(IDID); // 見上方「標記已完成後再編輯要退回草稿」的說明

        if ("all_last".equals(mode)) {
            // Patch 89: 使用者要求「餐廳住宿排最後改成只有餐廳排最後 所以會是行程 住宿 餐廳」——原本是
            // 餐廳跟住宿「一起」依原本順序排到最後面 (兩者可能交錯), 改成只有餐廳被搬到最後面, 住宿跟
            // 景點/交通/自費一樣維持原本順序不動——因為住宿在這個系統的其他建立/整理路徑本來就已經習慣
            // 被排在一天的最後面 (例如 meal_time 模式的 arranged.addAll(hotels)), 只搬餐廳的結果自然就
            // 會落成使用者要的「景點/行程 → 住宿 → 餐廳」這個順序, 不需要額外特判「把住宿排在餐廳前面」。
            List<ItineraryItem> anchorsAndHotels = new ArrayList<>();
            List<ItineraryItem> mealsToEnd = new ArrayList<>();
            for (ItineraryItem item : items) {
                if ("meal".equals(item.getItemType())) {
                    mealsToEnd.add(item);
                } else {
                    anchorsAndHotels.add(item);
                }
            }
            List<ItineraryItem> arranged = new ArrayList<>(anchorsAndHotels);
            arranged.addAll(mealsToEnd);
            for (int i = 0; i < arranged.size(); i++) {
                arranged.get(i).setSortOrder(i);
                itineraryItemDAO.save(arranged.get(i));
            }
            recalculateRoutes(IDID);
            return;
        }

        ItineraryDay day = itineraryDayDAO.findById(IDID);
        java.time.LocalTime dayStart = (day != null && day.getStartTime() != null) ? day.getStartTime() : java.time.LocalTime.of(9, 0);
        java.time.LocalTime breakfastTime = java.time.LocalTime.of(8, 0);
        java.time.LocalTime lunchTime = java.time.LocalTime.of(12, 0);
        java.time.LocalTime dinnerTime = java.time.LocalTime.of(18, 0);

        // Patch 74: 這一天如果已經有回程班機 (attachFlightItems() 可能已經跑過——這個方法不只在「建立
        // 行程」時被呼叫一次, 使用者在看板上按「餐廳住宿排最後」「依照早中晚安排」也會直接重跑這一天,
        // 那時候班機通常早就存在), 算出「最晚可以安排到幾點」(回程班機出發前, 扣掉機場緩衝時間)——
        // 早餐固定在最前面、本來就代表「出發前」, 不受這個限制; 午餐/晚餐算出來的時間如果已經到了/超過
        // 這個緩衝時間點, 代表人已經要去機場了, 這一餐當天根本排不進去, 直接刪除, 不要硬塞在回程班機
        // 後面 (那個時間點人已經在機場/飛機上, 不可能真的去吃, 見下面迴圈裡的說明)。
        ItineraryItem departureFlightForCutoff = null;
        for (ItineraryItem it : items) {
            if ("transport".equals(it.getItemType()) && "return".equals(it.getFlightDirection())
                    && it.getStartTime() != null) {
                departureFlightForCutoff = it; // 同一天如果有多段轉機, 取「排序在最前面」的那一段
                break;
            }
        }
        java.time.LocalTime dayCutoff = departureFlightForCutoff != null
                ? departureFlightForCutoff.getStartTime().minusMinutes(AIRPORT_BUFFER_MIN) : null;

        List<ItineraryItem> hotels = new ArrayList<>();
        List<ItineraryItem> mealsToPlace = new ArrayList<>(); // 所有餐廳, 依原本出現順序 (這是明確的「重新整理」動作, 不管之前有沒有標過時段, 都強制重新判斷位置)
        List<ItineraryItem> anchors = new ArrayList<>();      // 其餘項目 (景點/交通/自費/自由活動), 當作時間軸骨架

        for (ItineraryItem item : items) {
            if ("hotel".equals(item.getItemType())) {
                hotels.add(item);
            } else if ("meal".equals(item.getItemType()) && !isInFlightMeal(item)) {
                mealsToPlace.add(item);
            } else {
                // Patch 85: 機上套餐 (isInFlightMeal) 這裡刻意跟景點/交通/班機一樣歸進 anchors, 不進
                // mealsToPlace——它不是一筆需要依「目標用餐時間」重新排位置的一般餐食, 而是刻意跟在
                // 它所屬的那個班機後面 (轉換當下已經由 hideMealsOverlappingFlights() 排定好位置, 見該
                // 方法說明), 維持在 anchors 裡代表它會保留原本的相對順序, 不會被下面的早/午/晚餐排序
                // 邏輯搬到別的地方去、也不會被誤標記時段。
                anchors.add(item);
            }
        }

        // 依序標記時段: 有明確早餐標記 (addBreakfastPlaceholder() 建立時就已經標好, 見該方法說明) 的
        // 維持早餐, 不再無條件把「排在最前面的第 1 個餐廳」當成早餐——Patch 85 之後, 行程第一天已經
        // 不會再有早餐項目 (使用者要求「第一天不需要安排早餐」), 如果還是照位置強制判斷, 第一天真正的
        // 午餐反而會被誤標成早餐、插到最前面還套用 08:00 這個時間錨點, 排出完全錯誤的結果。其餘依序
        // 標記第 1 個=午餐, 第 2 個 (以後)=晚餐 (機上套餐已經在上面分類時歸進 anchors, 不會出現在
        // 這個清單裡, 不受這個時段標記影響)。
        int mealSlotIndex = 0;
        for (ItineraryItem meal : mealsToPlace) {
            if ("breakfast".equals(meal.getTimeSlot())) continue;
            meal.setTimeSlot(mealSlotIndex == 0 ? "lunch" : "dinner");
            mealSlotIndex++;
        }

        // 早餐固定放最前面, 午餐/晚餐依累計停留時間算出最接近目標時間 (12:00 / 18:00) 的位置插進去。
        // Patch 28: 三餐的 start_time/end_time 同時固定用預設用餐時間錨點 (早餐 08:00、午餐 12:00、
        // 晚餐 18:00) 直接寫死, 不再是 null——使用者反映用餐時間應該要有預設值, 也讓後面
        // hideMealsOverlappingFlights() 可以用這個時間跟班機時間比對是否重疊。
        //
        // 使用者反映「時間表出現『13:10 迴頭路』後面接一個『12:00』開始的午餐, 時間倒退」——追查後發現:
        // findIndexForTargetTime() 原本只用「停留時間」累加去估算插入點, 完全沒把景點跟景點之間的拉車
        // 時間算進去; 但這一餐真正顯示的時間卻是寫死的目標時間 (12:00), 兩個基準對不起來——只要前面幾個
        // 景點之間有拉車時間 (幾乎一定會有), 這一餐實際被插入的位置會比「沒有拉車時間」時晚, 可是顯示
        // 時間還是釘死 12:00, 就會比它前一項的估計結束時間還早, 畫面上時間軸就會出現倒退的現象。
        // 修正分兩步: (1) findIndexForTargetTime() 內部改用跟 RouteService fallback 公式一致的直線距離
        // 估算 (estimateTravelMinutes), 讓抓插入點的基準盡量貼近之後 recalculateRoutes() 真正算出來的
        // 拉車時間, 減少抓錯插入點的機會; (2) 這一餐真正顯示的開始時間, 改成「目標用餐時間」跟「照這個
        // 順序實際排下來, 最早幾點才可能走到這個地點」(estimateArrivalTime) 兩者取比較晚的一個——行程
        // 排得比較趕、實際到這一餐地點的時間比目標時間晚時, 直接採用比較晚、比較合理的那個時間, 不要死守
        // 目標時間, 這樣整天的時間軸才能保證單調往後推進, 不會再出現「前面才走到 13:10、後面卻接一個
        // 12:00 開始的午餐」這種時間倒退的狀況。早餐維持原本行為不變 (固定 08:00、固定插在最前面)——
        // 這次沒有回報過早餐有類似問題, 範圍先只收斂在午餐/晚餐。
        List<ItineraryItem> arranged = new ArrayList<>(anchors);
        List<ItineraryItem> mealsToDelete = new ArrayList<>(); // Patch 74: 見上面 dayCutoff 說明
        for (ItineraryItem meal : mealsToPlace) {
            int insertIndex;
            java.time.LocalTime start;
            if ("breakfast".equals(meal.getTimeSlot())) {
                insertIndex = 0;
                start = breakfastTime;
            } else {
                java.time.LocalTime target = "lunch".equals(meal.getTimeSlot()) ? lunchTime : dinnerTime;
                insertIndex = findIndexForTargetTime(arranged, dayStart, target);
                java.time.LocalTime estimatedArrival = estimateArrivalTime(arranged, dayStart, insertIndex, meal);
                start = estimatedArrival.isAfter(target) ? estimatedArrival : target;

                // Patch 74: 早餐 (上面 if 分支) 排在最前面、代表出發前, 不受這個限制; 午餐/晚餐這裡
                // 算出來的開始時間如果已經到了/超過回程班機的機場緩衝時間點, 代表排出來的位置已經在
                // 回程班機當天要去機場之後——不能硬塞, 直接刪除這筆, 不要進 arranged。
                if (dayCutoff != null && !start.isBefore(dayCutoff)) {
                    mealsToDelete.add(meal);
                    continue;
                }
            }
            int mealDur = meal.getStayDurationMin() != null ? meal.getStayDurationMin() : defaultStayMinutes(meal.getItemType());
            meal.setStartTime(start);
            meal.setEndTime(start.plusMinutes(mealDur));
            arranged.add(Math.min(insertIndex, arranged.size()), meal);
        }

        arranged.addAll(hotels); // 住宿固定排最後

        for (int i = 0; i < arranged.size(); i++) {
            ItineraryItem item = arranged.get(i);
            item.setSortOrder(i);
            itineraryItemDAO.save(item);
        }
        if (!mealsToDelete.isEmpty()) {
            // Patch 84: 使用者提供的 Railway 部署 log 顯示這裡實際跑出
            // DataIntegrityViolationException（route_segment 的外鍵 from_item_id 擋住刪除）——這一天
            // 如果先前已經呼叫過 recalculateRoutes() 算過拉車距離 (route_segment 表已經有連到這個項目的
            // 快取列), 直接 itineraryItemDAO.deleteById() 會被那個外鍵擋下來。這個地雷 removeItem()
            // (使用者在看板上手動刪除單一項目那個既有方法) 其實早就踩過、也已經修好了, 只是那次的修法
            // 沒有同步套用到這裡——見 removeItem() 的註解「route_segment 的外鍵沒設 CASCADE, 有算過拉車
            // 距離的項目直接刪會被擋」, 這裡比照同一套做法: 刪除項目之前先清掉這天的路段快取, 讓刪除本身
            // 不會被外鍵擋住; 下面的 recalculateRoutes() 本來就會重新算出一份新的, 不會少算。
            routeSegmentDAO.deleteByDay(IDID);
            for (ItineraryItem meal : mealsToDelete) {
                itineraryItemDAO.deleteById(meal.getIIID());
            }
        }

        recalculateRoutes(IDID);
    }

    // Patch 74: 使用者反映「AI排行程要以航班時間為主, 先處理完航班時間再去安排後續行程」——追查後發現
    // 這一整組「累加估算現在排到幾點」的邏輯 (下面的 findIndexForTargetTime()/estimateArrivalTime(),
    // 以及 trimItemsAroundFlights() 對住宿/交通類項目的游標推進), 對序列裡任何一個項目一律用「(可能沒
    // 設定的) 停留時間, 或 defaultStayMinutes(itemType)」累加, 從來沒有檢查這個項目「自己是不是已經
    // 有真實的出發/抵達時間」——班機 (attachFlightLegsAcrossDays() 設的) 剛好就是
    // defaultStayMinutes("transport") 固定回傳 0 的類型, 等於把班機真正佔用的那幾個小時 (例如 05:00
    // 飛到 12:00, 整整 7 小時) 當作瞬間發生, 後面接著的午餐/晚餐插入點跟顯示時間全部嚴重低估——這正是
    // 「航班12:00抵達, 排的午餐卻沒跟著往後挪、時間軸還會倒退」的根因。
    //
    // 修正: 序列裡任何一項只要自己已經有真實的出發/抵達時間 (目前只有班機、以及這個方法自己在同一次
    // 呼叫裡已經排定過時間的餐食符合), 直接跳到它的真實結束時間當作新的游標, 不要再用「預設停留時間」
    // 累加瞎猜——已知的真實時間一定比瞎猜準, 這樣不管班機排在序列第幾個位置, 都能正確反映它真正佔用
    // 的時間, 不會再被當成 0 分鐘忽略過去。
    private java.time.LocalTime advancePast(java.time.LocalTime current, ItineraryItem prev, ItineraryItem it) {
        if (it.getStartTime() != null && it.getEndTime() != null) {
            java.time.LocalTime end = it.getEndTime();
            // Patch 76: 使用者反映「去程飛機的抵達時間後 90 分鐘開始安排 (通勤時間)」——降落當下不代表
            // 馬上就能開始行程, 還要過海關/領行李/從機場通勤到市區, 這裡統一補上這段緩衝, 讓後面所有沿用
            // advancePast() 的邏輯 (findIndexForTargetTime/estimateArrivalTime 排午餐晚餐、
            // trimItemsAroundFlights 判斷有沒有超出可行時間範圍) 都一致從「降落 + 90 分鐘」開始估算,
            // 不再是「一降落就能馬上開始行程」這種不切實際的假設。只加在「去程班機」身上——回程班機本來就
            // 代表這天最後一段行程, 後面不會再接東西, 不需要往後推。
            if ("transport".equals(it.getItemType()) && "outbound".equals(it.getFlightDirection())) {
                end = end.plusMinutes(AIRPORT_BUFFER_MIN);
            }
            return end.isBefore(current) ? current : end; // 理論上真實時間不會逆著游標跑, 保險起見取較晚者
        }
        current = current.plusMinutes(estimateTravelMinutes(prev, it));
        int dur = it.getStayDurationMin() != null ? it.getStayDurationMin() : defaultStayMinutes(it.getItemType());
        return current.plusMinutes(dur);
    }

    // 依「目前已排好的項目序列」累加「停留時間 + 估算拉車時間」(或項目自己真實的出發/抵達時間, 見上面
    // advancePast() 說明), 找出最接近目標時間 (12:00/18:00) 該插在第幾個位置 (跟 RouteService 一樣用
    // 直線距離估算拉車時間, 但這裡是排序當下用的簡化推算, 不是精準排程, 之後 recalculateRoutes() 算
    // 出來的才是真正準確的距離/時間)
    private int findIndexForTargetTime(List<ItineraryItem> sequence, java.time.LocalTime dayStart, java.time.LocalTime target) {
        java.time.LocalTime current = dayStart;
        ItineraryItem prev = null;
        for (int i = 0; i < sequence.size(); i++) {
            ItineraryItem it = sequence.get(i);
            java.time.LocalTime end = advancePast(current, prev, it);
            if (!end.isBefore(target)) {
                return i + 1; // 這一項結束時已經超過目標時間, 插在它後面
            }
            current = end;
            prev = it;
        }
        return sequence.size();
    }

    // 依「目前已排好的項目序列」的前 insertIndex 項, 估算最早幾點才能真的走到 meal 這個地點 (含每一段的
    // 估算拉車時間, 或項目自己真實的出發/抵達時間), 給 autoArrangeDay() 判斷這一餐的顯示時間要不要比
    // 寫死的目標時間 (12:00/18:00) 晚。
    private java.time.LocalTime estimateArrivalTime(List<ItineraryItem> sequence, java.time.LocalTime dayStart,
                                                    int insertIndex, ItineraryItem meal) {
        java.time.LocalTime current = dayStart;
        ItineraryItem prev = null;
        int limit = Math.min(insertIndex, sequence.size());
        for (int i = 0; i < limit; i++) {
            ItineraryItem it = sequence.get(i);
            current = advancePast(current, prev, it);
            prev = it;
        }
        current = current.plusMinutes(estimateTravelMinutes(prev, meal));
        return current;
    }

    // 兩個項目之間的拉車時間估算: 跟 RouteService 的 fallback 公式 (沒有 Google API 金鑰或呼叫失敗時用)
    // 同一套邏輯——直線距離 (haversine) 除以均速 (1公里以內走路 4.5km/h, 否則開車 30km/h) 換算成分鐘,
    // 乘 1.5 倍安全緩衝後四捨五入到最近 10 分鐘, 盡量讓這裡排序當下用的估算值跟之後 recalculateRoutes()
    // 真正算出來的結果同一個量級。任一邊沒有座標 (還沒地理編碼成功、或本來就是純文字預留項目) 或這是這天
    // 第一個項目 (prev 為 null) 時, 沒辦法算距離, 一律退回一個保守的預設緩衝值, 避免估成 0 分鐘反而低估。
    private static final int DEFAULT_TRAVEL_ESTIMATE_MIN = 15;

    private int estimateTravelMinutes(ItineraryItem prev, ItineraryItem next) {
        if (prev == null || next == null) return DEFAULT_TRAVEL_ESTIMATE_MIN;
        BigDecimal fromLat = prev.getLatitude(), fromLng = prev.getLongitude();
        BigDecimal toLat = next.getLatitude(), toLng = next.getLongitude();
        if (fromLat == null || fromLng == null || toLat == null || toLng == null) return DEFAULT_TRAVEL_ESTIMATE_MIN;

        double distanceKm = scheduleHaversineKm(fromLat.doubleValue(), fromLng.doubleValue(),
                toLat.doubleValue(), toLng.doubleValue());
        double avgSpeedKmh = distanceKm <= 1.0 ? 4.5 : 30.0; // 跟 RouteService.recommendMode() 同一個門檻: 1公里內走路
        double rawMinutes = (distanceKm / avgSpeedKmh) * 60;
        long buffered = Math.round(rawMinutes * 1.5 / 10.0) * 10;
        return (int) Math.max(buffered, 10);
    }

    // 跟 RouteService.haversineKm() 同一套公式——那邊是 private, 這裡為了不更動既有類別的可見度,
    // 另外寫一份同樣的小工具函式 (排程預估跟實際算路線本來就是兩個不同階段, 分開維護風險更低)。
    private double scheduleHaversineKm(double lat1, double lon1, double lat2, double lon2) {
        final double earthRadiusKm = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * earthRadiusKm * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
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

    // Patch 28: 使用者反映 AI 排的行程常常玩到很晚, 這裡在 createItineraryWithAiPlan() 裡整趟排完、
    // autoArrangeItinerary() 也跑完 (每一天的最終順序、三餐固定時間錨點都已經確定) 之後呼叫,
    // 逐天依序累加「目前已排到第幾點」, 只要某個項目 (景點/餐食) 預估開始時間已經超過晚上 20:30,
    // 就直接刪掉這個項目跟它後面所有一樣會更晚開始的項目——確保「每天行程最多只到晚上 8:30」。
    // 住宿/交通 (機場接送/轉機等) 不受這個規則影響 (飯店本來就代表當天結束回房休息, 不管幾點都不會被砍掉),
    // 但還是要照樣累加時間軸, 讓它後面接的項目 (理論上不會有, 但保留以防萬一) 估算時間維持連續。
    private static final java.time.LocalTime DAY_CUTOFF = java.time.LocalTime.of(20, 30);

    /**
     * Patch 78: 使用者反映「之前有設定最後行程時間應該是 20:30, 但直接排到凌晨 4 點」——這個方法本來
     * 就是負責這條規則的地方 (patch 28), 但它原本只在 createItineraryWithAiPlan() 內部、班機都還沒
     * 插入這天之前跑過一次, 之後不管是 attachFlightItems() 插入班機、看板「自動整理」按鈕重排、還是
     * 單純重新整理看板頁 (前端呼叫 GET /day/{IDID}/items, 見 trimItemsAroundFlights() 同一次修正的
     * 說明), 都完全不會再重跑這個檢查。這次 patch 76/77 讓「去程班機降落 + 90 分鐘緩衝」之後的景點
     * 估算時間統一往後推, 這件事本身是對的, 但因為這個 20:30 上限從來沒有機會用「班機插入後、時間已經
     * 往後推移」的最新狀態重新檢查一次, 才會一路排到隔天凌晨都沒有被擋下來——不是這次改壞的, 是這條
     * 既有規則從一開始就沒有被放到會重複執行的地方。補上這個以 ITID 為單位的公開版本, 讓它可以跟
     * trimItemsAroundFlights() 一樣被 create()/createWithAiPlan() 的班機處理流程、board() 的自我
     * 修復、以及 GET /day/{IDID}/items 這個真正的資料來源一起呼叫。
     */
    public void trimDaysExceedingCutoff(int ITID) {
        trimDaysExceedingCutoff(itineraryDayDAO.findByItinerary(ITID));
    }

    private void trimDaysExceedingCutoff(List<ItineraryDay> days) {
        for (ItineraryDay day : days) {
            List<ItineraryItem> items = itineraryItemDAO.findByDay(day.getIDID());
            if (items.isEmpty()) continue;

            java.time.LocalTime dayStart = day.getStartTime() != null ? day.getStartTime() : java.time.LocalTime.of(9, 0);
            java.time.LocalTime current = dayStart;
            ItineraryItem prev = null;
            boolean cutoffTriggered = false;
            List<ItineraryItem> toDelete = new ArrayList<>();

            for (ItineraryItem item : items) {
                if ("hotel".equals(item.getItemType()) || "transport".equals(item.getItemType())) {
                    // Patch 78: 這裡原本用 defaultStayMinutes(item.getItemType()) 累加, 但班機/交通的
                    // defaultStayMinutes() 固定回傳 0——等於完全忽略班機真正佔用的時間, 跟 patch 74
                    // 修正 advancePast() 之前的 findIndexForTargetTime()/estimateArrivalTime() 是
                    // 同一種毛病。改用 advancePast() (順便套用「去程班機降落 + 90 分鐘緩衝」), 讓這裡
                    // 跟 trimItemsAroundFlights() 算出來的時間基準一致, 不要各算各的。
                    current = advancePast(current, prev, item);
                    prev = item;
                    continue;
                }
                if (isInFlightMeal(item)) {
                    // Patch 83: 機上套餐本來就綁定班機時間, 不受「每天最多排到 20:30」這條以地面行程
                    // 為前提的規則限制 (班機本身也一樣不受這條規則限制, 見上面 hotel/transport 分支)——
                    // 比照處理: 不刪除, 只用它自己真實的時間推進游標。
                    current = advancePast(current, prev, item);
                    prev = item;
                    continue;
                }
                if (cutoffTriggered) {
                    toDelete.add(item); // 已經超過 20:30, 後面接的也一併捨棄, 不留不連貫的殘留
                    continue;
                }

                // 這裡原本 (patch 78 第一版) 對「沒有寫死時間」的景點直接用 start = current, 完全沒有
                // 加上 estimateTravelMinutes(prev, item) 這段拉車時間——跟 trimItemsAroundFlights() 的
                // 對應邏輯不一致 (那邊每一項都會先加拉車時間再判斷), 會讓這裡低估整天實際累加下來的時間,
                // 20:30 這個上限判斷得比應有的寬鬆。改成跟 trimItemsAroundFlights() 完全一致的算法:
                // 先算「加上拉車時間後最早能到的時間」(travelArrival), 項目自己有寫死時間就用寫死的,
                // 沒有就用 travelArrival；但寫死時間如果比 travelArrival 還早 (理論上不該發生, 保險起見),
                // 一律取較晚者, 避免游標往回跳。
                java.time.LocalTime travelArrival = current.plusMinutes(estimateTravelMinutes(prev, item));
                java.time.LocalTime start = item.getStartTime() != null ? item.getStartTime() : travelArrival;
                if (start.isBefore(travelArrival)) {
                    start = travelArrival;
                }

                if (start.isAfter(DAY_CUTOFF)) {
                    cutoffTriggered = true;
                    toDelete.add(item);
                    continue;
                }

                int dur = item.getStayDurationMin() != null ? item.getStayDurationMin() : defaultStayMinutes(item.getItemType());
                java.time.LocalTime realEnd = item.getEndTime();
                current = (realEnd != null && !realEnd.isBefore(start)) ? realEnd : start.plusMinutes(dur);
                prev = item;
            }

            if (!toDelete.isEmpty()) {
                // Patch 84: 使用者提供的 Railway log 顯示這裡實際跑出 DataIntegrityViolationException
                // （route_segment 的外鍵 from_item_id 擋住刪除）——這一天如果先前已經呼叫過
                // recalculateRoutes() 算過拉車距離 (route_segment 表已經有連到這個項目的快取列), 直接
                // itineraryItemDAO.deleteById() 會被那個外鍵擋下來 (見 createItineraryWithAiPlan() 裡
                // autoArrangeItinerary() → 這個方法的呼叫順序: autoArrangeItinerary() 內部
                // autoArrangeDay() 早就已經呼叫過 recalculateRoutes() 算好路段, 這個方法接著要刪除超過
                // 20:30 的項目時就會撞上)。比照既有的 removeItem() 方法同一套做法: 刪除項目之前先清掉
                // 這天的路段快取, 讓刪除本身不會被外鍵擋住; 下面的 recalculateRoutes() 本來就會重新算出
                // 一份新的, 不會少算。
                routeSegmentDAO.deleteByDay(day.getIDID());
                for (ItineraryItem item : toDelete) {
                    itineraryItemDAO.deleteById(item.getIIID());
                }
                // 刪掉項目後 sort_order 會有空隙, 重新壓縮成連續的 0..n-1 (跟 trimItemsAroundFlights()
                // 一致, 避免留下不連續的排序造成前端顯示/拖曳異常)。
                List<ItineraryItem> remaining = itineraryItemDAO.findByDay(day.getIDID());
                for (int i = 0; i < remaining.size(); i++) {
                    remaining.get(i).setSortOrder(i);
                    itineraryItemDAO.save(remaining.get(i));
                }
                recalculateRoutes(day.getIDID());
            }
        }
    }

    // Patch 83: 這個名字是「機上套餐」轉換後的固定顯示名稱——判斷一筆餐食項目是不是已經被下面
    // hideMealsOverlappingFlights() 轉換過的機上套餐, 讓 trimItemsAroundFlights()/trimDaysExceedingCutoff()
    // 可以認得它、不要用「人在地面上移動」的前提去誤刪它 (見那兩個方法裡對應的說明)。
    private static final String INFLIGHT_MEAL_NAME = "機上套餐";

    private boolean isInFlightMeal(ItineraryItem item) {
        return "meal".equals(item.getItemType()) && INFLIGHT_MEAL_NAME.equals(item.getCustomName());
    }

    // Patch 28: 班機時間如果剛好卡到某一餐固定的用餐時間 (例如中午 12 點左右的班機跟午餐重疊), 這餐當天
    // 就不需要再排了——一定要在 attachFlightItems() 把去程/回程班機轉成 transport 項目「之後」才呼叫這個
    // 方法, 不然這天還沒有任何班機項目可以比對。
    //
    // Patch 83: 使用者要求「行程第一天/最後一天如果航班時間包含到吃飯時間, 餐廳可以直接顯示機上套餐,
    // 不須顯示早餐」——原本這裡是整筆刪除, 但線控實際上還是需要知道「這一餐算在機上解決, 不用額外
    // 安排」, 直接刪掉反而讓行程表看起來這一餐憑空消失, 不夠清楚。改成: 不刪除這筆項目, 而是把它轉換成
    // 一筆「機上套餐」——解除跟原本飯店/餐廳 POI 的連結 (機上套餐不是真正的地點, 不該再顯示座標/地圖
    // 圖釘/簡介), 顯示名稱固定改成「機上套餐」(不管原本標記的是早餐/午餐/晚餐, 只要跟班機時間重疊, 代表
    // 這一餐實際上是在飛機上解決)。開始/結束時間維持原本的值不變 (本來就是造成重疊判斷的那組時間, 不需要
    // 也不應該重算)。已經轉換過的項目 (isInFlightMeal) 跳過, 不用重複處理。
    public void hideMealsOverlappingFlights(int ITID) {
        for (ItineraryDay day : itineraryDayDAO.findByItinerary(ITID)) {
            List<ItineraryItem> items = itineraryItemDAO.findByDay(day.getIDID());
            List<ItineraryItem> flights = items.stream()
                    .filter(item -> "transport".equals(item.getItemType())
                            && item.getStartTime() != null && item.getEndTime() != null)
                    .collect(java.util.stream.Collectors.toList());
            if (flights.isEmpty()) continue;

            for (ItineraryItem item : items) {
                if (!"meal".equals(item.getItemType())) continue;
                if (isInFlightMeal(item)) continue; // 已經轉換過, 不用重複處理
                if (item.getStartTime() == null || item.getEndTime() == null) continue; // 缺時間資訊就跳過, 不要誤刪
                boolean overlaps = flights.stream().anyMatch(f ->
                        timeRangesOverlap(item.getStartTime(), item.getEndTime(), f.getStartTime(), f.getEndTime()));
                if (overlaps) {
                    // Patch 85: 使用者要求「機上套餐要跟在航班下面」——找出實際造成重疊的那一段班機,
                    // 待會轉換完直接把這筆項目的順序移到緊接在它後面, 不管轉換前原本排在哪裡 (原本是
                    // 依「午餐/晚餐目標時間」估算出來的位置, 不一定緊接著班機, 見使用者附的截圖: 曾經
                    // 被排到降落後其他景點的後面)。
                    ItineraryItem overlappingFlight = flights.stream()
                            .filter(f -> timeRangesOverlap(item.getStartTime(), item.getEndTime(),
                                    f.getStartTime(), f.getEndTime()))
                            .findFirst().orElse(null);

                    item.setPID(null);
                    item.setCustomName(INFLIGHT_MEAL_NAME);
                    item.setLatitude(null);
                    item.setLongitude(null);
                    item.setAiDescription(null);
                    item.setShowOnMap(false);
                    itineraryItemDAO.save(item);

                    if (overlappingFlight != null) {
                        repositionItemAfter(day.getIDID(), item, overlappingFlight);
                    }
                }
            }
        }
    }

    // Patch 85: 把 itemToMove 移到緊接在 anchor 後面的位置, 其餘項目依原順序往後遞補, 重新壓縮
    // sort_order 成連續的 0..n-1。給 hideMealsOverlappingFlights() 轉換機上套餐時使用, 讓它固定
    // 緊跟在造成重疊的那段班機後面, 不管轉換前原本排在哪裡。anchor 如果因為某種原因已經不在清單裡
    // (理論上不該發生, 保險起見), 退回排在最後面, 不會讓項目憑空消失。
    private void repositionItemAfter(int IDID, ItineraryItem itemToMove, ItineraryItem anchor) {
        List<ItineraryItem> items = new ArrayList<>(itineraryItemDAO.findByDay(IDID));
        items.removeIf(it -> it.getIIID() == itemToMove.getIIID());
        int anchorIndex = -1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getIIID() == anchor.getIIID()) {
                anchorIndex = i;
                break;
            }
        }
        int insertAt = anchorIndex >= 0 ? anchorIndex + 1 : items.size();
        items.add(insertAt, itemToMove);
        for (int i = 0; i < items.size(); i++) {
            items.get(i).setSortOrder(i);
            itineraryItemDAO.save(items.get(i));
        }
    }

    // 兩個時間區間 [aStart, aEnd) / [bStart, bEnd) 是否有重疊 (前提: 呼叫端已經保證兩邊 start < end)
    private boolean timeRangesOverlap(java.time.LocalTime aStart, java.time.LocalTime aEnd,
                                      java.time.LocalTime bStart, java.time.LocalTime bEnd) {
        return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
    }

    // 使用者反映「回程班機明明中午 12:30 就起飛, 行程表卻還排了晚上 18:00 的晚餐」——hideMealsOverlappingFlights()
    // 只處理「跟班機時間『字面重疊』的那一餐」, 對「班機出發後幾個小時, 完全沒有重疊、但邏輯上人早就已經
    // 離開/在飛機上」的項目完全沒有防護 (回程晚餐 18:00-19:00 對回程班機 12:30-14:00 來說沒有字面重疊,
    // 就不會被那個方法刪掉)。這裡另外補一層更全面的檢查: 逐天累加估算 (跟 autoArrangeDay 的
    // estimateArrivalTime 同一套 haversine 估算), 只要
    //   (1) 這天有「去程班機」(flightDirection=outbound, 有抵達時間) 時, 抵達之前不可能有任何行程
    //       (人根本還沒下飛機), 抵達前面排的項目全部刪掉;
    //   (2) 這天有「回程班機」(flightDirection=return, 有出發時間) 時, 從班機起飛前 AIRPORT_BUFFER_MIN
    //       分鐘 (預留去機場/辦登機的時間) 開始, 這個時間點之後才會開始的項目全部刪掉 (不只是跟班機時間
    //       重疊的那幾筆, 後面接著的也一起刪, 不然會留下「刪掉午餐但晚餐還在」這種不連貫的殘留)。
    // 只影響景點/餐廳這種「會佔用時間」的項目; 住宿/其他一般交通項目不受影響 (但還是要照樣累加時間軸,
    // 讓後面的估算連續), 班機本身當然也不會刪自己。一定要在 attachFlightItems() 把去程/回程班機轉成
    // transport 項目之後才呼叫這個方法, 不然這天還沒有任何班機項目可以判斷。
    private static final int AIRPORT_BUFFER_MIN = 90; // 出發前預留到機場/辦登機的緩衝時間 (分鐘)

    public void trimItemsAroundFlights(int ITID) {
        for (ItineraryDay day : itineraryDayDAO.findByItinerary(ITID)) {
            List<ItineraryItem> items = itineraryItemDAO.findByDay(day.getIDID());
            if (items.isEmpty()) continue;

            ItineraryItem arrivalFlight = null;
            ItineraryItem departureFlight = null;
            for (ItineraryItem it : items) {
                if (!"transport".equals(it.getItemType())) continue;
                if ("outbound".equals(it.getFlightDirection()) && it.getEndTime() != null) {
                    // 同一天如果有多段轉機, 取「排序在最後面」的那一段抵達時間才是真正落地的時間
                    arrivalFlight = it;
                }
                if ("return".equals(it.getFlightDirection()) && it.getStartTime() != null && departureFlight == null) {
                    // 同一天如果有多段轉機, 取「排序在最前面」的那一段出發時間才是真正離開的時間
                    departureFlight = it;
                }
            }
            if (arrivalFlight == null && departureFlight == null) continue;

            // Patch 76: 使用者反映「去程飛機的抵達時間後 90 分鐘開始安排(通勤時間), 回程飛機出發前 90
            // 分鐘安排行程, 但現在都會無視飛機時間導致時間表錯亂」——追查後發現這裡原本只有回程 (departureFlight)
            // 那一側有扣 AIRPORT_BUFFER_MIN 緩衝, 去程 (arrivalFlight) 這一側完全沒有: cursor 直接採用
            // 「降落當下」(arrivalFlight.getEndTime()), 沒有預留下飛機後過海關/領行李/從機場通勤到市區的時間,
            // 跟回程側的緩衝設計不對稱。補上 earliestAllowed (降落 + 90 分鐘緩衝), 當作這天真正能開始安排
            // 行程的下限, 取代原本沒有緩衝的 cursor 起點。
            java.time.LocalTime dayStart = day.getStartTime() != null ? day.getStartTime() : java.time.LocalTime.of(9, 0);
            java.time.LocalTime earliestAllowed = (arrivalFlight != null)
                    ? arrivalFlight.getEndTime().plusMinutes(AIRPORT_BUFFER_MIN) : null;
            java.time.LocalTime cursor = (earliestAllowed != null) ? earliestAllowed : dayStart;
            java.time.LocalTime cutoff = (departureFlight != null)
                    ? departureFlight.getStartTime().minusMinutes(AIRPORT_BUFFER_MIN) : null;

            boolean pastArrival = (arrivalFlight == null);
            boolean cutoffTriggered = false;
            List<ItineraryItem> toDelete = new ArrayList<>();
            ItineraryItem prev = null;

            for (ItineraryItem item : items) {
                if (item == arrivalFlight) { pastArrival = true; prev = item; continue; }
                if (item == departureFlight) { prev = item; continue; }
                if ("hotel".equals(item.getItemType()) || "transport".equals(item.getItemType())) {
                    // 住宿/其他一般交通不受剪裁影響, 但還是要照樣累加時間軸, 讓後面的估算連續。
                    // Patch 74: 這裡原本一律用「預設停留時間」累加, 沒有檢查這個交通項目是不是本身
                    // 已經有真實的出發/抵達時間 (例如同一天還有另一段轉機航班)——改用 advancePast()
                    // (見上面說明), 有真實時間就直接採用, 不要再把它當成 0 分鐘忽略過去。
                    cursor = advancePast(cursor, prev, item);
                    prev = item;
                    continue;
                }
                if (isInFlightMeal(item)) {
                    // Patch 83: 機上套餐 (hideMealsOverlappingFlights() 轉換出來的) 本來就是刻意跟班機
                    // 時間重疊——人在飛機上用餐, 不是在地面移動——下面「排在班機前面是否合理」「有沒有超過
                    // 回程截止時間」這些判斷全部是以「人在地面上」為前提, 對這種項目完全不適用。比照上面
                    // 住宿/交通的處理方式: 不刪除, 只用它自己真實的開始/結束時間推進游標, 讓後面接的項目
                    // 估算時間維持連續。
                    cursor = advancePast(cursor, prev, item);
                    prev = item;
                    continue;
                }
                if (!pastArrival) {
                    // Patch 73: 配合上面 attachFlightLegsAcrossDays() 的修正——早餐這種「出發前」的固定
                    // 時間餐食, 現在會被刻意留在班機『前面』(見那邊的說明), 排在班機前面本身不代表「人
                    // 還沒下飛機却排了不可能的行程」, 只要它自己寫死的時間確實不晚於班機出發時間, 就是
                    // 合理的「出發前先吃完早餐再去機場」, 不能砍掉——這裡原本無條件把「排在班機前面」的
                    // 項目全部當成不可能發生而刪除, 沒有這層例外的話, 早餐反而會被這次改動連帶誤刪。
                    boolean legitimateBeforeDeparture = arrivalFlight != null && "meal".equals(item.getItemType())
                            && item.getStartTime() != null && arrivalFlight.getStartTime() != null
                            && !item.getStartTime().isAfter(arrivalFlight.getStartTime());
                    if (!legitimateBeforeDeparture) {
                        toDelete.add(item); // 人還沒下飛機, 這個項目不可能真的排得進去
                    }
                    continue;
                }
                if (cutoffTriggered) {
                    toDelete.add(item); // 已經過了「該去機場了」的時間點, 後面接的也一併捨棄, 不留不連貫的殘留
                    continue;
                }

                // Patch 76: 這一項如果自己已經有寫死的開始時間 (餐廳訂位、或 autoArrangeDay 在班機還沒
                // 插入這天之前就先算好、之後才被排到班機後面的舊午餐/晚餐時間——後者正是使用者這次截圖
                // 「回程 11:00 起飛, 行程卻排到 21:30」「淺草今半 13:05 排在班機 15:00 降落、東京晴空塔
                // 之後」的成因), 不能無條件信任它、直接拿來當這一項的顯示時間——那個時間可能是在完全不知道
                // 班機真正時間的情況下算出來的舊值, 早於這天真正能開始 (去程降落+90分鐘緩衝) 的時間點,
                // 也可能造成下面 cursor 往回跳、後面接的項目估算跟著錯亂。分兩層防護:
                //   (1) 這個寫死時間早於 earliestAllowed (人根本還沒到、還沒過完緩衝時間), 代表這筆從一
                //       開始排的時間點就不合理, 直接刪除, 不要留著造成時間軸倒退。
                //   (2) 就算晚於 earliestAllowed, 也不能讓它比目前累加游標 (cursor, 已經反映前面所有
                //       項目真正排到的時間) 還早——跟 advancePast() 一樣的「取較晚者」保護, 避免游標往回跳。
                java.time.LocalTime travelArrival = cursor.plusMinutes(estimateTravelMinutes(prev, item));
                java.time.LocalTime start = item.getStartTime() != null ? item.getStartTime() : travelArrival;
                if (earliestAllowed != null && item.getStartTime() != null && item.getStartTime().isBefore(earliestAllowed)) {
                    toDelete.add(item);
                    continue;
                }
                if (start.isBefore(travelArrival)) {
                    start = travelArrival; // 寫死時間比累加估算的到達時間還早, 不能真的採用, 否則游標倒退
                }
                if (cutoff != null && !start.isBefore(cutoff)) {
                    cutoffTriggered = true;
                    toDelete.add(item);
                    continue;
                }
                int dur = item.getStayDurationMin() != null ? item.getStayDurationMin() : defaultStayMinutes(item.getItemType());
                java.time.LocalTime realEnd = item.getEndTime();
                cursor = (realEnd != null && !realEnd.isBefore(start)) ? realEnd : start.plusMinutes(dur);
                prev = item;
            }

            if (!toDelete.isEmpty()) {
                // Patch 84: 跟 trimDaysExceedingCutoff() 同一個地雷 (見那邊的說明)——這一天如果先前已經
                // 呼叫過 recalculateRoutes() 算過拉車距離, route_segment 的外鍵會擋住這裡的
                // itineraryItemDAO.deleteById()。比照 removeItem() 的既有做法, 刪除前先清掉這天的路段
                // 快取, 下面的 recalculateRoutes() 會重新算出一份新的。
                routeSegmentDAO.deleteByDay(day.getIDID());
                for (ItineraryItem item : toDelete) {
                    itineraryItemDAO.deleteById(item.getIIID());
                }
                // 刪掉項目後 sort_order 會有空隙, 重新壓縮成連續的 0..n-1
                List<ItineraryItem> remaining = itineraryItemDAO.findByDay(day.getIDID());
                for (int i = 0; i < remaining.size(); i++) {
                    remaining.get(i).setSortOrder(i);
                    itineraryItemDAO.save(remaining.get(i));
                }
                recalculateRoutes(day.getIDID());
            }
        }
    }

    /**
     * 拖曳排序後呼叫: orderedItemIds 是前端拖完之後的新順序 (IIID 陣列)
     */
    public void reorderItems(int IDID, List<Integer> orderedItemIds) {
        revertToDraftIfCompletedByDay(IDID); // 見上方「標記已完成後再編輯要退回草稿」的說明
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
        revertToDraftIfCompleted(ITID); // 見上方「標記已完成後再編輯要退回草稿」的說明
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
                            com.example.travelereasygate.entity.RouteSegment::getTransportMode,
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
    public List<com.example.travelereasygate.entity.Poi> suggestPoiBetween(int AID, int IDID, int fromIIID, int toIIID) {
        ItineraryItem fromItem = itineraryItemDAO.findById(fromIIID);
        ItineraryItem toItem = itineraryItemDAO.findById(toIIID);
        if (fromItem == null || toItem == null) return List.of();

        // 座標來源改成沿用 RouteService.resolveCoordinates() 同一套邏輯 (優先項目自己的座標, 沒有才查
        // 連結的 POI)——原本這裡限定兩邊都要有 PID 才能推薦, 導致「交通」類項目 (沒有連結 POI, 但飛機/
        // 有目的地座標的交通項目本身有自己的經緯度) 兩側的順路推薦永遠不會出現。使用者這次明確要求
        // 「交通跟景點中間還是要有推薦景點」, 這裡放寬成只要任一種方式能拿到座標就可以。
        double[] fromCoord = resolveItemCoordinates(fromItem);
        double[] toCoord = resolveItemCoordinates(toItem);
        if (fromCoord == null || toCoord == null) return List.of();

        double fLat = fromCoord[0], fLng = fromCoord[1];
        double tLat = toCoord[0], tLng = toCoord[1];
        double directKm = haversineKm(fLat, fLng, tLat, tLng);
        final double RATIO_LIMIT = 1.5; // 拉車時間 (或退回時的直線距離) 不能超過直達的 1.5 倍

        // 已經在這天的項目不重複推薦
        java.util.Set<Integer> alreadyInDay = itineraryItemDAO.findByDay(IDID).stream()
                .map(ItineraryItem::getPID).filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        // 候選清單查詢範圍的國家: 交通項目沒有連結 POI、不能再像以前一樣直接拿 fromPoi.getCountry(),
        // 改用跟「交通項目自動查詢地址」同一套 resolveCountryForItem() (優先項目自己判斷出的國家,
        // 沒有才反查行程層級的國家)。
        String country = resolveCountryForItem(fromItem);
        Integer fromPid = fromItem.getPID();
        Integer toPid = toItem.getPID();

        List<com.example.travelereasygate.entity.Poi> roughCandidates = poiDAO.findByAgencyAndCountry(AID, country, null).stream()
                .filter(p -> p.getLatitude() != null)
                .filter(p -> !alreadyInDay.contains(p.getPID()))
                .filter(p -> fromPid == null || p.getPID() != fromPid)
                .filter(p -> toPid == null || p.getPID() != toPid)
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
                    .map(pair -> (com.example.travelereasygate.entity.Poi) pair[0])
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
        revertToDraftIfCompletedByDay(IDID); // 見上方「標記已完成後再編輯要退回草稿」的說明
        routeSegmentDAO.updateTransportMode(RSID, mode);
        // 只重算這一段, 不用整天重算
        routeService.recalculateSingleSegment(RSID, mode);
    }
}