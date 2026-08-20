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
    // tradeFormula 必填 (至少要能算出同業價), retailFormula/rebateFormula 選填
    // (空白代表這個規則不提供直售價/退傭金額, 沿用時視同 0)。
    @PostMapping("/new")
    public String create(@RequestParam String name,
                         @RequestParam String tradeFormula,
                         @RequestParam(required = false) String retailFormula,
                         @RequestParam(required = false) String rebateFormula,
                         @RequestParam(required = false, defaultValue = "false") boolean isDefault,
                         HttpSession session, RedirectAttributes redirectAttributes) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return "redirect:/login";

        // 存檔前先用樣本數字驗證三個公式的格式跟變數是否合法, 錯的話擋下來不存, 回去畫面顯示錯誤
        try {
            Map<String, BigDecimal> sample = new HashMap<>();
            sample.put("NET_COST", BigDecimal.valueOf(100000));
            sample.put("GROUP_SIZE", BigDecimal.valueOf(20));
            BigDecimal sampleTrade = FormulaEngine.evaluate(tradeFormula, sample);
            sample.put("TRADE_PRICE", sampleTrade);
            if (retailFormula != null && !retailFormula.isBlank()) FormulaEngine.validate(retailFormula, sample);
            if (rebateFormula != null && !rebateFormula.isBlank()) FormulaEngine.validate(rebateFormula, sample);
        } catch (FormulaEngine.FormulaException e) {
            redirectAttributes.addFlashAttribute("formError", "公式有誤：" + e.getMessage());
            return "redirect:/margin-setting";
        }

        // 同一時間只能有一組預設規則: 這筆要設為預設的話, 先把這間旅行社原本的預設都清掉,
        // 不然「可以設定很多組預設」會讓 findDefault() 撈到哪一筆變成不確定 (看資料庫回傳順序)
        if (isDefault) {
            marginSettingDAO.clearDefault(AID);
        }

        MarginSetting setting = new MarginSetting(AID, name, tradeFormula,
                (retailFormula == null || retailFormula.isBlank()) ? null : retailFormula,
                (rebateFormula == null || rebateFormula.isBlank()) ? null : rebateFormula);
        setting.setDefault(isDefault);
        marginSettingDAO.save(setting);
        return "redirect:/margin-setting";
    }

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