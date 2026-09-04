package com.example.travelereasygate.controller;

import com.example.travelereasygate.DAO.CurrencyDAO;
import com.example.travelereasygate.DAO.ItineraryDAO;
import com.example.travelereasygate.DAO.MarginSettingDAO;
import com.example.travelereasygate.DAO.TravelComponentDAO;
import com.example.travelereasygate.entity.Itinerary;
import com.example.travelereasygate.entity.ItineraryDay;
import com.example.travelereasygate.entity.ItineraryItem;
import com.example.travelereasygate.entity.Quotation;
import com.example.travelereasygate.entity.QuotationLine;
import com.example.travelereasygate.service.ItineraryService;
import com.example.travelereasygate.service.PermissionService;
import com.example.travelereasygate.service.QuotationService;
import com.example.travelereasygate.service.FormulaEngine;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 報價 / 財務引擎 (需求文件第三章) 的操作入口。
 * 行程排版看板「跳轉報價單編輯」會導到這裡。
 */
@Controller
public class QuotationController {

    private final QuotationService quotationService;
    private final ItineraryDAO itineraryDAO;
    private final ItineraryService itineraryService;
    private final CurrencyDAO currencyDAO;
    private final TravelComponentDAO travelComponentDAO;
    private final PermissionService permissionService;
    private final MarginSettingDAO marginSettingDAO;

    @Autowired
    public QuotationController(QuotationService quotationService, ItineraryDAO itineraryDAO,
                               ItineraryService itineraryService,
                               CurrencyDAO currencyDAO, TravelComponentDAO travelComponentDAO,
                               PermissionService permissionService, MarginSettingDAO marginSettingDAO) {
        this.quotationService = quotationService;
        this.itineraryDAO = itineraryDAO;
        this.itineraryService = itineraryService;
        this.currencyDAO = currencyDAO;
        this.travelComponentDAO = travelComponentDAO;
        this.permissionService = permissionService;
        this.marginSettingDAO = marginSettingDAO;
    }

    /** 報價相關的寫入動作 (新增版本/新增明細/上鎖/確認...) 都要有「製作/調整報價」權限 (ADMIN 或 QUOTER)。 */
    private boolean canQuote(HttpSession session) {
        String role = (String) session.getAttribute("role");
        return permissionService.canQuote(role);
    }

    // 使用者回報「正式報價單一打開就出錯」的真正根因: quotation/edit.html 用 Thymeleaf 的
    // JS inlining (/*[[${currencies}]]*/) 把 currencies 這個 model 屬性直接序列化成 JS 陣列給
    // 前端幣別自動完成用 (見 edit.html CURRENCY_LIST)。currencyDAO.findAvailable() 回傳的是完整的
    // Currency 實體, 裡面的 updatedAt 欄位是 java.time.LocalDateTime——Thymeleaf 這個 JS 序列化用的
    // Jackson ObjectMapper 是它自己內部另外 new 出來的一份, 沒有註冊 jackson-datatype-jsr310,
    // 一旦真的遇到某一筆幣別的 updatedAt 不是 null (等於真的要序列化 LocalDateTime 這個型別),
    // 就會丟 InvalidDefinitionException 把整個樣板 render 中斷掉, 看起來就像「頁面打開就出錯／消失」。
    // 前端 (CURRENCY_LIST) 實際上只用得到 code/name 兩個欄位 (見 currencyDisplayFor()/renderList()),
    // 這裡直接組一份只有這兩個欄位的輕量清單餵給 JS inlining, 從根本避開整個 Currency 實體
    // (以及它未來任何不支援 JS 序列化的欄位) 被拿去序列化的風險, 不用去改全域 Jackson/Thymeleaf 設定。
    // 「整團人數級距報價結果」表格的幣別轉換用匯率 (1 單位該幣別 = 多少台幣, 跟 QuotationService
    // #resolveGroupTierCurrencyRate() 同一個方向/同一份資料來源, 這裡是給畫面顯示轉換用, 不影響
    // 實際存進資料庫的台幣快照)。所有級距共用同一個幣別 (updateGroupTierCurrency() 一次改全部),
    // 拿第一筆的 currency 查就好; 沒有級距、或幣別是 TWD/空白時固定回傳 1 (不轉換)。
    private BigDecimal groupTierCurrencyRate(List<com.example.travelereasygate.entity.QuotationGroupTier> groupTiers, int AID) {
        if (groupTiers == null || groupTiers.isEmpty()) return BigDecimal.ONE;
        String currency = groupTiers.get(0).getCurrency();
        if (currency == null || currency.isBlank() || "TWD".equalsIgnoreCase(currency)) return BigDecimal.ONE;
        com.example.travelereasygate.entity.Currency c = currencyDAO.findByCode(currency, AID);
        return (c != null && c.getRateToTwd() != null && c.getRateToTwd().compareTo(BigDecimal.ZERO) > 0)
                ? c.getRateToTwd() : BigDecimal.ONE;
    }

    private List<Map<String, String>> currencyOptionsForJs(int AID) {
        List<Map<String, String>> options = new java.util.ArrayList<>();
        for (com.example.travelereasygate.entity.Currency c : currencyDAO.findAvailable(AID)) {
            Map<String, String> opt = new LinkedHashMap<>();
            opt.put("code", c.getCode());
            opt.put("name", c.getName());
            options.add(opt);
        }
        return options;
    }

    // GET /quotations → 報價單儀表板, 跟「我的行程」(agency/dashboard.html) 同一套版面,
    // 列出這個帳號所有行程 + 每個行程目前有幾份報價單版本, 點一行就進去 /itinerary/{id}/quotations
    @GetMapping("/quotations")
    public String dashboard(HttpSession session, Model model) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return "redirect:/login";

        List<Itinerary> itineraries = itineraryDAO.findByAgency(AID);
        Map<Integer, Integer> quotationCounts = new HashMap<>();
        for (Itinerary it : itineraries) {
            quotationCounts.put(it.getITID(), quotationService.findByItinerary(it.getITID()).size());
        }

        model.addAttribute("itineraries", itineraries);
        model.addAttribute("quotationCounts", quotationCounts);
        model.addAttribute("name", session.getAttribute("name"));
        return "quotation/dashboard";
    }

    // GET /itinerary/{id}/quotations → 這個行程底下所有報價單版本列表
    @GetMapping("/itinerary/{id}/quotations")
    public String list(@PathVariable("id") int ITID, HttpSession session, Model model) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return "redirect:/login";

        Itinerary itinerary = itineraryDAO.findById(ITID);
        if (itinerary == null || itinerary.getAID() != AID) return "redirect:/agency/dashboard";

        model.addAttribute("itinerary", itinerary);
        model.addAttribute("quotations", quotationService.findByItinerary(ITID));
        return "quotation/list";
    }

    // POST /itinerary/{id}/quotations/new → 建立新版本報價單
    // 同業/直售加成跟退傭%的初始值改成直接帶入同一個行程上一版報價單的數字, 不再需要選「加成規則」範本
    @PostMapping("/itinerary/{id}/quotations/new")
    public String create(@PathVariable("id") int ITID,
                         @RequestParam(defaultValue = "1") int groupSize,
                         HttpSession session) {
        Integer AID = (Integer) session.getAttribute("AID");
        Integer UID = (Integer) session.getAttribute("UID");
        if (AID == null) return "redirect:/login";
        if (!canQuote(session)) return "redirect:/itinerary/" + ITID + "/quotations?permissionError=1";

        Itinerary itinerary = itineraryDAO.findById(ITID);
        if (itinerary == null || itinerary.getAID() != AID) return "redirect:/agency/dashboard";

        int size = groupSize > 0 ? groupSize : (itinerary.getGroupSize() != null ? itinerary.getGroupSize() : 1);

        Quotation q = quotationService.createQuotation(ITID, AID, size, UID);
        return "redirect:/quotation/" + q.getQID();
    }

    // GET /itinerary/{id}/quick-quote → 找目前的草稿報價單(沒有就建一個), 導去簡易報價編輯頁
    @GetMapping("/itinerary/{id}/quick-quote")
    public String quickQuoteEntry(@PathVariable("id") int ITID, HttpSession session) {
        Integer AID = (Integer) session.getAttribute("AID");
        Integer UID = (Integer) session.getAttribute("UID");
        if (AID == null) return "redirect:/login";
        if (!canQuote(session)) return "redirect:/itinerary/" + ITID + "/board?permissionError=1";

        Itinerary itinerary = itineraryDAO.findById(ITID);
        if (itinerary == null || itinerary.getAID() != AID) return "redirect:/agency/dashboard";

        int size = itinerary.getGroupSize() != null ? itinerary.getGroupSize() : 1;

        Quotation q = quotationService.findOrCreateDraftQuotation(ITID, AID, size, UID);
        return "redirect:/quotation/" + q.getQID() + "/quick-edit";
    }

    // GET /quotation/{qid}/quick-edit → 簡易報價編輯頁: 跟編輯行程模板類似, 但沒有地圖,
    // 景點只顯示名稱旁邊填價錢, 左側改放雜費, 適合遊程規劃師快速抓個大概價錢用
    @GetMapping("/quotation/{qid}/quick-edit")
    public String quickEdit(@PathVariable("qid") int QID, HttpSession session, Model model) {
        String view = buildQuickEditModel(QID, session, model);
        return view != null ? view : "quotation/quick-edit";
    }

    // GET /quotation/{qid}/quick-edit/fragment → 跟上面同一份資料, 但只回傳畫面內容片段, 給前端 AJAX 局部刷新用
    @GetMapping("/quotation/{qid}/quick-edit/fragment")
    public String quickEditFragment(@PathVariable("qid") int QID, HttpSession session, Model model) {
        String view = buildQuickEditModel(QID, session, model);
        return view != null ? view : "quotation/quick-edit :: pageContent";
    }

    /** 回傳 null 代表資料正常, model 已經填好可以渲染; 回傳非 null 代表要導頁 (沒登入/找不到資料), 兩個 GET 端點共用。 */
    private String buildQuickEditModel(int QID, HttpSession session, Model model) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return "redirect:/login";

        Quotation quotation = quotationService.findById(QID);
        if (quotation == null || quotation.getAID() != AID) return "redirect:/agency/dashboard";

        Itinerary itinerary = itineraryDAO.findById(quotation.getITID());
        List<ItineraryDay> days = itineraryService.getDays(quotation.getITID());

        // 每一天的項目清單 + 這個項目目前有沒有填過價錢 (依 IIID 對應到同一筆報價明細)
        Map<Integer, List<ItineraryItem>> itemsByDay = new LinkedHashMap<>();
        Map<Integer, QuotationLine> lineByItem = new LinkedHashMap<>();
        for (ItineraryDay day : days) {
            List<ItineraryItem> items = itineraryService.getItems(day.getIDID());
            itemsByDay.put(day.getIDID(), items);
            for (ItineraryItem item : items) {
                QuotationLine line = quotationService.findLineByItem(QID, item.getIIID());
                if (line != null) lineByItem.put(item.getIIID(), line);
            }
        }

        // 雜費: 沒有連結到任何行程項目的明細 (接駁車/導遊薪資/保險這類自訂項目)
        List<QuotationLine> miscLines = quotationService.findLines(QID).stream()
                .filter(l -> l.getSourceItemId() == null)
                .toList();

        model.addAttribute("quotation", quotation);
        model.addAttribute("itinerary", itinerary);
        model.addAttribute("days", days);
        model.addAttribute("itemsByDay", itemsByDay);
        model.addAttribute("lineByItem", lineByItem);
        model.addAttribute("miscLines", miscLines);
        model.addAttribute("currencies", currencyOptionsForJs(AID));
        model.addAttribute("totals", quotationService.getTotals(QID));
        model.addAttribute("canEdit", quotation.isEditable() && canQuote(session));
        // 使用者要求「簡易報價單可以拉元件庫內容，名稱價錢會自動帶入」——跟正式報價頁「新增報價項目」
        // 卡片的「從元件庫選擇」是同一份元件清單、同一支後端端點 (/quotation/{qid}/lines/from-component)，
        // 差別只在 back=quick 導回這一頁而不是正式報價頁。
        model.addAttribute("components", travelComponentDAO.findByAgency(AID));
        return null;
    }

    // POST /quotation/{qid}/quick-edit/items/{iiid}/price → 幫某個景點/餐廳/飯店項目填價錢
    @PostMapping("/quotation/{qid}/quick-edit/items/{iiid}/price")
    public ResponseEntity<String> updateItemPrice(@PathVariable("qid") int QID, @PathVariable("iiid") int IIID,
                                                  @RequestParam String itemName,
                                                  @RequestParam(defaultValue = "other") String category,
                                                  @RequestParam BigDecimal unitPrice,
                                                  HttpSession session) {
        if (session.getAttribute("AID") == null) return ResponseEntity.status(401).body("尚未登入");
        if (!canQuote(session)) return ResponseEntity.status(403).body("沒有報價權限");
        try {
            quotationService.upsertItemPrice(QID, IIID, itemName, category, unitPrice);
            return ResponseEntity.ok("ok");
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // GET /quotation/{qid} → 報價單編輯/檢視頁 (核心畫面)
    @GetMapping("/quotation/{qid}")
    public String edit(@PathVariable("qid") int QID, HttpSession session, Model model) {
        String view = buildEditModel(QID, session, model);
        return view != null ? view : "quotation/edit";
    }

    // GET /quotation/{qid}/fragment → 跟上面同一份資料, 但只回傳畫面內容片段, 給前端 AJAX 局部刷新用
    @GetMapping("/quotation/{qid}/fragment")
    public String editFragment(@PathVariable("qid") int QID, HttpSession session, Model model) {
        String view = buildEditModel(QID, session, model);
        return view != null ? view : "quotation/edit :: pageContent";
    }

    /** 回傳 null 代表資料正常, model 已經填好可以渲染; 回傳非 null 代表要導頁, 兩個 GET 端點共用。 */
    private String buildEditModel(int QID, HttpSession session, Model model) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return "redirect:/login";

        Quotation quotation = quotationService.findById(QID);
        if (quotation == null || quotation.getAID() != AID) return "redirect:/agency/dashboard";

        Itinerary itinerary = itineraryDAO.findById(quotation.getITID());

        model.addAttribute("quotation", quotation);
        model.addAttribute("itinerary", itinerary);
        List<QuotationLine> lines = quotationService.findLines(QID);
        model.addAttribute("lines", lines);
        model.addAttribute("totals", quotationService.getTotals(QID));
        model.addAttribute("currencies", currencyOptionsForJs(AID));
        // 「新增報價項目」表單幣別欄位預設值: 記住這張報價單目前最後一筆項目用的幣別, 而不是每次都預設 TWD
        // (使用者回報「加入報價單的項目幣別會跟上一筆加入的幣別一樣」這個行為不見了——這裡補回來)。
        // lines 本身已經是照 sortOrder/QLID 由小到大排序 (見 QuotationLineDAO#findByQuotation), 拿最後一筆
        // 的幣別當預設值就是「上一筆加入的幣別」；還沒有任何項目時退回 TWD。
        model.addAttribute("lastUsedCurrency", lines.isEmpty() ? "TWD" : lines.get(lines.size() - 1).getCurrencyCode());
        model.addAttribute("components", travelComponentDAO.findByAgency(AID));
        model.addAttribute("priceTierTemplates", quotationService.listTemplates(AID));
        // 「基本報價／同業／直售加成與退傭設定」卡片「已儲存的」選項用: 這間旅行社在「計算公式管理」存的規則清單
        List<com.example.travelereasygate.entity.MarginSetting> marginSettings = marginSettingDAO.findByAgency(AID);
        model.addAttribute("marginSettings", marginSettings);

        // 目前這張報價單實際套用中的「已儲存的」規則物件 (isPresetFormulaModeActive() 為 true 時才有值,
        // 否則是 null)。改在 controller 這裡用一般 Java for 迴圈找, 不要在 Thymeleaf 樣板裡用 OGNL 的
        // `.?[...]` selection 語法混在三元運算子 (`? :`) 裡算——那個組合曾經在畫面上引發過整頁從某個位置
        // 開始整段消失的問題 (使用者回報「使用儲存公式計算會使整個頁面消失」): Thymeleaf 輸出是邊算邊
        // 往外送 (streaming) 的, `.?[...]` 選取語法本身也用了一個 `?` 字元, 跟外層三元運算子的 `?` 混在
        // 同一個屬性值裡解析很容易出錯, 一旦樣板運算式在渲染途中丟例外, 已經送出去的部分 (這個區塊之前的
        // HTML) 還是會留在瀏覽器裡, 後面全部沒了, 看起來就像「整頁消失」而不是很容易辨識的 500 錯誤頁。
        // 直接在 Java 這裡算好一個乾淨的物件丟給 model, 樣板只要判斷 null 就好, 完全不會有這個風險。
        com.example.travelereasygate.entity.MarginSetting activePreset = null;
        if (quotation.isPresetFormulaModeActive()) {
            for (com.example.travelereasygate.entity.MarginSetting ms : marginSettings) {
                if (ms.getMSID() == quotation.getMSID()) { activePreset = ms; break; }
            }
        }
        model.addAttribute("activePreset", activePreset);

        // NP／團費成本這組獨立的「已儲存的」規則物件 (isTierPresetFormulaModeActive() 為 true 時才有值) ——
        // 跟上面 activePreset 同一套查法, 但看的是 tier_formula_mode/tier_MSID 這兩個獨立欄位 (使用者要求
        // ①②③④基本報價/同業/直售/退傭這組跟⑤⑥NP/團費成本這組要能分開選各自的規則, 不再共用同一組)。
        com.example.travelereasygate.entity.MarginSetting activeTierPreset = null;
        if (quotation.isTierPresetFormulaModeActive()) {
            for (com.example.travelereasygate.entity.MarginSetting ms : marginSettings) {
                if (ms.getMSID() == quotation.getTierMSID()) { activeTierPreset = ms; break; }
            }
        }
        model.addAttribute("activeTierPreset", activeTierPreset);

        // 每一筆明細各自的級距清單, 給畫面上「這個項目有沒有設定區間價錢」用
        Map<Integer, List<com.example.travelereasygate.entity.QuotationLineTier>> tiersByLine = new LinkedHashMap<>();
        for (QuotationLine line : lines) {
            tiersByLine.put(line.getQLID(), quotationService.listTiers(line.getQLID()));
        }
        model.addAttribute("tiersByLine", tiersByLine);

        // 整團人數級距報價結果 (掛在整張報價單底下, 跟上面 tiersByLine 是不同層級)
        List<com.example.travelereasygate.entity.QuotationGroupTier> groupTiers = quotationService.listGroupTiers(QID);
        model.addAttribute("groupTiers", groupTiers);
        // 使用者要求「幣別（全部級距共用）」選了要像報價項目明細一樣整張表即時換算成該幣別——這裡查好
        // 目前這組級距共用的幣別匯率 (1 單位該幣別 = 多少台幣), 丟給樣板呼叫 tier.getMiscValueInCurrency()/
        // getNpResultInCurrency()/getTeamResultInCurrency() 當參數用 (見 QuotationGroupTier 的說明,
        // entity 本身不直接查資料庫, 匯率統一由這裡查好)。沒有級距或幣別是 TWD/空白時固定用 1 (不轉換)。
        model.addAttribute("groupTierCurrencyRate", groupTierCurrencyRate(groupTiers, AID));

        // 畫面能不能編輯 = 報價單本身是 draft (未上鎖) 而且這個角色有「製作/調整報價」的權限 (ADMIN/QUOTER)。
        // 行程編輯者 (EDITOR) 跟唯讀 (VIEWER) 只能看, 看得到金額但按鈕會被關閉。
        model.addAttribute("canEdit", quotation.isEditable() && canQuote(session));
        return null;
    }

    // POST /quotation/{qid}/settings → 調整團體人數 / 有效期限 (會觸發整張報價單重新計算 FOC)
    @PostMapping("/quotation/{qid}/settings")
    public String updateSettings(@PathVariable("qid") int QID,
                                 @RequestParam int groupSize,
                                 @RequestParam(required = false) String expiresAt,
                                 HttpSession session) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return "redirect:/login";
        if (!canQuote(session)) return "redirect:/quotation/" + QID + "?permissionError=1";

        // 團體人數變動會連動重新計算所有明細 (FOC 折抵), 交給 service 統一處理
        quotationService.updateGroupSize(QID, groupSize);

        // 有效期限只是單純寫回欄位, 不影響金額計算, 額外處理即可
        if (expiresAt != null && !expiresAt.isBlank()) {
            quotationService.setExpiresAt(QID, LocalDate.parse(expiresAt).atTime(23, 59, 59));
        }
        return "redirect:/quotation/" + QID;
    }

    // POST /quotation/{qid}/lines → 手動新增一筆報價項目
    @PostMapping("/quotation/{qid}/lines")
    public String addLine(@PathVariable("qid") int QID,
                          @RequestParam(required = false) Integer componentId,
                          @RequestParam String itemName,
                          @RequestParam(defaultValue = "other") String category,
                          @RequestParam(defaultValue = "PER_PAX") String costType,
                          @RequestParam(defaultValue = "TWD") String currencyCode,
                          @RequestParam BigDecimal unitPrice,
                          @RequestParam(defaultValue = "1") int quantity,
                          @RequestParam(required = false, defaultValue = "0") BigDecimal fuelSurcharge,
                          @RequestParam(required = false, defaultValue = "0") BigDecimal taxAmount,
                          @RequestParam(required = false, defaultValue = "0") int focRatio,
                          @RequestParam(required = false, defaultValue = "true") boolean refundable,
                          @RequestParam(required = false) String note,
                          @RequestParam(required = false, defaultValue = "edit") String back,
                          HttpSession session) {
        if (session.getAttribute("AID") == null) return "redirect:/login";
        if (!canQuote(session)) return redirectBackDenied(QID, back);

        quotationService.addLine(QID, componentId, itemName, category, costType, currencyCode, unitPrice, quantity,
                fuelSurcharge, taxAmount, focRatio, refundable, note);
        return redirectBack(QID, back);
    }

    // POST /quotation/{qid}/lines/from-component → 從元件庫快速掛一筆 (可以覆寫計費方式/幣別/單價, 沒填就沿用元件庫的預設值)
    // back="quick" 給簡易報價編輯頁用 (使用者要求「簡易報價單可以拉元件庫內容」), 不給的話 (正式報價頁原本的用法) 導回正式報價頁。
    @PostMapping("/quotation/{qid}/lines/from-component")
    public String addLineFromComponent(@PathVariable("qid") int QID,
                                       @RequestParam int componentId,
                                       @RequestParam(required = false) String costType,
                                       @RequestParam(required = false) String currencyCode,
                                       @RequestParam(required = false) java.math.BigDecimal unitPrice,
                                       @RequestParam(defaultValue = "1") int quantity,
                                       @RequestParam(required = false, defaultValue = "0") int focRatio,
                                       @RequestParam(required = false) String note,
                                       @RequestParam(required = false, defaultValue = "edit") String back,
                                       HttpSession session) {
        if (session.getAttribute("AID") == null) return "redirect:/login";
        if (!canQuote(session)) return redirectBackDenied(QID, back);

        quotationService.addLineFromComponent(QID, componentId, costType, currencyCode, unitPrice, quantity, focRatio, note);
        return redirectBack(QID, back);
    }

    // POST /quotation/{qid}/lines/{qlid}/update → 編輯報價項目
    @PostMapping("/quotation/{qid}/lines/{qlid}/update")
    public String updateLine(@PathVariable("qid") int QID,
                             @PathVariable("qlid") int QLID,
                             @RequestParam String itemName,
                             @RequestParam(defaultValue = "other") String category,
                             @RequestParam(defaultValue = "PER_PAX") String costType,
                             @RequestParam(defaultValue = "TWD") String currencyCode,
                             @RequestParam BigDecimal unitPrice,
                             @RequestParam(defaultValue = "1") int quantity,
                             @RequestParam(required = false, defaultValue = "0") BigDecimal fuelSurcharge,
                             @RequestParam(required = false, defaultValue = "0") BigDecimal taxAmount,
                             @RequestParam(required = false, defaultValue = "0") int focRatio,
                             @RequestParam(required = false, defaultValue = "true") boolean refundable,
                             @RequestParam(required = false) String note,
                             HttpSession session) {
        if (session.getAttribute("AID") == null) return "redirect:/login";
        if (!canQuote(session)) return "redirect:/quotation/" + QID + "?permissionError=1";

        quotationService.updateLine(QLID, itemName, category, costType, currencyCode, unitPrice, quantity,
                fuelSurcharge, taxAmount, focRatio, refundable, note);
        return "redirect:/quotation/" + QID;
    }

    // POST /quotation/{qid}/lines/{qlid}/delete
    @PostMapping("/quotation/{qid}/lines/{qlid}/delete")
    public String deleteLine(@PathVariable("qid") int QID, @PathVariable("qlid") int QLID,
                             @RequestParam(required = false, defaultValue = "edit") String back,
                             HttpSession session) {
        if (session.getAttribute("AID") == null) return "redirect:/login";
        if (!canQuote(session)) return redirectBackDenied(QID, back);
        quotationService.deleteLine(QLID);
        return redirectBack(QID, back);
    }

    /** back="quick" 導回簡易報價編輯頁, 其他 (預設) 導回正式報價編輯頁。共用同一批寫入端點, 只是回去的地方不同。 */
    private String redirectBack(int QID, String back) {
        return "quick".equals(back) ? "redirect:/quotation/" + QID + "/quick-edit" : "redirect:/quotation/" + QID;
    }

    // 跟 redirectBack 一樣, 但是給「權限不足擋下來」的分支專用: 額外帶一個 ?permissionError=1 的標記,
    // 讓頁面 (quotation/edit.html、quick-edit.html) 讀到這個參數時彈出「你沒有這個操作的權限」的提示——
    // 使用者反映 EDITOR 角色點「解鎖」這類按鈕時完全沒反應 (伺服器其實有正確擋下來, 只是靜靜導回原頁面,
    // 使用者以為是按鈕壞掉), 這裡統一補上明確的提示。
    private String redirectBackDenied(int QID, String back) {
        return redirectBack(QID, back) + "?permissionError=1";
    }

    // POST /quotation/{qid}/lock → 上鎖 (凍結金額, 可對外報價)
    @PostMapping("/quotation/{qid}/lock")
    public String lock(@PathVariable("qid") int QID, HttpSession session) {
        if (session.getAttribute("AID") == null) return "redirect:/login";
        if (!canQuote(session)) return "redirect:/quotation/" + QID + "?permissionError=1";
        quotationService.lock(QID);
        return "redirect:/quotation/" + QID;
    }

    // POST /quotation/{qid}/reopen → 解鎖回草稿, 繼續調整報價
    @PostMapping("/quotation/{qid}/reopen")
    public String reopen(@PathVariable("qid") int QID, HttpSession session) {
        if (session.getAttribute("AID") == null) return "redirect:/login";
        if (!canQuote(session)) return "redirect:/quotation/" + QID + "?permissionError=1";
        quotationService.reopen(QID);
        return "redirect:/quotation/" + QID;
    }

    // POST /quotation/{qid}/confirm → 客戶已確認, 轉為正式報價 (未來可接轉正式訂單流程)
    @PostMapping("/quotation/{qid}/confirm")
    public String confirm(@PathVariable("qid") int QID, HttpSession session) {
        if (session.getAttribute("AID") == null) return "redirect:/login";
        if (!canQuote(session)) return "redirect:/quotation/" + QID + "?permissionError=1";
        quotationService.confirm(QID);
        return "redirect:/quotation/" + QID;
    }

    // POST /quotation/{qid}/delete → 刪除這個報價版本 (僅 draft 狀態建議刪除, 已鎖定的建議保留歷史)
    @PostMapping("/quotation/{qid}/delete")
    public String delete(@PathVariable("qid") int QID, HttpSession session) {
        if (session.getAttribute("AID") == null) return "redirect:/login";
        Quotation quotation = quotationService.findById(QID);
        int ITID = quotation != null ? quotation.getITID() : 0;
        if (!canQuote(session)) return "redirect:/quotation/" + QID + "?permissionError=1";
        quotationService.delete(QID);
        return "redirect:/itinerary/" + ITID + "/quotations";
    }

    // ------------------------------------------------------------
    // 區間價錢 (掛在單一報價項目底下的人數級距)
    // ------------------------------------------------------------

    // POST /quotation/{qid}/lines/{qlid}/tiers → 新增一條級距 (min~max 對應價錢)
    @PostMapping("/quotation/{qid}/lines/{qlid}/tiers")
    public String addTier(@PathVariable("qid") int QID, @PathVariable("qlid") int QLID,
                          @RequestParam int minQty,
                          @RequestParam(required = false) Integer maxQty,
                          @RequestParam BigDecimal price,
                          HttpSession session) {
        if (session.getAttribute("AID") == null) return "redirect:/login";
        if (!canQuote(session)) return "redirect:/quotation/" + QID + "?permissionError=1";
        quotationService.addTier(QLID, minQty, maxQty, price);
        return "redirect:/quotation/" + QID;
    }

    // POST /quotation/{qid}/lines/{qlid}/tiers/{qltid}/delete
    @PostMapping("/quotation/{qid}/lines/{qlid}/tiers/{qltid}/delete")
    public String deleteTier(@PathVariable("qid") int QID, @PathVariable("qlid") int QLID,
                             @PathVariable("qltid") int QLTID, HttpSession session) {
        if (session.getAttribute("AID") == null) return "redirect:/login";
        if (!canQuote(session)) return "redirect:/quotation/" + QID + "?permissionError=1";
        quotationService.deleteTier(QLTID);
        return "redirect:/quotation/" + QID;
    }

    // POST /quotation/{qid}/lines/{qlid}/tiers/{qltid}/update → 編輯一條既有的級距 (下限/上限/價錢)
    @PostMapping("/quotation/{qid}/lines/{qlid}/tiers/{qltid}/update")
    public String updateTier(@PathVariable("qid") int QID, @PathVariable("qlid") int QLID,
                             @PathVariable("qltid") int QLTID,
                             @RequestParam int minQty,
                             @RequestParam(required = false) Integer maxQty,
                             @RequestParam BigDecimal price,
                             HttpSession session) {
        if (session.getAttribute("AID") == null) return "redirect:/login";
        if (!canQuote(session)) return "redirect:/quotation/" + QID + "?permissionError=1";
        quotationService.updateTier(QLTID, minQty, maxQty, price);
        return "redirect:/quotation/" + QID;
    }

    // POST /quotation/{qid}/lines/{qlid}/tier-managed → 勾選/取消「這個項目要不要出現在區間價錢管理卡片」
    @PostMapping("/quotation/{qid}/lines/{qlid}/tier-managed")
    public String updateLineTierManaged(@PathVariable("qid") int QID, @PathVariable("qlid") int QLID,
                                        @RequestParam(required = false, defaultValue = "false") boolean tierManaged,
                                        HttpSession session) {
        if (session.getAttribute("AID") == null) return "redirect:/login";
        if (!canQuote(session)) return "redirect:/quotation/" + QID + "?permissionError=1";
        quotationService.updateLineTierManaged(QLID, tierManaged);
        return "redirect:/quotation/" + QID;
    }

    // POST /quotation/{qid}/lines/{qlid}/tiers/save-template → 把這個項目目前的級距存成範本
    @PostMapping("/quotation/{qid}/lines/{qlid}/tiers/save-template")
    public ResponseEntity<String> saveTierTemplate(@PathVariable("qid") int QID, @PathVariable("qlid") int QLID,
                                                   @RequestParam String templateName, HttpSession session) {
        Integer AID = (Integer) session.getAttribute("AID");
        Integer UID = (Integer) session.getAttribute("UID");
        if (AID == null) return ResponseEntity.status(401).body("尚未登入");
        if (!canQuote(session)) return ResponseEntity.status(403).body("沒有報價權限");

        try {
            quotationService.saveLineTiersAsTemplate(QLID, AID, templateName, UID);
            return ResponseEntity.ok("ok");
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // POST /quotation/{qid}/lines/{qlid}/tiers/apply-template → 套用一組已存的範本 (整組取代原本的級距)
    @PostMapping("/quotation/{qid}/lines/{qlid}/tiers/apply-template")
    public String applyTierTemplate(@PathVariable("qid") int QID, @PathVariable("qlid") int QLID,
                                    @RequestParam int templateId, HttpSession session) {
        if (session.getAttribute("AID") == null) return "redirect:/login";
        if (!canQuote(session)) return "redirect:/quotation/" + QID + "?permissionError=1";
        quotationService.applyTemplateToLine(QLID, templateId);
        return "redirect:/quotation/" + QID;
    }

    // POST /quotation/{qid}/markup → 調整基本報價/同業/直售加成跟退傭的模式(%數/自填金額)跟數值,
    // 以及公式建構器的四層算式 (畫面上的「基本報價／同業／直售加成與退傭設定」卡片, 跟 margin-setting 同一套 FormulaEngine)
    // (5層疊加式: 基本報價=NNet+此設定, 同業價=基本報價+此設定, 直售價=同業價+此設定; 退傭=同業價×退傭% 或自填金額)
    // 每組參數都是選填, 沒帶到的那一層維持原值不變, 交給 service 判斷。
    // 公式欄位存檔前先用樣本數字驗證格式跟變數合法性 (跟 MarginSettingController#create 同一套規則), 錯的話擋下來不存,
    // 用 flash 訊息帶回錯誤原因 (這顆表單畫面上有標記 data-full-redirect, 是整頁換頁而不是 AJAX 局部刷新, flash 訊息才顯示得出來)。
    @PostMapping("/quotation/{qid}/markup")
    public String updateMarkup(@PathVariable("qid") int QID,
                               @RequestParam(required = false) String basicMarkupMode,
                               @RequestParam(required = false) java.math.BigDecimal basicMarkupValue,
                               @RequestParam(required = false) String tradeMarkupMode,
                               @RequestParam(required = false) java.math.BigDecimal tradeMarkupValue,
                               @RequestParam(required = false) String retailMarkupMode,
                               @RequestParam(required = false) java.math.BigDecimal retailMarkupValue,
                               @RequestParam(required = false) String rebateMode,
                               @RequestParam(required = false) java.math.BigDecimal rebatePct,
                               @RequestParam(required = false) String basicFormula,
                               @RequestParam(required = false) String tradeFormula,
                               @RequestParam(required = false) String retailFormula,
                               @RequestParam(required = false) String rebateFormula,
                               @RequestParam(required = false) String formulaMode,
                               @RequestParam(required = false) Integer marginSettingId,
                               HttpSession session, RedirectAttributes redirectAttributes) {
        if (session.getAttribute("AID") == null) return "redirect:/login";
        if (!canQuote(session)) return "redirect:/quotation/" + QID + "?permissionError=1";

        // 「已儲存的」模式下, ①②③④四層都改成套用選定的 MarginSetting (①如果那組規則沒填 basicFormula,
        // 交給 QuotationService 在計算時 fallback 回這張報價單自己的 basicMarkupMode/Value), 不驗證這次表單
        // 一起送過來的 basic/trade/retail/rebate 公式欄位 (這四個 dropzone 在「已儲存的」模式下畫面上是隱藏的,
        // 內容視同沒填, 而且很可能是選規則之前殘留的舊字串, 驗證了也沒意義還可能誤擋存檔)。
        boolean presetMode = "preset".equals(formulaMode);
        try {
            Map<String, BigDecimal> sample = new HashMap<>();
            sample.put("GROSS_COST", BigDecimal.valueOf(120000));
            sample.put("NET_COST", BigDecimal.valueOf(100000));
            sample.put("GROUP_SIZE", BigDecimal.valueOf(20));
            if (!presetMode) {
                BigDecimal sampleBasic = (basicFormula != null && !basicFormula.isBlank())
                        ? FormulaEngine.evaluate(basicFormula, sample) : sample.get("NET_COST");
                sample.put("BASIC_PRICE", sampleBasic);
                BigDecimal sampleTrade = (tradeFormula != null && !tradeFormula.isBlank())
                        ? FormulaEngine.evaluate(tradeFormula, sample) : sampleBasic;
                sample.put("TRADE_PRICE", sampleTrade);
                BigDecimal sampleRetail = (retailFormula != null && !retailFormula.isBlank())
                        ? FormulaEngine.evaluate(retailFormula, sample) : sampleTrade;
                sample.put("RETAIL_PRICE", sampleRetail);
                if (rebateFormula != null && !rebateFormula.isBlank()) FormulaEngine.validate(rebateFormula, sample);
            }
        } catch (FormulaEngine.FormulaException e) {
            redirectAttributes.addFlashAttribute("quotationError", "公式有誤：" + e.getMessage());
            return "redirect:/quotation/" + QID;
        }

        quotationService.updateMarkupSettings(QID, basicMarkupMode, basicMarkupValue,
                tradeMarkupMode, tradeMarkupValue, retailMarkupMode, retailMarkupValue, rebateMode, rebatePct,
                presetMode ? null : basicFormula, presetMode ? null : tradeFormula, presetMode ? null : retailFormula,
                presetMode ? null : rebateFormula, formulaMode, marginSettingId);
        return "redirect:/quotation/" + QID;
    }

    // ------------------------------------------------------------
    // 整團人數級距報價 (掛在整張報價單底下, 不是掛在單一項目)
    // ------------------------------------------------------------

    // POST /quotation/{qid}/group-tiers → 新增一個人數級距, 金額不用填, 系統依現有成本自動試算
    @PostMapping("/quotation/{qid}/group-tiers")
    public String addGroupTier(@PathVariable("qid") int QID,
                               @RequestParam int minQty,
                               @RequestParam(required = false) Integer maxQty,
                               HttpSession session) {
        if (session.getAttribute("AID") == null) return "redirect:/login";
        if (!canQuote(session)) return "redirect:/quotation/" + QID + "?permissionError=1";
        quotationService.addGroupTier(QID, minQty, maxQty);
        return "redirect:/quotation/" + QID;
    }

    // POST /quotation/{qid}/group-tiers/{qgtid}/delete
    @PostMapping("/quotation/{qid}/group-tiers/{qgtid}/delete")
    public String deleteGroupTier(@PathVariable("qid") int QID, @PathVariable("qgtid") int QGTID,
                                  HttpSession session) {
        if (session.getAttribute("AID") == null) return "redirect:/login";
        if (!canQuote(session)) return "redirect:/quotation/" + QID + "?permissionError=1";
        quotationService.deleteGroupTier(QGTID);
        return "redirect:/quotation/" + QID;
    }

    // POST /quotation/{qid}/group-tiers/headcount-mode → 「雜項的固定成本除以級距的」下限/平均/上限人數切換
    @PostMapping("/quotation/{qid}/group-tiers/headcount-mode")
    public String updateGroupTierHeadcountMode(@PathVariable("qid") int QID,
                                               @RequestParam String mode,
                                               HttpSession session) {
        if (session.getAttribute("AID") == null) return "redirect:/login";
        if (!canQuote(session)) return "redirect:/quotation/" + QID + "?permissionError=1";
        quotationService.updateGroupTierHeadcountMode(QID, mode);
        return "redirect:/quotation/" + QID;
    }

    // POST /quotation/{qid}/group-tiers/currency → 幣別（全部級距共用）, 跟下面 NP/團費成本計算式分開存,
    // 選了馬上生效 (畫面上是 onchange 自動送出, 不用按鈕)。
    @PostMapping("/quotation/{qid}/group-tiers/currency")
    public String updateGroupTierCurrency(@PathVariable("qid") int QID,
                                          @RequestParam String currency,
                                          HttpSession session) {
        if (session.getAttribute("AID") == null) return "redirect:/login";
        if (!canQuote(session)) return "redirect:/quotation/" + QID + "?permissionError=1";
        quotationService.updateGroupTierCurrency(QID, currency);
        return "redirect:/quotation/" + QID;
    }

    // POST /quotation/{qid}/group-tiers/apply-all → NP計算式/團費成本計算式, 一次套用到「所有」人數級距
    // (不用一個個進去改)。公式欄位存檔前先驗證格式, 跟markup公式建構器同一套規則。
    // tierFormulaMode/tierMarginSettingId: NP/團費成本這組獨立的「已儲存的／自填」切換 (跟①②③④基本報價/
    // 同業/直售/退傭那組的 formulaMode/marginSettingId 完全分開), 「已儲存的」模式下不驗證這次表單一起
    // 送過來的 npFormula/teamFormula (這兩個 dropzone 在「已儲存的」模式下畫面上是隱藏的, 內容視同沒填)。
    @PostMapping("/quotation/{qid}/group-tiers/apply-all")
    public String applyGroupTierFormulaSettings(@PathVariable("qid") int QID,
                                                @RequestParam(required = false) String npFormula,
                                                @RequestParam(required = false) String teamFormula,
                                                @RequestParam(required = false) String tierFormulaMode,
                                                @RequestParam(required = false) Integer tierMarginSettingId,
                                                HttpSession session, RedirectAttributes redirectAttributes) {
        if (session.getAttribute("AID") == null) return "redirect:/login";
        if (!canQuote(session)) return "redirect:/quotation/" + QID + "?permissionError=1";
        boolean tierPresetMode = "preset".equals(tierFormulaMode);
        try {
            quotationService.applyGroupTierFormulaSettings(QID,
                    tierPresetMode ? null : npFormula, tierPresetMode ? null : teamFormula,
                    tierFormulaMode, tierMarginSettingId);
        } catch (FormulaEngine.FormulaException e) {
            redirectAttributes.addFlashAttribute("quotationError", "計算式有誤：" + e.getMessage());
        }
        return "redirect:/quotation/" + QID;
    }
}