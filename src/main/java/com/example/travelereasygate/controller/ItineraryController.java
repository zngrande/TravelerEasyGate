package com.example.travelereasygate.controller;

import com.example.travelereasygate.entity.Itinerary;
import com.example.travelereasygate.entity.ItineraryDay;
import com.example.travelereasygate.entity.ItineraryItem;
import com.example.travelereasygate.entity.Poi;
import com.example.travelereasygate.service.GoogleMapsClient;
import com.example.travelereasygate.service.ItineraryService;
import com.example.travelereasygate.service.PermissionService;
import com.example.travelereasygate.service.PoiService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
@RequestMapping("/itinerary")
public class ItineraryController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ItineraryController.class);

    private final ItineraryService itineraryService;
    private final PoiService poiService;
    private final GoogleMapsClient googleMapsClient;
    private final PermissionService permissionService;

    @Autowired
    public ItineraryController(ItineraryService itineraryService, PoiService poiService,
                               GoogleMapsClient googleMapsClient, PermissionService permissionService) {
        this.itineraryService = itineraryService;
        this.poiService = poiService;
        this.googleMapsClient = googleMapsClient;
        this.permissionService = permissionService;
    }

    /**
     * 共用守門邏輯：檢查「這個角色能不能編輯行程」+「這個行程有沒有被鎖定」。
     * 回傳 null 代表可以放行；不是 null 就是要擋下來的錯誤訊息, 呼叫端依自己的回傳型別決定怎麼包裝。
     *
     * 使用者要求「鎖定完不能更改」: 這裡原本只掛在 controller 層級較粗的動作 (完成/刪除/整體自動整理/
     * 日期排序/上鎖/解鎖) 上, 看板上細部的單一景點新增/編輯/刪除等 AJAX 端點完全沒有檢查——這是這次
     * 補上的部分, 見下面每一個 /day/{IDID}/... 端點呼叫的 checkEditPermissionByDay()。
     */
    private String checkEditPermission(HttpSession session, int ITID) {
        Integer AID = (Integer) session.getAttribute("AID");
        Integer UID = (Integer) session.getAttribute("UID");
        String role = (String) session.getAttribute("role");
        if (AID == null || UID == null) return "尚未登入";
        if (!permissionService.canEditItinerary(role)) return "目前帳號角色沒有編輯行程的權限";

        Itinerary itinerary = itineraryService.getItinerary(ITID);
        if (itinerary == null || itinerary.getAID() != AID) return "找不到這個行程";
        if (!itineraryService.isEditableBy(ITID)) return "這個行程目前已鎖定，無法編輯（只有建立者可以解鎖）";
        return null;
    }

    /**
     * 跟 checkEditPermission() 一樣的守門邏輯, 差別是給只帶 IDID (某一天) 的 AJAX 端點用——
     * 這些端點原本沒有 ITID 可以直接查, 先用 getItineraryIdByDay() 反查回所屬的 ITID 再檢查。
     */
    private String checkEditPermissionByDay(HttpSession session, int IDID) {
        Integer ITID = itineraryService.getItineraryIdByDay(IDID);
        if (ITID == null) return "找不到這一天";
        return checkEditPermission(session, ITID);
    }

    // GET /itinerary/new → 建立行程表單
    @GetMapping("/new")
    public String newForm(HttpSession session, Model model) {
        if (session.getAttribute("AID") == null) return "redirect:/login";
        // editMode 一定要明確帶 false (而不是讓它在 model 裡完全不存在), 因為 itinerary/new.html
        // 這份範本被「建立行程」「編輯行程基本資料」共用, 樣板裡到處都會用 ${editMode} 判斷要顯示哪一種
        // 版面 (標題/按鈕/是否隱藏航班區塊等), 沒有明確給值的話, 樣板引擎對 boolean 取反 (th:unless) 遇到
        // null 容易出問題。
        model.addAttribute("editMode", false);
        model.addAttribute("editItineraryId", 0);
        return "itinerary/new";
    }

    // GET /itinerary/{id}/edit-basic → 編輯行程基本資料 (共用「建立新行程」同一份表單, editMode=true)
    // 使用者要求: 名稱/國家/地區/天數/出發日期都要能改, 存檔後不會重新安排行程 (每天已經排好的內容不動),
    // 也不會動到已經加入看板的去程/回程班機項目, 所以這裡不用像 create() 一樣還要處理一大串班機欄位。
    @GetMapping("/{id}/edit-basic")
    public String editBasicForm(@PathVariable("id") int ITID, HttpSession session, Model model,
                                org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        String err = checkEditPermission(session, ITID);
        if (err != null) {
            if (session.getAttribute("AID") == null) return "redirect:/login";
            redirectAttributes.addFlashAttribute("deleteError", err);
            return "redirect:/agency/dashboard";
        }
        Itinerary itinerary = itineraryService.getItinerary(ITID);
        model.addAttribute("editMode", true);
        model.addAttribute("editItineraryId", ITID);
        model.addAttribute("itinerary", itinerary);

        // 使用者要求: 編輯基本資料把天數改少時要真的刪除多出來的天, 送出表單前要先跳出確認對話框告知
        // 「第X天有N個行程項目」。把每一天目前的項目數量整理成 "天數:項目數" 的字串塞進頁面 (格式例如
        // "1:3,2:0,3:5"), 讓前端 JS 不用另外打 API 就能在使用者送出表單當下判斷哪幾天有內容。
        StringBuilder dayItemCounts = new StringBuilder();
        for (com.example.travelereasygate.entity.ItineraryDay day : itineraryService.getDays(ITID)) {
            if (dayItemCounts.length() > 0) dayItemCounts.append(",");
            dayItemCounts.append(day.getDayNumber()).append(":").append(itineraryService.getItems(day.getIDID()).size());
        }
        model.addAttribute("dayItemCounts", dayItemCounts.toString());

        return "itinerary/new";
    }

    // POST /itinerary/{id}/edit-basic → 儲存行程基本資料編輯
    // 天數變多時, ItineraryService.updateBasicInfo() 只會在最後面補空白的新天數, 不會動既有天數的內容;
    // 天數變少或不變則完全不動既有天數 (不刪除), 避免誤刪已經排好的資料。
    @PostMapping("/{id}/edit-basic")
    public String updateBasic(@PathVariable("id") int ITID,
                              @RequestParam String title,
                              @RequestParam String country,
                              @RequestParam(required = false) String region,
                              @RequestParam int daysCount,
                              @RequestParam(required = false) String startDate,
                              HttpSession session,
                              org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        String err = checkEditPermission(session, ITID);
        if (err != null) {
            if (session.getAttribute("AID") == null) return "redirect:/login";
            redirectAttributes.addFlashAttribute("deleteError", err);
            return "redirect:/agency/dashboard";
        }

        LocalDate parsedDate = (startDate != null && !startDate.isBlank()) ? LocalDate.parse(startDate) : null;
        try {
            itineraryService.updateBasicInfo(ITID, title, country, region, daysCount, parsedDate);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("deleteError",
                    "儲存失敗：" + (e.getMessage() != null ? e.getMessage() : e.toString()));
            return "redirect:/itinerary/" + ITID + "/edit-basic";
        }
        return "redirect:/itinerary/" + ITID + "/board";
    }

    // POST /itinerary/new → 建立行程 + 自動產生 Day1~DayN 骨架 (空白行程, 使用者自己手動排)
    // 「行程重點資訊」的去程/回程班機是選填: 有填去程機場/時間就自動建立交通項目放第一天最前面,
    // 有填回程就放最後一天最後面。去程/回程都支援「+新增航段」多筆 (例如轉機), 表單同一個欄位名稱會重複
    // 送出多筆, 用 List 接收, 同一個 index 位置的出發機場/出發時間/抵達機場/抵達時間組成一個航段。
    //
    // Patch 27: 原本自由文字的「行程說明」欄位改成逐天指定城市的下拉選單 (dayCities, 前端依「預計天數」
    // 動態產生, index 0 對應第 1 天、index 1 對應第 2 天...以此類推, 只列出已選的「地區/城市」, 見
    // itinerary/new.html 的 renderDayCitiesRows())。這裡直接原樣轉交給 ItineraryService。
    @PostMapping("/new")
    public String create(@RequestParam String title,
                         @RequestParam String country,
                         @RequestParam(required = false) String region,
                         @RequestParam int daysCount,
                         @RequestParam(required = false) String startDate,
                         @RequestParam(required = false) List<String> dayCities,
                         @RequestParam(required = false) List<String> outFlightNo,
                         @RequestParam(required = false) List<String> outDepAirport,
                         @RequestParam(required = false) List<String> outDepTime,
                         @RequestParam(required = false) List<String> outArrAirport,
                         @RequestParam(required = false) List<String> outArrTime,
                         @RequestParam(required = false) List<String> outDepDay,
                         @RequestParam(required = false) List<String> retFlightNo,
                         @RequestParam(required = false) List<String> retDepAirport,
                         @RequestParam(required = false) List<String> retDepTime,
                         @RequestParam(required = false) List<String> retArrAirport,
                         @RequestParam(required = false) List<String> retArrTime,
                         @RequestParam(required = false) List<String> retDepDay,
                         HttpSession session) {
        Integer AID = (Integer) session.getAttribute("AID");
        Integer UID = (Integer) session.getAttribute("UID");
        if (AID == null || UID == null) return "redirect:/login";

        LocalDate parsedDate = (startDate != null && !startDate.isBlank()) ? LocalDate.parse(startDate) : null;
        Itinerary itinerary = itineraryService.createItinerary(AID, UID, title, country, region, daysCount, parsedDate, dayCities);
        // Patch 76: 使用者反映「AI排行程無視飛機時間, 時間表錯亂」——追查過程中發現這四步 (插入去程/回程
        // 班機、隱藏跟班機時間重疊的餐食、清掉班機時間前後不合理的項目、算機場銜接路線的拉車距離) 原本
        // 全部包在同一個 try-catch 裡: 只要中間任何一步丟出例外 (例如 hideMealsOverlappingFlights 查
        // 資料庫瞬斷), 後面幾步 (包含最關鍵的 trimItemsAroundFlights() 清理不合理項目) 會全部被這一個
        // catch 攔截、直接跳過, 完全不會執行——這正好可以解釋「班機資訊確實有插進去 (attachFlightItems
        // 顯然成功了), 但排在班機前後不合理的舊景點/餐廳時間卻完全沒被清掉」這種部分失敗的狀態。改成
        // 每一步各自獨立包一層 try-catch: 前面某一步失敗不會連帶擋住後面幾步各自的清理機會, 盡量把行程
        // 修到最乾淨的狀態, 不要因為一步失敗就整批放棄。
        try {
            itineraryService.attachFlightItems(itinerary.getITID(),
                    outFlightNo, outDepAirport, outDepTime, outArrAirport, outArrTime, outDepDay,
                    retFlightNo, retDepAirport, retDepTime, retArrAirport, retArrTime, retDepDay);
        } catch (Exception e) {
            LOGGER.warn("建立行程後補插入去程/回程班機失敗 (ITID={}): {}", itinerary.getITID(), e.toString(), e);
        }
        try {
            // Patch 28: 班機時間如果剛好卡到某一餐固定的用餐時間, 這餐就不需要呈現——一定要在班機轉成
            // transport 項目之後才呼叫, 見 ItineraryService.hideMealsOverlappingFlights() 說明。
            itineraryService.hideMealsOverlappingFlights(itinerary.getITID());
        } catch (Exception e) {
            LOGGER.warn("建立行程後隱藏跟班機時間重疊的餐食失敗 (ITID={}): {}", itinerary.getITID(), e.toString(), e);
        }
        try {
            // 使用者反映「回程班機中午就起飛, 但行程表還排了傍晚的行程/晚餐」——這裡另外補一層更全面的
            // 檢查, 把「去程班機還沒降落 (含 90 分鐘通勤緩衝)」跟「回程班機出發前預留時間之後」這兩段
            // 時間範圍內的景點/餐廳全部清掉, 不是只清跟班機時間字面重疊的那幾筆, 見
            // ItineraryService.trimItemsAroundFlights() 說明。一樣要在 attachFlightItems()/
            // hideMealsOverlappingFlights() 之後才呼叫, 但即使前面兩步其中之一失敗, 這步還是要嘗試執行。
            itineraryService.trimItemsAroundFlights(itinerary.getITID());
        } catch (Exception e) {
            LOGGER.warn("建立行程後清理班機時間前後不合理項目失敗 (ITID={}): {}", itinerary.getITID(), e.toString(), e);
        }
        try {
            // Patch 78: 使用者反映「之前設定最後行程時間應該是 20:30, 但直接排到凌晨 4 點」——
            // trimItemsAroundFlights() 把班機前後不合理的項目清掉之後, 剩下的景點/餐食估算時間可能因為
            // 「去程班機降落 + 90 分鐘緩衝」往後推移, 一定要在它之後重新檢查一次每天是否還是超過晚上
            // 20:30, 見 ItineraryService.trimDaysExceedingCutoff() 說明。
            itineraryService.trimDaysExceedingCutoff(itinerary.getITID());
        } catch (Exception e) {
            LOGGER.warn("建立行程後裁剪超過晚間截止時間的項目失敗 (ITID={}): {}", itinerary.getITID(), e.toString(), e);
        }
        try {
            // 去程/回程機場銜接的拉車距離/時間——理想上要在上面呼叫都跑完之後才算, 見
            // ItineraryService.calculateAirportTransferSegments() 說明; 即使前面幾步有失敗, board()
            // 開啟看板時也會自動補跑一次這幾步 (見該方法註解), 這裡失敗一樣不讓「建立行程」整個失敗。
            itineraryService.calculateAirportTransferSegments(itinerary.getITID());
        } catch (Exception e) {
            // 使用者反映「建立行程/AI安排行程」偶爾會直接跳「系統發生錯誤」畫面——這幾步都要打
            // Google Maps API, 原本完全沒有防護, 任何一步丟出例外都會讓整個 request 被
            // GlobalExceptionHandler 攔截、直接顯示系統錯誤頁——但這時候 createItinerary() 早就已經成功
            // 寫入資料庫, 使用者反而會看到一個更混亂的狀態: 畫面顯示系統錯誤, 但行程其實已經建立好了。
            // 改成: 這幾步都不應該讓「建立行程」整個失敗, 失敗就跳過、留 log, 讓使用者至少能正常導到
            // 看板頁面——calculateAirportTransferSegments() 在 board() 裡本來就會自動補跑一次
            // (見該方法註解), 之後重新整理看板頁通常就會自動補上。
            LOGGER.warn("建立行程後計算機場銜接路線失敗 (ITID={}): {}", itinerary.getITID(), e.toString(), e);
        }
        return "redirect:/itinerary/" + itinerary.getITID() + "/board";
    }

    // POST /itinerary/new/ai → 「AI 安排行程」按鈕: 建立行程骨架後, 用 AI 從公司 POI 資料庫裡挑選/安排每一天的行程,
    // 不是空白行程。跟旁邊「建立行程並進入看板」共用同一組表單欄位, 只是多這個按鈕會多跑一次 AI 排程。
    @PostMapping("/new/ai")
    public String createWithAiPlan(@RequestParam String title,
                                   @RequestParam String country,
                                   @RequestParam(required = false) String region,
                                   @RequestParam int daysCount,
                                   @RequestParam(required = false) String startDate,
                                   @RequestParam(required = false) List<String> dayCities,
                                   @RequestParam(required = false) List<String> outFlightNo,
                                   @RequestParam(required = false) List<String> outDepAirport,
                                   @RequestParam(required = false) List<String> outDepTime,
                                   @RequestParam(required = false) List<String> outArrAirport,
                                   @RequestParam(required = false) List<String> outArrTime,
                                   @RequestParam(required = false) List<String> outDepDay,
                                   @RequestParam(required = false) List<String> retFlightNo,
                                   @RequestParam(required = false) List<String> retDepAirport,
                                   @RequestParam(required = false) List<String> retDepTime,
                                   @RequestParam(required = false) List<String> retArrAirport,
                                   @RequestParam(required = false) List<String> retArrTime,
                                   @RequestParam(required = false) List<String> retDepDay,
                                   HttpSession session,
                                   org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        Integer AID = (Integer) session.getAttribute("AID");
        Integer UID = (Integer) session.getAttribute("UID");
        if (AID == null || UID == null) return "redirect:/login";

        LocalDate parsedDate = (startDate != null && !startDate.isBlank()) ? LocalDate.parse(startDate) : null;
        // dayCities 逐天指定城市 (見上面 create() 的說明) 取代了原本的「行程說明」自由文字, 沒有指定城市的天
        // (前端只會讓去程班機最後一天/回程班機第一天可以選, 其餘班機/轉機日完全不會送出城市) 在 Service 裡
        // 會被當成交通/轉機日, AI 排程完全跳過那一天, 不會再被誤排進一整天觀光行程。
        //
        // 使用者反映「沒填逐天城市指定, AI 排不出東西, 這應該要是選填才對」: 這裡先用跟 attachFlightItems()
        // 完全一樣的班機/轉機日期資訊算出真正的班機/轉機日 (flightDayNumbers), 傳給 createItineraryWithAiPlan()
        // 分辨「沒填城市」到底是真正的班機日、還是使用者自己選擇不填的一般日期——一般日期沒填城市會退回整個
        // 國家/地區的候選景點, 不再整天排不出任何東西 (見 ItineraryService 的說明)。
        Set<Integer> flightDayNumbers = itineraryService.computeFlightDayNumbers(daysCount,
                outFlightNo, outDepAirport, outDepTime, outArrAirport, outArrTime, outDepDay,
                retFlightNo, retDepAirport, retDepTime, retArrAirport, retArrTime, retDepDay);
        Itinerary itinerary = itineraryService.createItineraryWithAiPlan(AID, UID, title, country, region, daysCount, parsedDate, dayCities, flightDayNumbers);

        // 這個提示是「AI 有沒有真的排到景點資料庫裡的東西」, 一定要在插入去程/回程班機之前判斷 ——
        // 不然只要有填班機資訊, hasAnyItem() 就會一直是 true (班機本身也算一筆項目), 提示永遠不會跳出來,
        // 使用者反而不知道 AI 其實沒排到任何真正的景點。
        boolean aiFoundNothing = !itineraryService.hasAnyItem(itinerary.getITID());

        // Patch 76: 使用者反映「AI排行程無視飛機時間, 時間表錯亂」——這裡原本跟 create() 一樣, 四步全部
        // 包在同一個 try-catch 裡, 中間任何一步失敗會連帶讓後面關鍵的 trimItemsAroundFlights() 清理
        // 完全沒機會執行, 導致「班機成功插入、但排在班機前後不合理的 AI 舊排程完全沒被清掉」的部分失敗
        // 狀態。改成每一步各自獨立包一層 try-catch, 見 create() 端點同樣的說明。
        try {
            // 一定要等 createItineraryWithAiPlan() 內部的自動整理 (meal_time) 全部跑完才能插入去程/回程班機,
            // 不然剛插好的「第一筆/最後一筆」會被自動整理重新洗牌 (見 ItineraryService.attachFlightItems 說明)
            itineraryService.attachFlightItems(itinerary.getITID(),
                    outFlightNo, outDepAirport, outDepTime, outArrAirport, outArrTime, outDepDay,
                    retFlightNo, retDepAirport, retDepTime, retArrAirport, retArrTime, retDepDay);
        } catch (Exception e) {
            LOGGER.warn("AI 安排行程後補插入去程/回程班機失敗 (ITID={}): {}", itinerary.getITID(), e.toString(), e);
        }
        try {
            // Patch 28: 班機時間如果剛好卡到某一餐固定的用餐時間, 這餐就不需要呈現——一定要在班機轉成
            // transport 項目之後才呼叫, 見 ItineraryService.hideMealsOverlappingFlights() 說明。
            itineraryService.hideMealsOverlappingFlights(itinerary.getITID());
        } catch (Exception e) {
            LOGGER.warn("AI 安排行程後隱藏跟班機時間重疊的餐食失敗 (ITID={}): {}", itinerary.getITID(), e.toString(), e);
        }
        try {
            // 使用者反映「AI 排的行程, 回程班機中午就起飛, 但當天還是排了整天行程、傍晚還有晚餐, 時間對
            // 不上」——這裡另外補一層更全面的檢查, 把「去程班機還沒降落 (含 90 分鐘通勤緩衝)」跟「回程
            // 班機出發前預留時間之後」這兩段時間範圍內 AI 排進去的景點/餐廳全部清掉, 不是只清跟班機時間
            // 字面重疊的那幾筆, 見 ItineraryService.trimItemsAroundFlights() 說明。一樣要在
            // attachFlightItems()/hideMealsOverlappingFlights() 之後才呼叫, 但即使前面兩步其中之一失敗,
            // 這步還是要嘗試執行——這正是這次修正最關鍵的一步, 不能被前面的失敗連帶擋住。
            itineraryService.trimItemsAroundFlights(itinerary.getITID());
        } catch (Exception e) {
            LOGGER.warn("AI 安排行程後清理班機時間前後不合理項目失敗 (ITID={}): {}", itinerary.getITID(), e.toString(), e);
        }
        try {
            // Patch 78: 使用者反映「之前設定最後行程時間應該是 20:30, 但直接排到凌晨 4 點」——
            // trimItemsAroundFlights() 把班機前後不合理的項目清掉之後, 剩下的景點/餐食估算時間可能因為
            // 「去程班機降落 + 90 分鐘緩衝」往後推移, 一定要在它之後重新檢查一次每天是否還是超過晚上
            // 20:30, 見 ItineraryService.trimDaysExceedingCutoff() 說明。
            itineraryService.trimDaysExceedingCutoff(itinerary.getITID());
        } catch (Exception e) {
            LOGGER.warn("AI 安排行程後裁剪超過晚間截止時間的項目失敗 (ITID={}): {}", itinerary.getITID(), e.toString(), e);
        }
        try {
            // 去程/回程機場銜接的拉車距離/時間——理想上要在上面呼叫都跑完之後才算, 見
            // ItineraryService.calculateAirportTransferSegments() 說明。
            itineraryService.calculateAirportTransferSegments(itinerary.getITID());
        } catch (Exception e) {
            // 使用者反映「AI安排行程報錯」(畫面直接跳「系統發生錯誤」, 不是回到看板頁看到提示訊息)——
            // calculateAirportTransferSegments() 要打 Google Maps API, 原本完全沒有防護, 丟出例外會讓
            // 整個 request 被 GlobalExceptionHandler 攔截、直接顯示系統錯誤頁——但 createItineraryWithAiPlan()
            // 這時候早就已經成功建立好行程 (可能還排好了 AI 選的景點), 使用者反而會看到「畫面說系統錯誤,
            // 但重新整理/回列表卻發現行程其實已經建立好了」這種更混亂的狀態。改成: 失敗就跳過、留 log,
            // 讓使用者至少能正常進入看板看到 AI 已經排好的內容——calculateAirportTransferSegments() 在
            // board() 裡本來就會自動補跑一次 (見該方法註解), 之後重新整理看板頁通常就會自動補上機場銜接路線。
            LOGGER.warn("AI 安排行程後計算機場銜接路線失敗 (ITID={}): {}", itinerary.getITID(), e.toString(), e);
        }

        if (aiFoundNothing) {
            // Patch 82: 原本這裡不管實際原因是什麼都顯示同一句籠統訊息, 使用者沒辦法自己判斷是資料問題
            // 還是 AI 呼叫問題, 每次都要另外要求對方去撈伺服器 log——改成呼叫 diagnoseAiPlanEmptyReason()
            // 直接把明確原因顯示在畫面上, 見該方法說明。這段診斷查詢失敗不應該讓使用者連結果都看不到,
            // 所以額外包一層防護, 失敗就退回原本的籠統訊息。
            String reason;
            try {
                reason = itineraryService.diagnoseAiPlanEmptyReason(AID, country, region);
            } catch (Exception e) {
                LOGGER.warn("AI 安排行程：產生空白行程原因說明時失敗 (ITID={}): {}", itinerary.getITID(), e.toString(), e);
                reason = "AI 沒有找到「" + country + (region != null && !region.isBlank() ? " / " + region : "")
                        + "」符合的景點資料 (或 AI 排程失敗)。";
            }
            redirectAttributes.addFlashAttribute("aiPlanNotice", reason + " 已建立空白行程, 請從左側手動加入景點。");
        }
        return "redirect:/itinerary/" + itinerary.getITID() + "/board";
    }

    // GET /itinerary/{id}/board → 積木式拖曳排版看板 (核心畫面)
    @GetMapping("/{id}/board")
    public String board(@PathVariable("id") int ITID, HttpSession session, Model model) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return "redirect:/login";

        Itinerary itinerary = itineraryService.getItinerary(ITID);
        List<ItineraryDay> days = itineraryService.getDays(ITID);
        // 補跑一次去程/回程機場銜接的拉車距離/地圖路線——calculateAirportTransferSegments() 內部有做過
        // 判斷 (座標已經算過、showOnMap 也已經是 true 就直接跳過), 所以這個 patch 上線之前就已經建立好的
        // 舊行程, 只要重新整理一次看板頁就會自動補上這個功能, 不需要重新建立行程; 已經處理過的行程再次
        // 打開看板不會重打 Google API, 不用擔心效能/費用問題。
        //
        // 使用者反映「打開行程排版看板整頁空白（只剩頂部導覽列跟 Day 分頁, 下面完全沒有內容）」——追查後
        // 發現這一行原本完全沒有防護, 而且是整個 board() 方法裡「查完 itinerary/days 之後、往 Model 塞
        // 任何屬性之前」唯一一段還會再去查資料庫、還會打 Google Maps API 的地方 (跟 create()/
        // createWithAiPlan() 剛補防護的那三步是同一批新功能、同一個 commit 一起上線的, 見那邊的註解)。
        // 這個方法在每次打開看板頁時都會執行 (不是只有第一次), 只要這裡任何一次丟出例外 (例如某個舊行程
        // 的機場文字剛好讓地理定位/距離矩陣 API 回應格式跑掉、API key 額度用完、或資料庫瞬斷), 整個
        // request 就會在 Model 屬性都還沒設定、Thymeleaf 樣板都還沒開始渲染之前被攔截丟出——這個 sandbox
        // 沒有辦法連上使用者實際的 MySQL/Google Maps 服務重現, 沒辦法 100% 斷定這就是使用者這次看到的
        // 「只剩導覽列/Day分頁, 下面空白」那個畫面的唯一成因, 但這確實是目前唯一一處「打開看板」這個原本
        // 應該是純顯示、不該失敗的動作, 卻完全沒有防護、會被這批新功能的外部 API 依賴拖累失敗的地方,
        // 值得先補起來——跟 create()/createWithAiPlan() 那邊剛做的防護邏輯一致: 失敗就記錄 log、跳過
        // 這一步, 不要讓整個看板頁打不開。
        // Patch 88: 使用者反映「行程時間不用限制最晚時間（自動安排行程再限制 但還是能自己加行程）」
        // 以及「有時候儲存會把行程刪除 或是行程內資料刪除 不知道為什麼」——這裡是同一個根因的另一處
        // (見 ItineraryService.getItems() 那邊 Patch 88 的完整說明): 每次打開看板頁都無條件重新套用
        // 「回程班機前 90 分鐘」「每天最晚 20:30」這兩個限制、直接刪除卡在範圍內的項目, 不管那是 AI 排的
        // 還是使用者剛手動加上去的——這正是使用者反映「儲存/整理畫面之後行程自己被刪掉一部分」的根因
        // 之一。拿掉這裡的自我修復; 這兩個方法依然保留, 呼叫責任收回到 createWithAiPlan()/
        // createItineraryWithAiPlan() (建立行程時的 AI 初稿) 以及 /day/{IDID}/auto-arrange、
        // /{id}/auto-arrange (看板上「自動整理」按鈕) 這幾個明確代表「使用者主動要求自動安排」的地方,
        // 不會再靠「打開看板」這種單純瀏覽動作觸發刪除。
        try {
            itineraryService.calculateAirportTransferSegments(ITID);
        } catch (Exception e) {
            LOGGER.warn("開啟看板時補算機場銜接路線失敗, 已略過 (ITID={}): {}", ITID, e.toString(), e);
        }
        model.addAttribute("itineraryId", ITID);
        model.addAttribute("itinerary", itinerary);
        model.addAttribute("days", days);

        // 左側資料庫依這個行程的國家/地區自動篩選相關景點, 而不是列出旅行社所有國家的資料。
        // 行程標題上的「國家」欄位是線控自己打的單一欄位, 多國行程常常會填「日本、泰國」這種合併字串,
        // 所以這裡再彙整每一天、每個項目自己 AI 判斷出來的國家 (item_country, 比較精確), 兩邊聯集起來
        // 一起丟給 PoiDAO 篩選 (PoiDAO 那邊會再拆解、用 IN 比對), 才不會因為合併字串 exact match 不到而整包篩不出東西。
        java.util.LinkedHashSet<String> countrySet = new java.util.LinkedHashSet<>();
        if (itinerary != null && itinerary.getCountry() != null && !itinerary.getCountry().isBlank()) {
            for (String token : itinerary.getCountry().split("[、,，/|]")) {
                if (!token.trim().isEmpty()) countrySet.add(token.trim());
            }
        }
        for (ItineraryDay day : days) {
            for (ItineraryItem item : itineraryService.getItems(day.getIDID())) {
                if (item.getItemCountry() != null && !item.getItemCountry().isBlank()) {
                    countrySet.add(item.getItemCountry().trim());
                }
            }
        }
        String mergedCountries = String.join("、", countrySet);
        // 多國行程時「地區」通常只對應某一國, 混進多國查詢容易誤篩, 交給 PoiDAO 自行判斷是否要套用
        String regionFilter = itinerary != null ? itinerary.getRegion() : null;

        model.addAttribute("poiList", poiService.listForItinerary(AID, mergedCountries, regionFilter));
        // 給前端畫「國家篩選標籤」用: 這個行程目前橫跨哪些國家 (只有 2 個以上才需要顯示切換標籤)
        model.addAttribute("itineraryCountries", new ArrayList<>(countrySet));
        model.addAttribute("googleMapsConfigured", googleMapsClient.isConfigured());
        model.addAttribute("googleMapsApiKey", googleMapsClient.getApiKey());

        // 上鎖狀態: 給看板頂部顯示鎖定提示 + 決定要不要停用編輯按鈕用。
        // 使用者要求「只有建立者可以鎖定/解鎖，鎖定完不能更改」——isCreator 決定「鎖定/解鎖」按鈕
        // 要不要顯示 (只有建立者看得到), canEditItinerary 決定所有編輯用的按鈕/欄位要不要停用
        // (鎖定後不管是不是建立者本人都要停用, 跟舊版「上鎖的人自己還可以編輯」不一樣)。
        Integer UID = (Integer) session.getAttribute("UID");
        String role = (String) session.getAttribute("role");
        boolean itineraryLocked = itinerary != null && itinerary.isLocked();
        boolean isCreator = itinerary != null && UID != null && itinerary.getCreatedBy() == UID;
        model.addAttribute("itineraryLocked", itineraryLocked);
        model.addAttribute("isCreator", isCreator);
        model.addAttribute("canEditItinerary", permissionService.canEditItinerary(role) && !itineraryLocked);
        model.addAttribute("canQuote", permissionService.canQuote(role));

        return "itinerary/board";
    }

    // POST /itinerary/{id}/lock → 上鎖。
    // 使用者要求「只有創建行程的使用者（建立者）可以鎖定跟解鎖」——原本是「任何有編輯權限的人都能
    // 上鎖」(協作防呆用途), 改成只認 Itinerary.createdBy, 跟角色權限無關 (即使建立者的角色權限被降級,
    // 本人一樣可以鎖定/解鎖自己建立的行程)。不能再沿用 checkEditPermission() (那支會連同
    // isEditableBy() 一起檢查「已鎖定就擋下」, 但「解鎖」這個動作本來就該在已鎖定的狀態下才會被呼叫,
    // 用那支會變成鎖上以後永遠解不開), 這裡直接另外寫身分檢查。
    @PostMapping("/{id}/lock")
    @ResponseBody
    public ResponseEntity<?> lock(@PathVariable("id") int ITID, HttpSession session) {
        Integer AID = (Integer) session.getAttribute("AID");
        Integer UID = (Integer) session.getAttribute("UID");
        if (AID == null || UID == null) return ResponseEntity.status(401).body("尚未登入");

        Itinerary itinerary = itineraryService.getItinerary(ITID);
        if (itinerary == null || itinerary.getAID() != AID) return ResponseEntity.status(404).body("找不到這個行程");
        if (itinerary.getCreatedBy() != UID) return ResponseEntity.status(403).body("只有建立這個行程的使用者可以鎖定行程");

        boolean ok = itineraryService.lockItinerary(ITID, UID);
        return ok ? ResponseEntity.ok().build() : ResponseEntity.status(409).body("鎖定失敗，請稍後再試");
    }

    // POST /itinerary/{id}/unlock → 解鎖, 同樣只有建立者可以操作 (見上面 /lock 的說明)。
    @PostMapping("/{id}/unlock")
    @ResponseBody
    public ResponseEntity<?> unlock(@PathVariable("id") int ITID, HttpSession session) {
        Integer AID = (Integer) session.getAttribute("AID");
        Integer UID = (Integer) session.getAttribute("UID");
        if (AID == null || UID == null) return ResponseEntity.status(401).body("尚未登入");

        Itinerary itinerary = itineraryService.getItinerary(ITID);
        if (itinerary == null || itinerary.getAID() != AID) return ResponseEntity.status(404).body("找不到這個行程");
        if (itinerary.getCreatedBy() != UID) return ResponseEntity.status(403).body("只有建立這個行程的使用者可以解鎖行程");

        itineraryService.unlockItinerary(ITID);
        return ResponseEntity.ok().build();
    }

    // POST /itinerary/{id}/complete → 行程排版看板 or 首頁列表按下「完成行程」, 狀態改成 completed
    // (首頁「進行中行程」變成「已完成行程」)。跟 delete 用同樣的寫法直接 redirect 回首頁, 而不是回空的
    // ResponseEntity —— 因為首頁的按鈕是一般 HTML form 送出 (非 AJAX), 回空白 response 瀏覽器會整頁跳轉
    // 到一片空白, 讓人以為「跳到其他網頁但沒有動作」。改成 redirect 後, 首頁 form 送出會直接導回首頁看到最新狀態；
    // board.html 那邊用 fetch() 呼叫時, fetch 會自動跟隨 redirect 拿到最終的 200 回應, 不影響原本的 AJAX 邏輯。
    @PostMapping("/{id}/complete")
    public String complete(@PathVariable("id") int ITID, HttpSession session,
                           org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        String err = checkEditPermission(session, ITID);
        if (err != null) {
            if (session.getAttribute("AID") == null) return "redirect:/login";
            redirectAttributes.addFlashAttribute("deleteError", err);
            return "redirect:/agency/dashboard";
        }
        try {
            itineraryService.markCompleted(ITID);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("deleteError",
                    "標記完成失敗：" + (e.getMessage() != null ? e.getMessage() : e.toString()));
        }
        return "redirect:/agency/dashboard";
    }

    // POST /itinerary/{id}/revert-to-draft → 看板上「完成行程」按鈕在行程已經是 completed 狀態時會變成
    // 「退回草稿」, 按下去呼叫這支 API 把狀態改回 draft。跟上面 /complete 不同的地方: 這裡用 @ResponseBody
    // 直接回 200/錯誤訊息 (不像 /complete 是走 redirect 回首頁列表那一套), 因為使用者要求「退回草稿時要
    // 停留在編輯行程頁面」——board.html 那邊拿到成功回應後只會重新整理「這一頁」(看板頁本身), 不會被導去
    // 別的頁面, 用 redirect 反而不好處理 (form submit 才需要 redirect 到看得到結果的頁面)。
    @PostMapping("/{id}/revert-to-draft")
    @ResponseBody
    public ResponseEntity<?> revertToDraft(@PathVariable("id") int ITID, HttpSession session) {
        String err = checkEditPermission(session, ITID);
        if (err != null) return ResponseEntity.status(403).body(err);
        try {
            itineraryService.revertToDraft(ITID);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }

    // POST /itinerary/{id}/title → 看板標題點兩下就地編輯 (Patch 89), 跟上面 /revert-to-draft 同一種
    // @ResponseBody AJAX 端點, 存完只在原地刷新標題文字, 不會整頁重載或跳轉頁面。
    @PostMapping("/{id}/title")
    @ResponseBody
    public ResponseEntity<?> updateTitle(@PathVariable("id") int ITID, @RequestParam String title, HttpSession session) {
        String err = checkEditPermission(session, ITID);
        if (err != null) return ResponseEntity.status(403).body(err);
        try {
            itineraryService.updateTitle(ITID, title);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }

    // DELETE /itinerary/day/{IDID} → 刪除整天 (Day 分頁旁邊的刪除按鈕), 後面的天數會自動往前遞補一位
    // 使用者要求「行程鎖定後不能再編輯」——這支端點原本完全沒有檢查, 補上 checkEditPermissionByDay()。
    @DeleteMapping("/day/{IDID}")
    @ResponseBody
    public ResponseEntity<?> deleteDay(@PathVariable int IDID, HttpSession session) {
        String err = checkEditPermissionByDay(session, IDID);
        if (err != null) return ResponseEntity.status(403).body(err);
        itineraryService.deleteDay(IDID);
        return ResponseEntity.ok().build();
    }

    // POST /itinerary/{id}/add-day → 看板天數旁邊「+」直接加一天空白天 (加在最後面)
    @PostMapping("/{id}/add-day")
    @ResponseBody
    public ResponseEntity<?> addDay(@PathVariable("id") int ITID, HttpSession session) {
        String err = checkEditPermission(session, ITID);
        if (err != null) return ResponseEntity.status(403).body(err);
        itineraryService.addBlankDay(ITID);
        return ResponseEntity.ok().build();
    }

    // POST /itinerary/{id}/duplicate → 首頁「複製行程」按鈕: 整份行程 (含每天/每個項目/拉車距離/報價元件)
    // 複製成一份全新草稿, 常用於「同一條路線, 下一團客人只是日期/人數不同」不用重新排一次
    @PostMapping("/{id}/duplicate")
    public String duplicate(@PathVariable("id") int ITID, HttpSession session,
                            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        Integer AID = (Integer) session.getAttribute("AID");
        Integer UID = (Integer) session.getAttribute("UID");
        String role = (String) session.getAttribute("role");
        if (AID == null || UID == null) return "redirect:/login";
        if (!permissionService.canEditItinerary(role)) {
            redirectAttributes.addFlashAttribute("deleteError", "目前帳號角色沒有建立行程的權限");
            return "redirect:/agency/dashboard";
        }
        Itinerary itinerary = itineraryService.getItinerary(ITID);
        if (itinerary == null || itinerary.getAID() != AID) {
            redirectAttributes.addFlashAttribute("deleteError", "找不到這個行程");
            return "redirect:/agency/dashboard";
        }
        try {
            itineraryService.duplicateItinerary(ITID, UID);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("deleteError",
                    "複製失敗：" + (e.getMessage() != null ? e.getMessage() : e.toString()));
        }
        return "redirect:/agency/dashboard";
    }

    // POST /itinerary/{id}/pin → 首頁「釘選」切換 (像 LINE 聊天列表往右滑釘選), 回傳切換後的狀態給前端更新畫面
    @PostMapping("/{id}/pin")
    @ResponseBody
    public ResponseEntity<?> togglePin(@PathVariable("id") int ITID, HttpSession session) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return ResponseEntity.status(401).build();
        Itinerary itinerary = itineraryService.getItinerary(ITID);
        if (itinerary == null || itinerary.getAID() != AID) return ResponseEntity.status(404).build();

        boolean pinned = itineraryService.togglePin(ITID);
        Map<String, Object> body = new HashMap<>();
        body.put("pinned", pinned);
        return ResponseEntity.ok(body);
    }

    // POST /itinerary/{id}/delete → 刪除整個行程
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable("id") int ITID, HttpSession session, org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        String err = checkEditPermission(session, ITID);
        if (err != null) {
            if (session.getAttribute("AID") == null) return "redirect:/login";
            redirectAttributes.addFlashAttribute("deleteError", err);
            return "redirect:/agency/dashboard";
        }
        try {
            itineraryService.deleteItinerary(ITID);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("deleteError",
                    "刪除失敗：" + (e.getMessage() != null ? e.getMessage() : e.toString()));
        }
        return "redirect:/agency/dashboard";
    }

    // GET /itinerary/day/{IDID}/items → 取某一天的行程項目 (給前端拖曳元件用的資料 API)
    //
    // Patch 77/78 原本在這裡補了自我修復呼叫 (trimItemsAroundFlights()/trimDaysExceedingCutoff()),
    // 因為這支才是 board.html 前端實際拿資料的來源 (見那兩次的說明)。但 patch 79 發現 Word 匯出
    // (ExportService)、報價單 (QuotationController)、企劃書合併 (TemplateMergeService) 都是直接
    // 呼叫 ItineraryService.getItems(IDID) 這個 Service 方法本身, 完全繞過這支 Controller——一樣的
    // 清理邏輯已經因為「補在某一個呼叫端, 其他呼叫端繞過去」重演過兩次, 這次乾脆把自我修復移到
    // ItineraryService.getItems() 本身 (所有呼叫端共用的最底層), 這支 Controller 端點只是單純轉呼叫,
    // 不用再自己重複一份一樣的清理邏輯。
    @GetMapping("/day/{IDID}/items")
    @ResponseBody
    public List<ItineraryItem> getItems(@PathVariable int IDID) {
        return itineraryService.getItems(IDID);
    }

    // POST /itinerary/day/{IDID}/items → 把景點/餐廳/自訂項目加入某一天
    // 使用者要求「行程鎖定後不能再編輯」——這支端點原本完全沒有檢查, 補上 checkEditPermissionByDay();
    // 擋下時回傳純文字錯誤訊息 (不是 ItineraryItem), 前端 (board.html 的 addToCurrentDay()/
    // addSuggestedBetween()) 已經同步補上先檢查 res.ok 才決定要不要當 JSON 解析。
    @PostMapping("/day/{IDID}/items")
    @ResponseBody
    public ResponseEntity<?> addItem(@PathVariable int IDID,
                                     @RequestParam(required = false) Integer PID,
                                     @RequestParam String itemType,
                                     @RequestParam(required = false) String customName,
                                     HttpSession session) {
        String err = checkEditPermissionByDay(session, IDID);
        if (err != null) return ResponseEntity.status(403).body(err);
        return ResponseEntity.ok(itineraryService.addItem(IDID, PID, itemType, customName));
    }

    // POST /itinerary/day/{IDID}/items/custom → 新增自訂項目 (不連結 POI 資料庫, 但會自動地理編碼)
    // rawName 可以用「或」分隔多個候選點, 例如「花蓮翰品酒店或día酒店或某某民宿」
    // locationHint 選填: 貼 Google 地圖網址或地址, 定位比純打名稱準確
    // 使用者要求「行程鎖定後不能再編輯」——同上補上 checkEditPermissionByDay(), 前端 addCustomItem()
    // 已經同步補上先檢查 res.ok。
    @PostMapping("/day/{IDID}/items/custom")
    @ResponseBody
    public ResponseEntity<?> addCustomItem(@PathVariable int IDID,
                                           @RequestParam String itemType,
                                           @RequestParam String rawName,
                                           @RequestParam(required = false) Integer stayDurationMin,
                                           @RequestParam(required = false) String locationHint,
                                           HttpSession session) {
        String err = checkEditPermissionByDay(session, IDID);
        if (err != null) return ResponseEntity.status(403).body(err);
        return ResponseEntity.ok(itineraryService.addCustomItem(IDID, itemType, rawName, stayDurationMin, locationHint));
    }

    // POST /itinerary/day/{IDID}/items/{IIID}/edit → 編輯看板上已存在的項目
    // (名稱/停留時間/時段/備註/地圖顯示/重新定位/更換類別, 以及交通類別專用的起始點/起始地址/目的地/目的地地址/交通工具/通勤時間)
    // 使用者要求「行程鎖定後不能再編輯」——這支端點原本完全沒有檢查, 補上 checkEditPermissionByDay();
    // 呼叫端 (saveAllChanges()) 本來就會檢查 res.ok, 不用另外改前端。
    @PostMapping("/day/{IDID}/items/{IIID}/edit")
    @ResponseBody
    public ResponseEntity<?> editItem(@PathVariable int IDID, @PathVariable int IIID,
                                      @RequestParam String customName,
                                      @RequestParam(required = false) Integer stayDurationMin,
                                      @RequestParam(required = false) String locationHint,
                                      @RequestParam(required = false) String timeSlot,
                                      @RequestParam(required = false) String note,
                                      @RequestParam(required = false) Boolean showOnMap,
                                      @RequestParam(required = false) String itemType,
                                      @RequestParam(required = false) String fromLocation,
                                      @RequestParam(required = false) String fromAddress,
                                      @RequestParam(required = false) String toLocation,
                                      @RequestParam(required = false) String toAddress,
                                      @RequestParam(required = false) String transportMethod,
                                      @RequestParam(required = false) String transportNumber,
                                      @RequestParam(required = false) String commuteDuration,
                                      @RequestParam(required = false) String startTime,
                                      @RequestParam(required = false) String endTime,
                                      @RequestParam(required = false) Integer commuteDurationMin,
                                      HttpSession session) {
        String err = checkEditPermissionByDay(session, IDID);
        if (err != null) return ResponseEntity.status(403).body(err);
        itineraryService.updateItemDetails(IIID, customName, stayDurationMin, locationHint, timeSlot, note, showOnMap,
                itemType, fromLocation, fromAddress, toLocation, toAddress, transportMethod, transportNumber, commuteDuration,
                startTime, endTime, commuteDurationMin);
        return ResponseEntity.ok().build();
    }

    // POST /itinerary/day/{IDID}/items/{IIID}/toggle-image-export → 切換某張圖片要不要匯出企劃書
    // (同一個景點/餐廳可能綁定多張照片，預設全部輸出，點一下排除，再點一下取消排除)
    // 使用者要求「行程鎖定後不能再編輯」——補上 checkEditPermissionByDay(), 前端 toggleItemImageExport()
    // 本來就會檢查 res.ok。
    @PostMapping("/day/{IDID}/items/{IIID}/toggle-image-export")
    @ResponseBody
    public ResponseEntity<?> toggleItemImageExport(@PathVariable int IDID, @PathVariable int IIID, @RequestParam int IAID,
                                                   HttpSession session) {
        String err = checkEditPermissionByDay(session, IDID);
        if (err != null) return ResponseEntity.status(403).body(err);
        itineraryService.toggleItemImageExport(IIID, IAID);
        return ResponseEntity.ok().build();
    }

    // POST /itinerary/day/{IDID}/items/{IIID}/ai-description → 沒有連結景點資料庫的項目, 儲存/更新
    // 自己暫存的介紹說明 (ai_description); 有連結 POI 的項目走的是另一套 /poi/{id}/description 端點
    // (見 PoiController), 會真的寫回共用的景點資料庫, 跟這個端點不是同一件事。
    // 使用者要求「行程鎖定後不能再編輯」——補上 checkEditPermissionByDay(), 呼叫端 (saveAllChanges())
    // 本來就會檢查 res.ok。
    @PostMapping("/day/{IDID}/items/{IIID}/ai-description")
    @ResponseBody
    public ResponseEntity<?> updateItemAiDescription(@PathVariable int IDID, @PathVariable int IIID,
                                                     @RequestParam String description, HttpSession session) {
        String err = checkEditPermissionByDay(session, IDID);
        if (err != null) return ResponseEntity.status(403).body(err);
        itineraryService.updateItemAiDescription(IIID, description);
        return ResponseEntity.ok().build();
    }

    // POST /itinerary/day/{IDID}/auto-arrange → 自動整理這一天 (預設: 餐廳/住宿排到最後面)
    // 使用者要求「行程鎖定後不能再編輯」——補上 checkEditPermissionByDay(), 前端 autoArrangeDay()
    // 本來就會檢查 res.ok。
    @PostMapping("/day/{IDID}/auto-arrange")
    @ResponseBody
    public ResponseEntity<?> autoArrangeDay(@PathVariable int IDID, @RequestParam(defaultValue = "meal_time") String mode,
                                            HttpSession session) {
        String err = checkEditPermissionByDay(session, IDID);
        if (err != null) return ResponseEntity.status(403).body(err);
        itineraryService.autoArrangeDay(IDID, mode);
        // Patch 75: 使用者反映「已經有班機的行程, 在看板按這兩顆自動整理按鈕之後, 景點還是會排到
        // 班機時間之後」——追查後發現 autoArrangeDay() 只會重新排「餐食」的時間 (patch 74 已經讓這部分
        // 正確反映班機真正佔用的時間), 但完全不會動到景點類項目的順序, 也不會刪除排到班機時間之後、
        // 明顯不可能發生的景點。這個清理工作原本只有「建立行程」流程結束時會呼叫一次
        // (trimItemsAroundFlights()), 使用者在看板上按這兩顆按鈕重排時完全沒有觸發到, 才會一直看到
        // 舊資料裡「行程排到晚上, 但班機中午就飛走了」這種結果。補上呼叫: 這裡只查得到 IDID, 用
        // getItineraryIdByDay() 反查回 ITID 才能呼叫這個以整個行程為單位的方法; 找不到 (理論上不會
        // 發生, 上面 checkEditPermissionByDay() 已經確認過這個 IDID 存在) 就跳過, 不讓例外擋掉整個
        // 自動整理動作。
        Integer ITIDForTrim = itineraryService.getItineraryIdByDay(IDID);
        if (ITIDForTrim != null) {
            itineraryService.trimItemsAroundFlights(ITIDForTrim);
            // Patch 78: 見 ItineraryService.trimDaysExceedingCutoff() 說明——一樣要放在
            // trimItemsAroundFlights() 之後, 用清理過班機後的最新狀態重新檢查每天是否還是超過 20:30。
            itineraryService.trimDaysExceedingCutoff(ITIDForTrim);
        }
        return ResponseEntity.ok().build();
    }

    // POST /itinerary/{id}/auto-arrange → 自動整理「整個行程」(所有天), 不是只有目前這天
    @PostMapping("/{id}/auto-arrange")
    @ResponseBody
    public ResponseEntity<?> autoArrangeItinerary(@PathVariable("id") int ITID, @RequestParam(defaultValue = "meal_time") String mode,
                                                  HttpSession session) {
        String err = checkEditPermission(session, ITID);
        if (err != null) return ResponseEntity.status(403).body(err);
        itineraryService.autoArrangeItinerary(ITID, mode);
        // Patch 75: 見上面 /day/{IDID}/auto-arrange 端點的說明, 這裡是同一個問題的「整個行程」版本。
        itineraryService.trimItemsAroundFlights(ITID);
        // Patch 78: 見 ItineraryService.trimDaysExceedingCutoff() 說明。
        itineraryService.trimDaysExceedingCutoff(ITID);
        return ResponseEntity.ok().build();
    }

    // GET /itinerary/day/{IDID}/items/{IIID}/options → 取得這個項目的候選點列表 (只有用「或」分隔新增的項目才有多筆)
    @GetMapping("/day/{IDID}/items/{IIID}/options")
    @ResponseBody
    public List<com.example.travelereasygate.entity.ItineraryItemOption> getItemOptions(@PathVariable int IIID) {
        return itineraryService.getItemOptions(IIID);
    }

    // POST /itinerary/day/{IDID}/items/{IIID}/select-option → 切換要用哪個候選點 (地圖會跟著換)
    // 使用者要求「行程鎖定後不能再編輯」——補上 checkEditPermissionByDay(), 前端 selectItemOption()
    // 已經同步補上失敗提示。
    @PostMapping("/day/{IDID}/items/{IIID}/select-option")
    @ResponseBody
    public ResponseEntity<?> selectItemOption(@PathVariable int IDID, @PathVariable int IIID,
                                              @RequestParam int optionId, HttpSession session) {
        String err = checkEditPermissionByDay(session, IDID);
        if (err != null) return ResponseEntity.status(403).body(err);
        itineraryService.selectItemOption(IIID, optionId);
        return ResponseEntity.ok().build();
    }

    // DELETE /itinerary/day/{IDID}/items/{IIID} → 移除項目
    // 使用者要求「行程鎖定後不能再編輯」——補上 checkEditPermissionByDay(), 呼叫端 (saveAllChanges()/
    // goBackOneStep()) 本來就會檢查 res.ok (goBackOneStep() 是刻意吞掉失敗繼續復原流程, 見該處註解)。
    @DeleteMapping("/day/{IDID}/items/{IIID}")
    @ResponseBody
    public ResponseEntity<?> removeItem(@PathVariable int IDID, @PathVariable int IIID, HttpSession session) {
        String err = checkEditPermissionByDay(session, IDID);
        if (err != null) return ResponseEntity.status(403).body(err);
        itineraryService.removeItem(IIID, IDID);
        return ResponseEntity.ok().build();
    }

    // GET /itinerary/day/{IDID}/routes → 取得該天所有相鄰項目的拉車距離/時間/迴頭路警示
    @GetMapping("/day/{IDID}/routes")
    @ResponseBody
    public List<com.example.travelereasygate.entity.RouteSegment> getRoutes(@PathVariable int IDID) {
        return itineraryService.getRoutes(IDID);
    }

    // GET /itinerary/day/{IDID}/carry-over-hotel → 前一天最後一項如果是住宿, 回傳它 (前端拿來當「今天的預設出發點」
    // 顯示在項目清單最前面 + 地圖上的第一個點), 不符合條件 (第一天/前一天沒排住宿/今天已經自己排了同一間) 就回傳 null
    @GetMapping("/day/{IDID}/carry-over-hotel")
    @ResponseBody
    public ItineraryItem getCarryOverHotel(@PathVariable int IDID) {
        return itineraryService.findCarryOverHotel(IDID);
    }

    // GET /itinerary/day/{IDID}/map → 取得這一天所有已定位項目的座標, 給看板畫地圖 / 匯出靜態地圖圖片用
    @GetMapping("/day/{IDID}/map")
    @ResponseBody
    public Map<String, Object> getMapData(@PathVariable int IDID) {
        List<ItineraryItem> items = itineraryService.getItems(IDID);
        List<com.example.travelereasygate.entity.RouteSegment> routes = itineraryService.getRoutes(IDID);
        List<Map<String, Object>> points = new ArrayList<>();
        List<double[]> coords = new ArrayList<>();
        List<String> modes = new ArrayList<>();
        List<String> itemTypes = new ArrayList<>(); // 給前端/靜態地圖依項目類型上不同顏色、住宿用床的 emoji 標示用

        // 前一天最後一項如果是住宿, 補在地圖第一個點 (見 findCarryOverHotel 說明); 這個點本身還是屬於昨天,
        // 不算今天的行程項目, 純粹是為了讓地圖 (跟靜態大圖) 從昨晚住的飯店開始畫, 感覺比較連貫。
        ItineraryItem carryOverHotel = itineraryService.findCarryOverHotel(IDID);
        if (carryOverHotel != null && !Boolean.FALSE.equals(carryOverHotel.getShowOnMap())
                && carryOverHotel.getLatitude() != null && carryOverHotel.getLongitude() != null) {
            double lat = carryOverHotel.getLatitude().doubleValue();
            double lng = carryOverHotel.getLongitude().doubleValue();

            Map<String, Object> hotelPoint = new HashMap<>();
            hotelPoint.put("iiid", carryOverHotel.getIIID());
            hotelPoint.put("lat", lat);
            hotelPoint.put("lng", lng);
            hotelPoint.put("name", carryOverHotel.getCustomName());
            hotelPoint.put("itemType", "hotel");
            String mode = routes.stream()
                    .filter(r -> r.getFromItemId() == carryOverHotel.getIIID())
                    .findFirst()
                    .map(com.example.travelereasygate.entity.RouteSegment::getTransportMode)
                    .orElse("driving");
            hotelPoint.put("mode", mode);

            points.add(hotelPoint);
            coords.add(new double[]{lat, lng});
            modes.add(mode);
            itemTypes.add("hotel");
        }

        for (ItineraryItem item : items) {
            if (Boolean.FALSE.equals(item.getShowOnMap())) continue; // 這個項目關閉了「顯示在地圖上」

            double lat, lng;
            if (item.getLatitude() != null && item.getLongitude() != null) {
                lat = item.getLatitude().doubleValue();
                lng = item.getLongitude().doubleValue();
            } else if (item.getPID() != null) {
                Poi poi = poiService.findById(item.getPID());
                if (poi == null || poi.getLatitude() == null) continue;
                lat = poi.getLatitude().doubleValue();
                lng = poi.getLongitude().doubleValue();
            } else {
                continue;
            }

            Map<String, Object> point = new HashMap<>();
            point.put("iiid", item.getIIID());
            point.put("lat", lat);
            point.put("lng", lng);
            point.put("name", item.getCustomName());
            point.put("itemType", item.getItemType());
            point.put("transportMethod", item.getTransportMethod()); // 前端用來判斷是不是班機項目 (畫飛機圖示、不編 A/B/C 字母)

            // 找出「這個點 → 下一個點」這段路段的通勤方式, 讓前端用 DirectionsService 畫路線、靜態地圖大圖時模式一致
            String mode = routes.stream()
                    .filter(r -> r.getFromItemId() == item.getIIID())
                    .findFirst()
                    .map(com.example.travelereasygate.entity.RouteSegment::getTransportMode)
                    .orElse("driving");
            point.put("mode", mode);

            points.add(point);
            coords.add(new double[]{lat, lng});
            modes.add(mode);
            itemTypes.add(item.getItemType());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("points", points);
        result.put("configured", googleMapsClient.isConfigured());
        result.put("staticMapUrl", googleMapsClient.buildStaticMapUrl(coords, modes, itemTypes, 600, 400));
        return result;
    }

    // GET /itinerary/day/{IDID}/suggest-between?fromIIID=X&toIIID=Y → 智慧中途點推薦
    // 找出公司資料庫裡, 落在這兩個景點之間「順路」範圍內的其他景點/餐廳/休息站
    @GetMapping("/day/{IDID}/suggest-between")
    @ResponseBody
    public List<Poi> suggestBetween(@PathVariable int IDID,
                                    @RequestParam int fromIIID, @RequestParam int toIIID,
                                    HttpSession session) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return List.of();
        return itineraryService.suggestPoiBetween(AID, IDID, fromIIID, toIIID);
    }

    // POST /itinerary/day/{IDID}/start-time → 更新這天的出發時間 (時間軸看板用)
    // 使用者要求「行程鎖定後不能再編輯」——補上 checkEditPermissionByDay(), 前端 updateStartTime()
    // 已經同步補上失敗提示。
    @PostMapping("/day/{IDID}/start-time")
    @ResponseBody
    public ResponseEntity<?> updateStartTime(@PathVariable int IDID, @RequestParam String startTime, HttpSession session) {
        String err = checkEditPermissionByDay(session, IDID);
        if (err != null) return ResponseEntity.status(403).body(err);
        itineraryService.updateDayStartTime(IDID, java.time.LocalTime.parse(startTime));
        return ResponseEntity.ok().build();
    }

    // POST /itinerary/day/{IDID}/transport-mode → 切換這天的交通方式 (開車/走路), 會自動重算拉車時間
    // (目前前端沒有任何地方呼叫這支端點, 交通方式改成用 segments/mode 逐段設定, 這裡補上檢查只是
    // 防禦性補齊, 避免這支端點以後被重新接上時漏掉鎖定檢查)
    @PostMapping("/day/{IDID}/transport-mode")
    @ResponseBody
    public ResponseEntity<?> updateTransportMode(@PathVariable int IDID, @RequestParam String mode, HttpSession session) {
        String err = checkEditPermissionByDay(session, IDID);
        if (err != null) return ResponseEntity.status(403).body(err);
        itineraryService.updateDayTransportMode(IDID, mode);
        return ResponseEntity.ok().build();
    }

    // POST /itinerary/day/{IDID}/items/{IIID}/add-to-poi → 把這個項目寫進公司 POI 資料庫並自動連結
    // 使用者要求「行程鎖定後不能再編輯」——補上 checkEditPermissionByDay(), 前端 addToPoiDatabase()
    // 本來就會檢查 res.ok。
    @PostMapping("/day/{IDID}/items/{IIID}/add-to-poi")
    @ResponseBody
    public ResponseEntity<?> addItemToPoi(@PathVariable int IDID, @PathVariable int IIID, HttpSession session) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return ResponseEntity.status(401).build();
        String err = checkEditPermissionByDay(session, IDID);
        if (err != null) return ResponseEntity.status(403).body(err);

        try {
            itineraryService.addItemToPoi(AID, IIID);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // POST /itinerary/day/{IDID}/reorder → 拖曳排序後儲存新順序
    // body 範例: { "order": [12, 15, 13, 14] }  <- IIID 陣列
    // 使用者要求「行程鎖定後不能再編輯」——補上 checkEditPermissionByDay(), 兩個呼叫端
    // (saveAllChanges()/addSuggestedBetween()) 本來就會檢查 res.ok。
    @PostMapping("/day/{IDID}/reorder")
    @ResponseBody
    public ResponseEntity<?> reorder(@PathVariable int IDID, @RequestBody Map<String, List<Integer>> body, HttpSession session) {
        String err = checkEditPermissionByDay(session, IDID);
        if (err != null) return ResponseEntity.status(403).body(err);
        itineraryService.reorderItems(IDID, body.get("order"));
        return ResponseEntity.ok().build();
    }

    // POST /itinerary/{id}/reorder-days → 拖曳上方「Day 分頁」排序後儲存新的天數順序
    // body 範例: { "order": [102, 100, 101] }  <- IDID 陣列, 代表新的 Day1, Day2, Day3...
    @PostMapping("/{id}/reorder-days")
    @ResponseBody
    public ResponseEntity<?> reorderDays(@PathVariable("id") int ITID, @RequestBody Map<String, List<Integer>> body,
                                         HttpSession session) {
        String err = checkEditPermission(session, ITID);
        if (err != null) return ResponseEntity.status(403).body(err);
        itineraryService.reorderDays(ITID, body.get("order"));
        return ResponseEntity.ok().build();
    }

    // POST /itinerary/day/{IDID}/segments/{RSID}/mode → 手動覆寫單一段的通勤方式, 並重算該段時間/距離
    // 使用者要求「行程鎖定後不能再編輯」——補上 checkEditPermissionByDay(), 前端 updateSegmentTransportMode()
    // 本來就會檢查 res.ok。
    @PostMapping("/day/{IDID}/segments/{RSID}/mode")
    @ResponseBody
    public ResponseEntity<?> updateSegmentMode(@PathVariable int IDID, @PathVariable int RSID,
                                               @RequestParam String mode, HttpSession session) {
        String err = checkEditPermissionByDay(session, IDID);
        if (err != null) return ResponseEntity.status(403).body(err);
        itineraryService.updateSegmentTransportMode(IDID, RSID, mode);
        return ResponseEntity.ok().build();
    }
}