package com.example.UsefulTravel.service;

import com.example.UsefulTravel.DAO.AiImportDAO;
import com.example.UsefulTravel.DAO.CountryCityCodeDAO;
import com.example.UsefulTravel.DAO.ItineraryDAO;
import com.example.UsefulTravel.DAO.ItineraryDayDAO;
import com.example.UsefulTravel.DAO.ItineraryItemDAO;
import com.example.UsefulTravel.DAO.ItineraryItemOptionDAO;
import com.example.UsefulTravel.DAO.PoiDAO;
import com.example.UsefulTravel.DAO.QuotationDAO;
import com.example.UsefulTravel.DAO.QuotationLineDAO;
import com.example.UsefulTravel.DAO.RouteSegmentDAO;
import com.example.UsefulTravel.entity.Itinerary;
import com.example.UsefulTravel.entity.ItineraryDay;
import com.example.UsefulTravel.entity.ItineraryItem;
import com.example.UsefulTravel.entity.ItineraryItemOption;
import com.example.UsefulTravel.entity.Poi;
import com.example.UsefulTravel.entity.Quotation;
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

    @Autowired
    public ItineraryService(ItineraryDAO itineraryDAO, ItineraryDayDAO itineraryDayDAO,
                            ItineraryItemDAO itineraryItemDAO, ItineraryItemOptionDAO itineraryItemOptionDAO,
                            RouteSegmentDAO routeSegmentDAO,
                            RouteService routeService, PoiDAO poiDAO, AiImportDAO aiImportDAO,
                            GoogleMapsClient googleMapsClient, AnthropicClient anthropicClient,
                            ObjectMapper objectMapper, CountryCityCodeDAO countryCityCodeDAO,
                            QuotationDAO quotationDAO, QuotationLineDAO quotationLineDAO) {
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
     */
    public Itinerary createItineraryWithAiPlan(int AID, int createdBy, String title, String country, String region,
                                               int daysCount, LocalDate startDate, List<String> dayCities) {
        Itinerary itinerary = createItinerary(AID, createdBy, title, country, region, daysCount, startDate, dayCities);

        List<Poi> candidates = poiDAO.findByAgencyAndCountry(AID, country, region);
        if (candidates.isEmpty() && region != null && !region.isBlank()) {
            // 地區篩選完全找不到候選景點時, 先退回成只用國家篩選再試一次 ——
            // 使用者選的地區/城市 (例如「佛羅倫斯、威尼斯、比薩、米蘭、羅馬」) 常常跟
            // poi.city 實際存的字串沒辦法字面 exact match 上, 但這個國家在資料庫裡其實有大量景點,
            // 不應該只因為城市名稱顆粒度對不上就整個放棄、建出空白行程。
            candidates = poiDAO.findByAgencyAndCountry(AID, country, null);
        }
        if (candidates.isEmpty()) return itinerary; // 這個國家在資料庫裡完全沒有景點, 保持空白行程讓使用者自己排

        List<ItineraryDay> days = itineraryDayDAO.findByItinerary(itinerary.getITID());

        try {
            // 逐天依「這天指定的城市」把候選清單再篩一次: 沒有指定城市的天 (通常是班機/轉機日) 拿到空清單,
            // AI 跟後面的餐食/住宿補位都會完全跳過這天; 有指定城市的天, 候選只會是「這個城市底下」的景點/
            // 餐廳/飯店, 不會混進別的城市的地點, 徹底解決 Patch 26 之前「餐廳/飯店在天數之間跨城市亂跳」的問題。
            Map<Integer, List<String>> cityTokensByDay = new HashMap<>();
            Map<Integer, List<Poi>> candidatesByDay = new HashMap<>();
            for (ItineraryDay day : days) {
                List<String> tokens = splitCityTokens(day.getPlannedCities());
                cityTokensByDay.put(day.getDayNumber(), tokens);
                candidatesByDay.put(day.getDayNumber(), tokens.isEmpty() ? List.of() : filterByCityTokens(candidates, tokens));
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
                for (Integer pid : pids) {
                    if (!allowedPids.contains(pid)) continue;
                    Poi poi = candidateByPid.get(pid); // AI 幻覺出資料庫沒有的 PID 就直接跳過, 不要硬加
                    if (poi == null) continue;
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

                addBreakfastPlaceholder(day.getIDID());

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
        // (attachFlightLegsAcrossDays 一律用「去程班機」「回程班機」開頭命名, 見 buildFlightLabel)。
        ItineraryItem lastOutboundLeg = null;
        ItineraryItem firstReturnLeg = null;
        for (ItineraryDay day : days) {
            for (ItineraryItem item : itineraryItemDAO.findByDay(day.getIDID())) {
                if (!"transport".equals(item.getItemType()) || !"飛機".equals(item.getTransportMethod())) continue;
                String name = item.getCustomName();
                if (name == null) continue;
                if (name.startsWith("去程班機")) {
                    lastOutboundLeg = item; // 一路覆蓋下去, 掃到最後留下來的就是整趟行程最後一筆去程航段
                } else if (name.startsWith("回程班機") && firstReturnLeg == null) {
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
            if (cities == null || cities.isEmpty()) {
                userContent.append("(未指定城市, 交通/轉機日, candidates=[])\n");
                continue;
            }
            userContent.append("城市=").append(String.join("、", cities)).append(", candidates=[");
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

    /**
     * 個別行程項目「切換某張圖片要不要匯出企劃書」(同一個景點/餐廳可能綁定多張照片)。
     * 使用者要求圖片預設全部輸出，所以這裡存的是「使用者排除掉的圖片」而不是「選了哪幾張」——
     * 沒被排除 (excludedImageIds 沒有這個 IAID) 就會匯出，第一次點某張圖片是把它加進排除清單
     * (從「輸出」變成「不輸出」)，再點一次則是從排除清單移除 (變回「輸出」)。
     */
    public void toggleItemImageExport(int IIID, int IAID) {
        ItineraryItem item = itineraryItemDAO.findById(IIID);
        if (item == null) return;

        java.util.LinkedHashSet<Integer> excluded = new java.util.LinkedHashSet<>(item.getExcludedImageIdSet());
        if (!excluded.remove(IAID)) {
            excluded.add(IAID);
        }
        item.setExcludedImageIds(excluded.isEmpty() ? null
                : excluded.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")));
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
                null, null, null, null, null, null, null, null, null, null);
    }

    /**
     * @param itemType         看板上「編輯」時可以直接更換這個項目的類別 (景點/餐廳/住宿/交通...), 傳 null 或空字串代表不更動
     * @param fromLocation     交通項目專用: 起始點名稱
     * @param fromAddress      交通項目專用: 起始地址; 有填就直接採用, 沒填但有起始點名稱的話後端會自動查詢帶入
     * @param toLocation       交通項目專用: 目的地名稱; 有填會順便重新查詢目的地座標, 讓這個項目在地圖上的位置
     *                         (沿用單一經緯度欄位) 對應到「抵達的目的地」
     * @param toAddress        交通項目專用: 目的地地址; 有填就直接採用, 沒填但有目的地名稱的話後端會自動查詢帶入
     * @param transportMethod  交通項目專用: 交通工具 (高鐵/飛機/遊覽車/渡輪/計程車...)
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
                                  String toLocation, String toAddress, String transportMethod, String commuteDuration,
                                  String startTime, String endTime, Integer commuteDurationMin) {
        ItineraryItem item = itineraryItemDAO.findById(IIID);
        if (item == null) throw new IllegalArgumentException("找不到這個項目");

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
        // 回程班機是唯一的例外: calculateAirportTransferSegments() 建立/補算這個項目的座標時, 刻意存的是
        // 「出發機場」(fromLocation) 不是「目的地」(toLocation)——如果這裡沒有排除, 使用者之後只要編輯這個
        // 項目任何欄位存檔一次 (包含單純按「顯示在地圖上」切換), 就會被這段既有邏輯蓋回抵達機場的座標,
        // 「最後一個景點→出發機場」這段地圖路線就會跟著跑掉。判斷方式跟 calculateAirportTransferSegments()
        // 找「回程班機」用的是同一套 (transportMethod === 飛機 + customName 開頭是「回程班機」)。
        boolean isReturnFlightLeg = "飛機".equals(item.getTransportMethod())
                && item.getCustomName() != null && item.getCustomName().startsWith("回程班機");
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
        java.time.LocalTime breakfastTime = java.time.LocalTime.of(8, 0);
        java.time.LocalTime lunchTime = java.time.LocalTime.of(12, 0);
        java.time.LocalTime dinnerTime = java.time.LocalTime.of(18, 0);

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

        // 早餐固定放最前面, 午餐/晚餐依累計停留時間算出最接近目標時間 (12:00 / 18:00) 的位置插進去。
        // Patch 28: 三餐的 start_time/end_time 同時固定用預設用餐時間錨點 (早餐 08:00、午餐 12:00、
        // 晚餐 18:00) 直接寫死, 不再是 null——使用者反映用餐時間應該要有預設值, 也讓後面
        // hideMealsOverlappingFlights() 可以用這個時間跟班機時間比對是否重疊。
        List<ItineraryItem> arranged = new ArrayList<>(anchors);
        for (ItineraryItem meal : mealsToPlace) {
            int insertIndex;
            java.time.LocalTime target;
            if ("breakfast".equals(meal.getTimeSlot())) {
                insertIndex = 0;
                target = breakfastTime;
            } else {
                target = "lunch".equals(meal.getTimeSlot()) ? lunchTime : dinnerTime;
                insertIndex = findIndexForTargetTime(arranged, dayStart, target);
            }
            int mealDur = meal.getStayDurationMin() != null ? meal.getStayDurationMin() : defaultStayMinutes(meal.getItemType());
            meal.setStartTime(target);
            meal.setEndTime(target.plusMinutes(mealDur));
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

    // Patch 28: 使用者反映 AI 排的行程常常玩到很晚, 這裡在 createItineraryWithAiPlan() 裡整趟排完、
    // autoArrangeItinerary() 也跑完 (每一天的最終順序、三餐固定時間錨點都已經確定) 之後呼叫,
    // 逐天依序累加「目前已排到第幾點」, 只要某個項目 (景點/餐食) 預估開始時間已經超過晚上 20:30,
    // 就直接刪掉這個項目跟它後面所有一樣會更晚開始的項目——確保「每天行程最多只到晚上 8:30」。
    // 住宿/交通 (機場接送/轉機等) 不受這個規則影響 (飯店本來就代表當天結束回房休息, 不管幾點都不會被砍掉),
    // 但還是要照樣累加時間軸, 讓它後面接的項目 (理論上不會有, 但保留以防萬一) 估算時間維持連續。
    private static final java.time.LocalTime DAY_CUTOFF = java.time.LocalTime.of(20, 30);

    private void trimDaysExceedingCutoff(List<ItineraryDay> days) {
        for (ItineraryDay day : days) {
            List<ItineraryItem> items = itineraryItemDAO.findByDay(day.getIDID());
            if (items.isEmpty()) continue;

            java.time.LocalTime dayStart = day.getStartTime() != null ? day.getStartTime() : java.time.LocalTime.of(9, 0);
            java.time.LocalTime current = dayStart;
            for (ItineraryItem item : items) {
                int dur = item.getStayDurationMin() != null ? item.getStayDurationMin() : defaultStayMinutes(item.getItemType());

                if ("hotel".equals(item.getItemType()) || "transport".equals(item.getItemType())) {
                    current = current.plusMinutes(dur);
                    continue;
                }

                // 餐食已經被 autoArrangeDay 設過固定的 start_time (08:00/12:00/18:00), 直接沿用這個時間
                // 而不是用累加估算蓋掉, 才會跟畫面上顯示的用餐時間一致。
                if (item.getStartTime() != null) {
                    current = item.getStartTime();
                }

                if (current.isAfter(DAY_CUTOFF)) {
                    itineraryItemDAO.deleteById(item.getIIID());
                    continue;
                }
                current = current.plusMinutes(dur);
            }
        }
    }

    // Patch 28: 班機時間如果剛好卡到某一餐固定的用餐時間 (例如中午 12 點左右的班機跟午餐重疊), 這餐當天
    // 就不需要再排了 (人已經在機場/飛機上, 排了也不會去吃)——一定要在 attachFlightItems() 把去程/回程班機
    // 轉成 transport 項目「之後」才呼叫這個方法, 不然這天還沒有任何班機項目可以比對。
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
                if (item.getStartTime() == null || item.getEndTime() == null) continue; // 缺時間資訊就跳過, 不要誤刪
                boolean overlaps = flights.stream().anyMatch(f ->
                        timeRangesOverlap(item.getStartTime(), item.getEndTime(), f.getStartTime(), f.getEndTime()));
                if (overlaps) {
                    itineraryItemDAO.deleteById(item.getIIID());
                }
            }
        }
    }

    // 兩個時間區間 [aStart, aEnd) / [bStart, bEnd) 是否有重疊 (前提: 呼叫端已經保證兩邊 start < end)
    private boolean timeRangesOverlap(java.time.LocalTime aStart, java.time.LocalTime aEnd,
                                      java.time.LocalTime bStart, java.time.LocalTime bEnd) {
        return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
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

        List<com.example.UsefulTravel.entity.Poi> roughCandidates = poiDAO.findByAgencyAndCountry(AID, country, null).stream()
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