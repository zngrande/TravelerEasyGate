package com.example.UsefulTravel.controller;

import com.example.UsefulTravel.entity.Itinerary;
import com.example.UsefulTravel.entity.ItineraryDay;
import com.example.UsefulTravel.entity.ItineraryItem;
import com.example.UsefulTravel.entity.Poi;
import com.example.UsefulTravel.service.GoogleMapsClient;
import com.example.UsefulTravel.service.ItineraryService;
import com.example.UsefulTravel.service.PermissionService;
import com.example.UsefulTravel.service.PoiService;
import jakarta.servlet.http.HttpSession;
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

@Controller
@RequestMapping("/itinerary")
public class ItineraryController {

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
     * 共用守門邏輯：檢查「這個角色能不能編輯行程」+「這個行程有沒有被別人鎖住」。
     * 回傳 null 代表可以放行；不是 null 就是要擋下來的錯誤訊息, 呼叫端依自己的回傳型別決定怎麼包裝。
     *
     * 目前只掛在 controller 層級較粗的動作 (完成/刪除/整體自動整理/日期排序/上鎖/解鎖) 上；
     * 看板上細部的單一景點新增/編輯/刪除等 AJAX 端點屬於更高頻互動, 先靠前端「上鎖時停用操作按鈕」擋,
     * 伺服器端逐一補齊屬於後續優化項目, 避免這次一次改動太大範圍造成既有看板互動出問題。
     */
    private String checkEditPermission(HttpSession session, int ITID) {
        Integer AID = (Integer) session.getAttribute("AID");
        Integer UID = (Integer) session.getAttribute("UID");
        String role = (String) session.getAttribute("role");
        if (AID == null || UID == null) return "尚未登入";
        if (!permissionService.canEditItinerary(role)) return "目前帳號角色沒有編輯行程的權限";

        Itinerary itinerary = itineraryService.getItinerary(ITID);
        if (itinerary == null || itinerary.getAID() != AID) return "找不到這個行程";
        if (!itineraryService.isEditableBy(ITID, UID)) return "這個行程目前被其他人鎖定中，無法編輯";
        return null;
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
        for (com.example.UsefulTravel.entity.ItineraryDay day : itineraryService.getDays(ITID)) {
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
                         @RequestParam(required = false) List<String> outDepAirport,
                         @RequestParam(required = false) List<String> outDepTime,
                         @RequestParam(required = false) List<String> outArrAirport,
                         @RequestParam(required = false) List<String> outArrTime,
                         @RequestParam(required = false) List<String> outDepDay,
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
        itineraryService.attachFlightItems(itinerary.getITID(),
                outDepAirport, outDepTime, outArrAirport, outArrTime, outDepDay,
                retDepAirport, retDepTime, retArrAirport, retArrTime, retDepDay);
        // Patch 28: 班機時間如果剛好卡到某一餐固定的用餐時間, 這餐就不需要呈現——一定要在班機轉成
        // transport 項目之後才呼叫, 見 ItineraryService.hideMealsOverlappingFlights() 說明。
        itineraryService.hideMealsOverlappingFlights(itinerary.getITID());
        // 去程/回程機場銜接的拉車距離/時間——一定要在上面兩個呼叫都跑完之後才算, 見
        // ItineraryService.calculateAirportTransferSegments() 說明。
        itineraryService.calculateAirportTransferSegments(itinerary.getITID());
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
                                    @RequestParam(required = false) List<String> outDepAirport,
                                    @RequestParam(required = false) List<String> outDepTime,
                                    @RequestParam(required = false) List<String> outArrAirport,
                                    @RequestParam(required = false) List<String> outArrTime,
                                    @RequestParam(required = false) List<String> outDepDay,
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
        Itinerary itinerary = itineraryService.createItineraryWithAiPlan(AID, UID, title, country, region, daysCount, parsedDate, dayCities);

        // 這個提示是「AI 有沒有真的排到景點資料庫裡的東西」, 一定要在插入去程/回程班機之前判斷 ——
        // 不然只要有填班機資訊, hasAnyItem() 就會一直是 true (班機本身也算一筆項目), 提示永遠不會跳出來,
        // 使用者反而不知道 AI 其實沒排到任何真正的景點。
        boolean aiFoundNothing = !itineraryService.hasAnyItem(itinerary.getITID());

        // 一定要等 createItineraryWithAiPlan() 內部的自動整理 (meal_time) 全部跑完才能插入去程/回程班機,
        // 不然剛插好的「第一筆/最後一筆」會被自動整理重新洗牌 (見 ItineraryService.attachFlightItems 說明)
        itineraryService.attachFlightItems(itinerary.getITID(),
                outDepAirport, outDepTime, outArrAirport, outArrTime, outDepDay,
                retDepAirport, retDepTime, retArrAirport, retArrTime, retDepDay);
        // Patch 28: 班機時間如果剛好卡到某一餐固定的用餐時間, 這餐就不需要呈現——一定要在班機轉成
        // transport 項目之後才呼叫, 見 ItineraryService.hideMealsOverlappingFlights() 說明。
        itineraryService.hideMealsOverlappingFlights(itinerary.getITID());
        // 去程/回程機場銜接的拉車距離/時間——一定要在上面兩個呼叫都跑完之後才算, 見
        // ItineraryService.calculateAirportTransferSegments() 說明。
        itineraryService.calculateAirportTransferSegments(itinerary.getITID());

        if (aiFoundNothing) {
            redirectAttributes.addFlashAttribute("aiPlanNotice",
                    "AI 沒有找到「" + country + (region != null && !region.isBlank() ? " / " + region : "")
                            + "」符合的景點資料 (或 AI 排程失敗), 已建立空白行程, 請從左側手動加入景點。");
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
        itineraryService.calculateAirportTransferSegments(ITID);
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

        // 上鎖狀態: 給看板頂部顯示「XXX 正在編輯」提示 + 決定要不要停用編輯按鈕用
        Integer UID = (Integer) session.getAttribute("UID");
        String role = (String) session.getAttribute("role");
        boolean lockedByOther = itinerary != null && itinerary.isLocked()
                && itinerary.getLockedBy() != null && UID != null && !itinerary.getLockedBy().equals(UID);
        model.addAttribute("lockedByOther", lockedByOther);
        model.addAttribute("canEditItinerary", permissionService.canEditItinerary(role) && !lockedByOther);
        model.addAttribute("canQuote", permissionService.canQuote(role));

        return "itinerary/board";
    }

    // POST /itinerary/{id}/lock → 上鎖 (供他人編輯時避免互相覆蓋, 需求文件 2.2)
    @PostMapping("/{id}/lock")
    @ResponseBody
    public ResponseEntity<?> lock(@PathVariable("id") int ITID, HttpSession session) {
        Integer UID = (Integer) session.getAttribute("UID");
        String err = checkEditPermission(session, ITID);
        if (err != null) return ResponseEntity.status(403).body(err);

        boolean ok = itineraryService.lockItinerary(ITID, UID);
        return ok ? ResponseEntity.ok().build() : ResponseEntity.status(409).body("這個行程已經被其他人鎖定");
    }

    // POST /itinerary/{id}/unlock → 解鎖
    @PostMapping("/{id}/unlock")
    @ResponseBody
    public ResponseEntity<?> unlock(@PathVariable("id") int ITID, HttpSession session) {
        Integer AID = (Integer) session.getAttribute("AID");
        String role = (String) session.getAttribute("role");
        if (AID == null) return ResponseEntity.status(401).build();
        if (!permissionService.canEditItinerary(role)) return ResponseEntity.status(403).body("沒有權限解鎖");

        Itinerary itinerary = itineraryService.getItinerary(ITID);
        if (itinerary == null || itinerary.getAID() != AID) return ResponseEntity.status(404).build();

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

    // DELETE /itinerary/day/{IDID} → 刪除整天 (Day 分頁旁邊的刪除按鈕), 後面的天數會自動往前遞補一位
    @DeleteMapping("/day/{IDID}")
    @ResponseBody
    public void deleteDay(@PathVariable int IDID) {
        itineraryService.deleteDay(IDID);
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
    @GetMapping("/day/{IDID}/items")
    @ResponseBody
    public List<ItineraryItem> getItems(@PathVariable int IDID) {
        return itineraryService.getItems(IDID);
    }

    // POST /itinerary/day/{IDID}/items → 把景點/餐廳/自訂項目加入某一天
    @PostMapping("/day/{IDID}/items")
    @ResponseBody
    public ItineraryItem addItem(@PathVariable int IDID,
                                 @RequestParam(required = false) Integer PID,
                                 @RequestParam String itemType,
                                 @RequestParam(required = false) String customName) {
        return itineraryService.addItem(IDID, PID, itemType, customName);
    }

    // POST /itinerary/day/{IDID}/items/custom → 新增自訂項目 (不連結 POI 資料庫, 但會自動地理編碼)
    // rawName 可以用「或」分隔多個候選點, 例如「花蓮翰品酒店或día酒店或某某民宿」
    // locationHint 選填: 貼 Google 地圖網址或地址, 定位比純打名稱準確
    @PostMapping("/day/{IDID}/items/custom")
    @ResponseBody
    public ItineraryItem addCustomItem(@PathVariable int IDID,
                                       @RequestParam String itemType,
                                       @RequestParam String rawName,
                                       @RequestParam(required = false) Integer stayDurationMin,
                                       @RequestParam(required = false) String locationHint) {
        return itineraryService.addCustomItem(IDID, itemType, rawName, stayDurationMin, locationHint);
    }

    // POST /itinerary/day/{IDID}/items/{IIID}/edit → 編輯看板上已存在的項目
    // (名稱/停留時間/時段/備註/地圖顯示/重新定位/更換類別, 以及交通類別專用的起始點/起始地址/目的地/目的地地址/交通工具/通勤時間)
    @PostMapping("/day/{IDID}/items/{IIID}/edit")
    @ResponseBody
    public void editItem(@PathVariable int IIID,
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
                         @RequestParam(required = false) String commuteDuration,
                         @RequestParam(required = false) String startTime,
                         @RequestParam(required = false) String endTime,
                         @RequestParam(required = false) Integer commuteDurationMin) {
        itineraryService.updateItemDetails(IIID, customName, stayDurationMin, locationHint, timeSlot, note, showOnMap,
                itemType, fromLocation, fromAddress, toLocation, toAddress, transportMethod, commuteDuration,
                startTime, endTime, commuteDurationMin);
    }

    // POST /itinerary/day/{IDID}/items/{IIID}/toggle-image-export → 切換某張圖片要不要匯出企劃書
    // (同一個景點/餐廳可能綁定多張照片，預設全部輸出，點一下排除，再點一下取消排除)
    @PostMapping("/day/{IDID}/items/{IIID}/toggle-image-export")
    @ResponseBody
    public void toggleItemImageExport(@PathVariable int IIID, @RequestParam int IAID) {
        itineraryService.toggleItemImageExport(IIID, IAID);
    }

    // POST /itinerary/day/{IDID}/auto-arrange → 自動整理這一天 (預設: 餐廳/住宿排到最後面)
    @PostMapping("/day/{IDID}/auto-arrange")
    @ResponseBody
    public void autoArrangeDay(@PathVariable int IDID, @RequestParam(defaultValue = "meal_time") String mode) {
        itineraryService.autoArrangeDay(IDID, mode);
    }

    // POST /itinerary/{id}/auto-arrange → 自動整理「整個行程」(所有天), 不是只有目前這天
    @PostMapping("/{id}/auto-arrange")
    @ResponseBody
    public ResponseEntity<?> autoArrangeItinerary(@PathVariable("id") int ITID, @RequestParam(defaultValue = "meal_time") String mode,
                                                   HttpSession session) {
        String err = checkEditPermission(session, ITID);
        if (err != null) return ResponseEntity.status(403).body(err);
        itineraryService.autoArrangeItinerary(ITID, mode);
        return ResponseEntity.ok().build();
    }

    // GET /itinerary/day/{IDID}/items/{IIID}/options → 取得這個項目的候選點列表 (只有用「或」分隔新增的項目才有多筆)
    @GetMapping("/day/{IDID}/items/{IIID}/options")
    @ResponseBody
    public List<com.example.UsefulTravel.entity.ItineraryItemOption> getItemOptions(@PathVariable int IIID) {
        return itineraryService.getItemOptions(IIID);
    }

    // POST /itinerary/day/{IDID}/items/{IIID}/select-option → 切換要用哪個候選點 (地圖會跟著換)
    @PostMapping("/day/{IDID}/items/{IIID}/select-option")
    @ResponseBody
    public void selectItemOption(@PathVariable int IIID, @RequestParam int optionId) {
        itineraryService.selectItemOption(IIID, optionId);
    }

    // DELETE /itinerary/day/{IDID}/items/{IIID} → 移除項目
    @DeleteMapping("/day/{IDID}/items/{IIID}")
    @ResponseBody
    public void removeItem(@PathVariable int IDID, @PathVariable int IIID) {
        itineraryService.removeItem(IIID, IDID);
    }

    // GET /itinerary/day/{IDID}/routes → 取得該天所有相鄰項目的拉車距離/時間/迴頭路警示
    @GetMapping("/day/{IDID}/routes")
    @ResponseBody
    public List<com.example.UsefulTravel.entity.RouteSegment> getRoutes(@PathVariable int IDID) {
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
        List<com.example.UsefulTravel.entity.RouteSegment> routes = itineraryService.getRoutes(IDID);
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
                    .map(com.example.UsefulTravel.entity.RouteSegment::getTransportMode)
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
                    .map(com.example.UsefulTravel.entity.RouteSegment::getTransportMode)
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
    @PostMapping("/day/{IDID}/start-time")
    @ResponseBody
    public void updateStartTime(@PathVariable int IDID, @RequestParam String startTime) {
        itineraryService.updateDayStartTime(IDID, java.time.LocalTime.parse(startTime));
    }

    // POST /itinerary/day/{IDID}/transport-mode → 切換這天的交通方式 (開車/走路), 會自動重算拉車時間
    @PostMapping("/day/{IDID}/transport-mode")
    @ResponseBody
    public void updateTransportMode(@PathVariable int IDID, @RequestParam String mode) {
        itineraryService.updateDayTransportMode(IDID, mode);
    }

    // POST /itinerary/day/{IDID}/items/{IIID}/add-to-poi → 把這個項目寫進公司 POI 資料庫並自動連結
    @PostMapping("/day/{IDID}/items/{IIID}/add-to-poi")
    @ResponseBody
    public ResponseEntity<?> addItemToPoi(@PathVariable int IDID, @PathVariable int IIID, HttpSession session) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return ResponseEntity.status(401).build();

        try {
            itineraryService.addItemToPoi(AID, IIID);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // POST /itinerary/day/{IDID}/reorder → 拖曳排序後儲存新順序
    // body 範例: { "order": [12, 15, 13, 14] }  <- IIID 陣列
    @PostMapping("/day/{IDID}/reorder")
    @ResponseBody
    public void reorder(@PathVariable int IDID, @RequestBody Map<String, List<Integer>> body) {
        itineraryService.reorderItems(IDID, body.get("order"));
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
    @PostMapping("/day/{IDID}/segments/{RSID}/mode")
    @ResponseBody
    public void updateSegmentMode(@PathVariable int IDID, @PathVariable int RSID, @RequestParam String mode) {
        itineraryService.updateSegmentTransportMode(IDID, RSID, mode);
    }
}