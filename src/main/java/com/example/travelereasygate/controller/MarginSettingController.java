package com.example.travelereasygate.controller;

import com.example.travelereasygate.DAO.MarginSettingDAO;
import com.example.travelereasygate.entity.MarginSetting;
import com.example.travelereasygate.service.FormulaEngine;
import com.example.travelereasygate.service.PermissionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 公司計算公式管理 (原「加成規則」，同業價/直售價/退傭金額改成可自訂運算式) —
 * 報價/財務引擎的基礎設定。對應需求文件 3.1 margin_setting，欄位擴充見 db/migration_formula_pricing.sql。
 */
@Controller
@RequestMapping("/margin-setting")
public class MarginSettingController {

    private final MarginSettingDAO marginSettingDAO;
    private final PermissionService permissionService;
    // 2026-09-03 修正: 原本這裡用 @Autowired 建構子注入 Spring 的 ObjectMapper Bean, 結果使用者這個專案
    // 的 Spring context 裡根本沒有 ObjectMapper 這個 Bean (啟動直接失敗: No qualifying bean of type
    // 'com.fasterxml.jackson.databind.ObjectMapper')——不確定是不是這個專案用的 Spring Boot/Spring 版本
    // 太新 (錯誤訊息裡看到 spring-boot-4.0.1、spring-7.0.2 這種目前還沒正式存在的版號, 可能是這個環境
    // 客製過的 BOM) 導致 Jackson 自動配置沒有照預期註冊 Bean, 總之不能依賴這個 Bean 一定存在。
    // 改成直接自己 new 一份最單純的 ObjectMapper, 不透過 Spring 容器——這裡序列化的 rows 只有
    // int/String/boolean 這幾種最基本的型別 (刻意排除了 MarginSetting.createdAt 這種需要額外模組
    // 才能處理的 java.time.* 型別, 見 buildSettingsJson() 註解), 一份最陽春、沒有任何額外模組的
    // ObjectMapper 就足夠處理, 不需要 Spring 那份特別註冊過 JavaTimeModule 的版本。
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    public MarginSettingController(MarginSettingDAO marginSettingDAO, PermissionService permissionService) {
        this.marginSettingDAO = marginSettingDAO;
        this.permissionService = permissionService;
    }

    // 計算公式是報價引擎的基礎設定, 對應側邊欄「計算公式」的顯示條件, 只有能報價的角色 (ADMIN/QUOTER) 能用。
    private boolean canQuote(HttpSession session) {
        return permissionService.canQuote((String) session.getAttribute("role"));
    }

    @GetMapping
    public String list(HttpSession session, Model model) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return "redirect:/login";
        if (!canQuote(session)) return "redirect:/agency/dashboard";

        List<MarginSetting> settings = marginSettingDAO.findByAgency(AID);
        model.addAttribute("settings", settings);
        // 「編輯」按鈕用: 給前端 JS 一份可以查到每筆規則原始公式字串的資料 (見 margin-setting/list.html
        // 的 RULES/RULES_BY_ID)。
        //
        // 2026-09-03 修正 (使用者回報「/margin-setting 整個不能用, 按鈕都不能點」——連跟這份資料完全
        // 無關的「價格變數／運算子」色塊清單都沒畫出來): 上一版是直接把整包 ${settings} (MarginSetting
        // entity 本身) 丟給 Thymeleaf 的 /*[[${...}]]*/ 內嵌運算式, 靠 Thymeleaf 自己內部的序列化機制
        // 轉成 JSON。問題是 MarginSetting 有 createdAt (LocalDateTime) 欄位, Thymeleaf 自己那份內部
        // 序列化機制不一定能正確處理, 一旦序列化失敗丟出例外, 樣板渲染會從那個位置整個中斷 (這個專案
        // 先前就發生過同一種 failure mode), 這個 <script> 標籤之後的所有程式碼 (包含價格變數/運算子
        // chips 產生邏輯) 就都沒有機會執行, 剛好對上使用者這次回報的症狀。
        // 改成: 完全不靠 Thymeleaf 自己的 JS 內嵌序列化, 手動組一份只含前端真的需要的欄位的輕量 DTO
        // (不含 createdAt, 也不含任何 java.time.* 型別), 序列化成 JSON 字串後直接當一般字串塞進
        // model——樣板那邊改成用 th:utext 把這個字串原封不動輸出成一行 `var RULES = [...];`, 不再讓
        // Thymeleaf 自己碰任何物件序列化, 徹底排除這個風險。
        model.addAttribute("settingsJson", buildSettingsJson(settings));
        return "margin-setting/list";
    }

    private String buildSettingsJson(List<MarginSetting> settings) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MarginSetting m : settings) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("MSID", m.getMSID());
            row.put("name", m.getName());
            row.put("basicFormula", m.getBasicFormula());
            row.put("tradeFormula", m.getTradeFormula());
            row.put("retailFormula", m.getRetailFormula());
            row.put("rebateFormula", m.getRebateFormula());
            row.put("npFormula", m.getNpFormula());
            row.put("teamFormula", m.getTeamFormula());
            row.put("defaultPricing", m.isDefaultPricing());
            row.put("defaultTier", m.isDefaultTier());
            rows.add(row);
        }
        try {
            String json = OBJECT_MAPPER.writeValueAsString(rows);
            // 保險: 如果哪天公式名稱/內容剛好包含 "</script>" 這種字串, 直接原樣輸出會把這個 <script>
            // 標籤提早結束、後面一大段程式碼變成裸露在頁面上的文字。斜線前面補一個反斜線跳脫掉,
            // 瀏覽器解析 HTML 時就不會把它當成真正的結束標籤, JSON.parse/JS 字面值本身完全不受影響。
            return json.replace("</", "<\\/");
        } catch (Exception e) {
            // 序列化萬一還是失敗, 回傳空陣列讓「編輯」功能退化成不能用, 但不能讓整個頁面的其他功能
            // (價格變數/運算子色塊、新增規則) 也一起壞掉。
            return "[]";
        }
    }

    // POST /margin-setting/new → 新增一組計算公式規則。
    // 使用者要求①②③④基本報價/同業/直售/退傭這組跟⑤⑥NP/團費成本這組要能分開存, 不用綁在同一個表單裡一起交——
    // 所以這裡兩組欄位全部改成選填, 畫面上是兩個獨立的表單各自送出 (margin-setting/list.html 的
    // 「新增報價定價規則」／「新增 NP／團費成本規則」兩張卡片), 但共用同一個 controller 方法: 送哪組欄位就存
    // 哪組, 沒送到的欄位一律是 null, 存進去的規則就是一筆「只管其中一組」的 row (畫面上顯示為
    // 「基本報價沿用報價單設定」／「NP 沿用報價單設定」這類徽章, 邏輯上跟一筆「兩組都有」的規則完全一致,
    // 只是這筆恰好某一半是空的)。tradeFormula 不再是必填 (以前規定「至少要能算出同業價」, 但現在一筆規則
    // 可能是「純 NP/團費成本規則」, 完全不管報價定價這四層, 這個限制沒有意義了)。
    @PostMapping("/new")
    public String create(@RequestParam String name,
                         @RequestParam(required = false) String basicFormula,
                         @RequestParam(required = false) String tradeFormula,
                         @RequestParam(required = false) String retailFormula,
                         @RequestParam(required = false) String rebateFormula,
                         @RequestParam(required = false) String npFormula,
                         @RequestParam(required = false) String teamFormula,
                         @RequestParam(required = false, defaultValue = "false") boolean isDefault,
                         HttpSession session, RedirectAttributes redirectAttributes) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return "redirect:/login";
        if (!canQuote(session)) return "redirect:/agency/dashboard";

        // 存檔前先用樣本數字驗證公式的格式跟變數是否合法, 錯的話擋下來不存, 回去畫面顯示錯誤。
        // 兩組公式完全獨立驗證: 這次表單沒有送到的那組欄位全部是 null, 對應的驗證區塊直接跳過, 不會因為
        // 「只送了⑤⑥那組」就硬要驗證①②③④ (反之亦然)。
        try {
            boolean hasPricingFields = notBlank(basicFormula) || notBlank(tradeFormula) || notBlank(retailFormula) || notBlank(rebateFormula);
            if (hasPricingFields) {
                Map<String, BigDecimal> sample = new HashMap<>();
                sample.put("GROSS_COST", BigDecimal.valueOf(120000));
                sample.put("NET_COST", BigDecimal.valueOf(100000));
                sample.put("GROUP_SIZE", BigDecimal.valueOf(20));
                BigDecimal sampleBasic = notBlank(basicFormula) ? FormulaEngine.evaluate(basicFormula, sample) : sample.get("NET_COST");
                sample.put("BASIC_PRICE", sampleBasic);
                BigDecimal sampleTrade = notBlank(tradeFormula) ? FormulaEngine.evaluate(tradeFormula, sample) : sampleBasic;
                sample.put("TRADE_PRICE", sampleTrade);
                if (notBlank(retailFormula)) FormulaEngine.validate(retailFormula, sample);
                if (notBlank(rebateFormula)) FormulaEngine.validate(rebateFormula, sample);
            }

            boolean hasTierFields = notBlank(npFormula) || notBlank(teamFormula);
            if (hasTierFields) {
                // NP／團費成本這組是完全獨立的變數空間 (跟上面基本/同業/直售/退傭那組無關), 用「整團人數級距報價
                // 結果」卡片同一套樣本數字驗證 (跟 QuotationService#applyGroupTierFormulaSettings() 的樣本對齊)。
                Map<String, BigDecimal> tierSample = new HashMap<>();
                tierSample.put("VARIABLE_COST", BigDecimal.valueOf(10000));
                tierSample.put("MISC", BigDecimal.valueOf(5000));
                tierSample.put("FIXED_GROUP", BigDecimal.valueOf(5000));
                tierSample.put("NET", BigDecimal.valueOf(10000));
                tierSample.put("BASIC_PRICE", BigDecimal.valueOf(12000));
                BigDecimal sampleNp = notBlank(npFormula) ? FormulaEngine.evaluate(npFormula, tierSample) : BigDecimal.valueOf(15000);
                tierSample.put("NP", sampleNp);
                if (notBlank(teamFormula)) FormulaEngine.validate(teamFormula, tierSample);
            }
        } catch (FormulaEngine.FormulaException e) {
            redirectAttributes.addFlashAttribute("formError", "公式有誤：" + e.getMessage());
            return "redirect:/margin-setting";
        }

        // 報價定價規則跟 NP／團費成本規則各自獨立的預設, 分開判斷這次送出的表單是哪一組欄位
        // (兩個獨立卡片各自送出, 一次只會有其中一組有值), 同一時間每一組各自只能有一筆預設規則:
        // 這筆要設為預設的話, 先把這間旅行社「同一組」原本的預設都清掉, 不影響另一組。
        boolean hasPricingFields = notBlank(basicFormula) || notBlank(tradeFormula) || notBlank(retailFormula) || notBlank(rebateFormula);
        boolean hasTierFields = notBlank(npFormula) || notBlank(teamFormula);
        if (isDefault && hasPricingFields) {
            marginSettingDAO.clearDefaultPricing(AID);
        }
        if (isDefault && hasTierFields) {
            marginSettingDAO.clearDefaultTier(AID);
        }

        MarginSetting setting = new MarginSetting(AID, name,
                notBlank(tradeFormula) ? tradeFormula : null,
                notBlank(retailFormula) ? retailFormula : null,
                notBlank(rebateFormula) ? rebateFormula : null);
        setting.setBasicFormula(notBlank(basicFormula) ? basicFormula : null);
        setting.setNpFormula(notBlank(npFormula) ? npFormula : null);
        setting.setTeamFormula(notBlank(teamFormula) ? teamFormula : null);
        setting.setDefaultPricing(isDefault && hasPricingFields);
        setting.setDefaultTier(isDefault && hasTierFields);
        marginSettingDAO.save(setting);
        return "redirect:/margin-setting";
    }

    // POST /margin-setting/{id}/update → 編輯一組已經存在的計算公式規則。
    // 使用者要求「每個公式要可以編輯」——以前只能刪除重建, 沒辦法直接改內容。
    // 跟 create() 一樣, ①②③④基本報價/同業/直售/退傭這組、⑤⑥NP/團費成本這組是分開的表單各自送出,
    // 用 formHalf 明確標記這次送出的是哪一組 (不能單靠欄位是不是空白判斷——使用者可能是想把
    // 這一組原本填的公式「整組清空」, 這種情況下欄位也會全部是空白, 但還是要當成「這組有送出、
    // 只是清空」處理, 不能誤判成「這次沒有動這一組」而略過, 也不能誤動到另一組完全沒被編輯到的欄位)。
    @PostMapping("/{id}/update")
    public String update(@PathVariable("id") int MSID,
                         @RequestParam String name,
                         @RequestParam String formHalf,
                         @RequestParam(required = false) String basicFormula,
                         @RequestParam(required = false) String tradeFormula,
                         @RequestParam(required = false) String retailFormula,
                         @RequestParam(required = false) String rebateFormula,
                         @RequestParam(required = false) String npFormula,
                         @RequestParam(required = false) String teamFormula,
                         @RequestParam(required = false, defaultValue = "false") boolean isDefault,
                         HttpSession session, RedirectAttributes redirectAttributes) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return "redirect:/login";
        if (!canQuote(session)) return "redirect:/agency/dashboard";

        MarginSetting setting = marginSettingDAO.findById(MSID);
        if (setting == null) {
            redirectAttributes.addFlashAttribute("formError", "找不到這組規則，可能已經被刪除過了");
            return "redirect:/margin-setting";
        }
        if (setting.getAID() != AID) {
            redirectAttributes.addFlashAttribute("formError", "沒有權限編輯這組規則");
            return "redirect:/margin-setting";
        }

        boolean editingPricing = "pricing".equals(formHalf);

        try {
            if (editingPricing) {
                boolean hasPricingFields = notBlank(basicFormula) || notBlank(tradeFormula) || notBlank(retailFormula) || notBlank(rebateFormula);
                if (hasPricingFields) {
                    Map<String, BigDecimal> sample = new HashMap<>();
                    sample.put("GROSS_COST", BigDecimal.valueOf(120000));
                    sample.put("NET_COST", BigDecimal.valueOf(100000));
                    sample.put("GROUP_SIZE", BigDecimal.valueOf(20));
                    BigDecimal sampleBasic = notBlank(basicFormula) ? FormulaEngine.evaluate(basicFormula, sample) : sample.get("NET_COST");
                    sample.put("BASIC_PRICE", sampleBasic);
                    BigDecimal sampleTrade = notBlank(tradeFormula) ? FormulaEngine.evaluate(tradeFormula, sample) : sampleBasic;
                    sample.put("TRADE_PRICE", sampleTrade);
                    if (notBlank(retailFormula)) FormulaEngine.validate(retailFormula, sample);
                    if (notBlank(rebateFormula)) FormulaEngine.validate(rebateFormula, sample);
                }
            } else {
                boolean hasTierFields = notBlank(npFormula) || notBlank(teamFormula);
                if (hasTierFields) {
                    Map<String, BigDecimal> tierSample = new HashMap<>();
                    tierSample.put("VARIABLE_COST", BigDecimal.valueOf(10000));
                    tierSample.put("MISC", BigDecimal.valueOf(5000));
                    tierSample.put("FIXED_GROUP", BigDecimal.valueOf(5000));
                    tierSample.put("NET", BigDecimal.valueOf(10000));
                    tierSample.put("BASIC_PRICE", BigDecimal.valueOf(12000));
                    BigDecimal sampleNp = notBlank(npFormula) ? FormulaEngine.evaluate(npFormula, tierSample) : BigDecimal.valueOf(15000);
                    tierSample.put("NP", sampleNp);
                    if (notBlank(teamFormula)) FormulaEngine.validate(teamFormula, tierSample);
                }
            }
        } catch (FormulaEngine.FormulaException e) {
            redirectAttributes.addFlashAttribute("formError", "公式有誤：" + e.getMessage());
            return "redirect:/margin-setting";
        }

        setting.setName(name);
        if (editingPricing) {
            setting.setBasicFormula(notBlank(basicFormula) ? basicFormula : null);
            setting.setTradeFormula(notBlank(tradeFormula) ? tradeFormula : null);
            setting.setRetailFormula(notBlank(retailFormula) ? retailFormula : null);
            setting.setRebateFormula(notBlank(rebateFormula) ? rebateFormula : null);
            if (isDefault) marginSettingDAO.clearDefaultPricing(AID);
            setting.setDefaultPricing(isDefault);
        } else {
            setting.setNpFormula(notBlank(npFormula) ? npFormula : null);
            setting.setTeamFormula(notBlank(teamFormula) ? teamFormula : null);
            if (isDefault) marginSettingDAO.clearDefaultTier(AID);
            setting.setDefaultTier(isDefault);
        }
        marginSettingDAO.save(setting);
        return "redirect:/margin-setting";
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

    // POST /margin-setting/{id}/delete → 刪除一組加成規則
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable("id") int MSID, HttpSession session,
                         org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return "redirect:/login";
        if (!canQuote(session)) return "redirect:/agency/dashboard";

        MarginSetting setting = marginSettingDAO.findById(MSID);
        if (setting == null) {
            redirectAttributes.addFlashAttribute("deleteError", "找不到這組規則，可能已經被刪除過了");
            return "redirect:/margin-setting";
        }
        if (setting.getAID() != AID) {
            redirectAttributes.addFlashAttribute("deleteError", "沒有權限刪除這組規則");
            return "redirect:/margin-setting";
        }

        // 已經被某張報價單引用的規則不能刪除 (報價單靠 MSID 反查加成% 來重新試算, 刪掉規則報價單會壞掉),
        // 請使用者先去那些報價單把加成規則換成別組, 再回來刪除
        long usage = marginSettingDAO.countQuotationUsage(MSID);
        if (usage > 0) {
            redirectAttributes.addFlashAttribute("deleteError",
                    "「" + setting.getName() + "」目前被 " + usage + " 張報價單使用中，請先到那些報價單把加成規則換成別組後再刪除");
            return "redirect:/margin-setting";
        }

        try {
            marginSettingDAO.deleteById(MSID);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("deleteError",
                    "刪除失敗：" + (e.getMessage() != null ? e.getMessage() : e.toString()));
        }
        return "redirect:/margin-setting";
    }
}