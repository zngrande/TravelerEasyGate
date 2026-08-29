package com.example.UsefulTravel.controller;

import com.example.UsefulTravel.DAO.MarginSettingDAO;
import com.example.UsefulTravel.entity.MarginSetting;
import com.example.UsefulTravel.service.FormulaEngine;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 公司計算公式管理 (原「加成規則」，同業價/直售價/退傭金額改成可自訂運算式) —
 * 報價/財務引擎的基礎設定。對應需求文件 3.1 margin_setting，欄位擴充見 db/migration_formula_pricing.sql。
 */
@Controller
@RequestMapping("/margin-setting")
public class MarginSettingController {

    private final MarginSettingDAO marginSettingDAO;

    @Autowired
    public MarginSettingController(MarginSettingDAO marginSettingDAO) {
        this.marginSettingDAO = marginSettingDAO;
    }

    @GetMapping
    public String list(HttpSession session, Model model) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return "redirect:/login";

        model.addAttribute("settings", marginSettingDAO.findByAgency(AID));
        return "margin-setting/list";
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

        // 同一時間只能有一組預設規則: 這筆要設為預設的話, 先把這間旅行社原本的預設都清掉,
        // 不然「可以設定很多組預設」會讓 findDefault() 撈到哪一筆變成不確定 (看資料庫回傳順序)
        if (isDefault) {
            marginSettingDAO.clearDefault(AID);
        }

        MarginSetting setting = new MarginSetting(AID, name,
                notBlank(tradeFormula) ? tradeFormula : null,
                notBlank(retailFormula) ? retailFormula : null,
                notBlank(rebateFormula) ? rebateFormula : null);
        setting.setBasicFormula(notBlank(basicFormula) ? basicFormula : null);
        setting.setNpFormula(notBlank(npFormula) ? npFormula : null);
        setting.setTeamFormula(notBlank(teamFormula) ? teamFormula : null);
        setting.setDefault(isDefault);
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