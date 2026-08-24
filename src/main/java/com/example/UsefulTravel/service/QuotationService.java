package com.example.UsefulTravel.service;

import com.example.UsefulTravel.DAO.*;
import com.example.UsefulTravel.entity.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 報價 / 財務引擎 (需求文件第三章)
 *
 * 計算公式 (對應 product_requirements.docx 3.2):
 *   單項淨成本 = 單價 × 數量（機票另加燃油稅 + 稅金）
 *   FOC 折抵人數 = floor(團體人數 ÷ foc_ratio)
 *   同業價 = 淨成本 ×（1 + 同業加成%）
 *   直售價 = 淨成本 ×（1 + 直售加成%）
 *   退傭金額 = 同業價 × 退傭%
 *   利潤（直售） = 直售價 − 淨成本
 *   利潤（同業） = 同業價 − 淨成本 − 退傭金額
 *
 * 所有金額換算成台幣後才套用加成規則, 換算用的匯率會凍結存進 quotation_line.exchange_rate,
 * 之後就算平台匯率更新, 已存在的報價單金額也不會跟著變動 (符合「金額凍結快照」的設計)。
 */
@Service
public class QuotationService {

    private static final int SCALE = 2;

    private final QuotationDAO quotationDAO;
    private final QuotationLineDAO quotationLineDAO;
    private final CurrencyDAO currencyDAO;
    private final TravelComponentDAO travelComponentDAO;
    private final QuotationLineTierDAO quotationLineTierDAO;
    private final PriceTierTemplateDAO priceTierTemplateDAO;
    private final PriceTierTemplateRowDAO priceTierTemplateRowDAO;
    private final QuotationGroupTierDAO quotationGroupTierDAO;
    private final MarginSettingDAO marginSettingDAO;

    @Autowired
    public QuotationService(QuotationDAO quotationDAO, QuotationLineDAO quotationLineDAO,
                            CurrencyDAO currencyDAO,
                            TravelComponentDAO travelComponentDAO, QuotationLineTierDAO quotationLineTierDAO,
                            PriceTierTemplateDAO priceTierTemplateDAO, PriceTierTemplateRowDAO priceTierTemplateRowDAO,
                            QuotationGroupTierDAO quotationGroupTierDAO, MarginSettingDAO marginSettingDAO) {
        this.quotationDAO = quotationDAO;
        this.quotationLineDAO = quotationLineDAO;
        this.currencyDAO = currencyDAO;
        this.travelComponentDAO = travelComponentDAO;
        this.quotationLineTierDAO = quotationLineTierDAO;
        this.priceTierTemplateDAO = priceTierTemplateDAO;
        this.priceTierTemplateRowDAO = priceTierTemplateRowDAO;
        this.quotationGroupTierDAO = quotationGroupTierDAO;
        this.marginSettingDAO = marginSettingDAO;
    }

    // ------------------------------------------------------------
    // 報價單版本管理
    // ------------------------------------------------------------

    /**
     * 新增一個報價單版本 (同一行程下 version 自動遞增)。
     * 同業/直售加成跟退傭%的初始值改成「直接帶入同一個行程上一版報價單的數字」方便微調
     * (第一版沒有上一版可帶, 全部從 0 開始) —— 不再依賴「加成規則」範本。
     */
    public Quotation createQuotation(int ITID, int AID, int groupSize, int createdBy) {
        int version = quotationDAO.nextVersion(ITID);
        Quotation q = new Quotation(ITID, AID, version, null, Math.max(groupSize, 1), createdBy);
        q.setCreatedAt(LocalDateTime.now());
        q.setUpdatedAt(LocalDateTime.now());

        List<Quotation> previousVersions = quotationDAO.findByItinerary(ITID); // 新到舊排序
        Quotation previous = previousVersions.isEmpty() ? null : previousVersions.get(0);

        q.setBasicMarkupMode(previous != null ? previous.getBasicMarkupMode() : "PERCENT");
        q.setBasicMarkupValue(previous != null ? nz(previous.getBasicMarkupValue()) : BigDecimal.ZERO);
        q.setTradeMarkupMode(previous != null ? previous.getTradeMarkupMode() : "PERCENT");
        q.setTradeMarkupValue(previous != null ? nz(previous.getTradeMarkupValue()) : BigDecimal.ZERO);
        q.setRetailMarkupMode(previous != null ? previous.getRetailMarkupMode() : "PERCENT");
        q.setRetailMarkupValue(previous != null ? nz(previous.getRetailMarkupValue()) : BigDecimal.ZERO);
        q.setRebateMode(previous != null ? previous.getRebateMode() : "PERCENT");
        q.setRebatePct(previous != null ? nz(previous.getRebatePct()) : BigDecimal.ZERO);
        quotationDAO.save(q);
        return q;
    }

    public Quotation findById(int QID) {
        return quotationDAO.findById(QID);
    }

    public List<Quotation> findByItinerary(int ITID) {
        return quotationDAO.findByItinerary(ITID);
    }

    /** 上鎖：凍結目前的金額快照, 上鎖後不得再編輯明細 (可作為對外報價的正式版本)。 */
    public void lock(int QID) {
        Quotation q = quotationDAO.findById(QID);
        if (q == null) return;
        q.setStatus("locked");
        q.setLockedAt(LocalDateTime.now());
        q.setUpdatedAt(LocalDateTime.now());
        quotationDAO.save(q);
    }

    /** 客戶簽署/確認：轉為 confirmed, 之後可作為轉正式訂單的依據。 */
    public void confirm(int QID) {
        Quotation q = quotationDAO.findById(QID);
        if (q == null) return;
        q.setStatus("confirmed");
        q.setConfirmedAt(LocalDateTime.now());
        q.setUpdatedAt(LocalDateTime.now());
        quotationDAO.save(q);
    }

    /** 解鎖回 draft, 供調整報價用 (等同「僅製作報價單」流程中的重新編輯)。 */
    public void reopen(int QID) {
        Quotation q = quotationDAO.findById(QID);
        if (q == null) return;
        q.setStatus("draft");
        q.setLockedAt(null);
        q.setUpdatedAt(LocalDateTime.now());
        quotationDAO.save(q);
    }

    public void delete(int QID) {
        Quotation q = quotationDAO.findById(QID);
        if (q == null) return;
        quotationLineDAO.deleteByQuotation(QID);
        quotationGroupTierDAO.deleteByQuotation(QID);
        quotationDAO.delete(q);
    }

    /**
     * 找這個行程目前的草稿報價單, 沒有的話就建一個。給「簡易報價編輯頁」的入口用——
     * 規劃師不用先手動去「建立新版本」, 點進簡易頁面就直接接上（或新建）目前這份還沒鎖定的草稿。
     */
    public Quotation findOrCreateDraftQuotation(int ITID, int AID, int groupSize, int createdBy) {
        for (Quotation q : quotationDAO.findByItinerary(ITID)) {
            if (q.isEditable()) return q; // findByItinerary 是新到舊排序, 第一筆 draft 就是最新的草稿
        }
        return createQuotation(ITID, AID, groupSize, createdBy);
    }

    // ------------------------------------------------------------
    // 報價單明細 (每一列都是計算引擎的輸出)
    // ------------------------------------------------------------

    /**
     * 新增一筆報價明細。若帶了 CPID (選了元件庫項目), 會用該元件的預設單價/幣別/是否可退
     * 當作初始值 (前端仍可覆寫)。加入後立刻套用計算引擎算出淨成本/同業價/直售價/利潤並存檔。
     */
    public QuotationLine addLine(int QID, Integer CPID, String itemName, String category, String costType,
                                 String currencyCode, BigDecimal unitPrice, int quantity,
                                 BigDecimal fuelSurcharge, BigDecimal taxAmount,
                                 int focRatio, Boolean refundable, String note) {
        Quotation quotation = quotationDAO.findById(QID);
        if (quotation == null) throw new IllegalArgumentException("報價單不存在");
        if (!quotation.isEditable()) throw new IllegalStateException("報價單已上鎖, 無法編輯");

        QuotationLine line = new QuotationLine();
        line.setQID(QID);
        line.setCPID(CPID);
        line.setItemName(itemName);
        line.setCategory(category == null ? "other" : category);
        line.setCostType("FIXED_GROUP".equals(costType) ? "FIXED_GROUP" : "PER_PAX");
        line.setCurrencyCode(currencyCode == null ? "TWD" : currencyCode);
        line.setUnitPrice(unitPrice == null ? BigDecimal.ZERO : unitPrice);
        line.setQuantity(Math.max(quantity, 0));
        line.setFuelSurcharge(fuelSurcharge == null ? BigDecimal.ZERO : fuelSurcharge);
        line.setTaxAmount(taxAmount == null ? BigDecimal.ZERO : taxAmount);
        line.setFocRatio(Math.max(focRatio, 0));
        line.setRefundable(refundable == null || refundable);
        line.setNote(note);
        line.setCreatedAt(LocalDateTime.now());

        List<QuotationLine> existing = quotationLineDAO.findByQuotation(QID);
        line.setSortOrder(existing.size());

        recalculateLine(line, quotation);
        quotationLineDAO.save(line);
        touchQuotation(quotation);
        return line;
    }

    /** 用既有元件庫的項目快速加一行 (對應「跳轉報價單編輯」時直接從元件庫掛項目)。 */
    public QuotationLine addLineFromComponent(int QID, int CPID, int quantity, int focRatio, String note) {
        TravelComponent component = travelComponentDAO.findById(CPID);
        if (component == null) throw new IllegalArgumentException("找不到元件");
        String category = mapComponentTypeToCategory(component.getType());
        return addLine(QID, CPID, component.getName(), category, "PER_PAX", component.getCurrencyCode(),
                component.getDefaultPrice(), quantity, BigDecimal.ZERO, BigDecimal.ZERO,
                focRatio, component.isRefundable(), note);
    }

    private String mapComponentTypeToCategory(String componentType) {
        if (componentType == null) return "other";
        switch (componentType) {
            case "flight": return "flight";
            case "meal": return "meal";
            case "hotel_grade": return "hotel";
            case "optional_tour": return "optional";
            default: return "other";
        }
    }

    public void updateLine(int QLID, String itemName, String category, String costType, String currencyCode,
                           BigDecimal unitPrice, int quantity, BigDecimal fuelSurcharge, BigDecimal taxAmount,
                           int focRatio, boolean refundable, String note) {
        QuotationLine line = quotationLineDAO.findById(QLID);
        if (line == null) throw new IllegalArgumentException("找不到報價項目");
        Quotation quotation = quotationDAO.findById(line.getQID());
        if (!quotation.isEditable()) throw new IllegalStateException("報價單已上鎖, 無法編輯");

        line.setItemName(itemName);
        line.setCategory(category == null ? "other" : category);
        line.setCostType("FIXED_GROUP".equals(costType) ? "FIXED_GROUP" : "PER_PAX");
        line.setCurrencyCode(currencyCode == null ? "TWD" : currencyCode);
        line.setUnitPrice(unitPrice == null ? BigDecimal.ZERO : unitPrice);
        line.setQuantity(Math.max(quantity, 0));
        line.setFuelSurcharge(fuelSurcharge == null ? BigDecimal.ZERO : fuelSurcharge);
        line.setTaxAmount(taxAmount == null ? BigDecimal.ZERO : taxAmount);
        line.setFocRatio(Math.max(focRatio, 0));
        line.setRefundable(refundable);
        line.setNote(note);

        recalculateLine(line, quotation);
        quotationLineDAO.save(line);
        touchQuotation(quotation);
    }

    public void deleteLine(int QLID) {
        QuotationLine line = quotationLineDAO.findById(QLID);
        if (line == null) return;
        Quotation quotation = quotationDAO.findById(line.getQID());
        if (quotation != null && !quotation.isEditable()) throw new IllegalStateException("報價單已上鎖, 無法編輯");
        quotationLineDAO.delete(line);
        if (quotation != null) touchQuotation(quotation);
    }

    // ------------------------------------------------------------
    // 簡易報價編輯頁: 幫行程裡的景點/餐廳/飯店項目直接填價錢
    // ------------------------------------------------------------

    public QuotationLine findLineByItem(int QID, int IIID) {
        return quotationLineDAO.findByQuotationAndSourceItem(QID, IIID);
    }

    /**
     * 幫某個行程項目 (景點/餐廳/飯店) 填價錢。第一次填會自動建立一筆對應的報價明細並記住連結,
     * 之後再填同一個項目會更新原本那一筆, 不會重複新增。
     * 固定按人頭計費 (數量預設等於目前團體人數)、幣別預設 TWD——這裡只求快速抓個大概價錢,
     * 幣別/FOC/區間價錢/是否可退這些細節請到正式報價頁面 (/quotation/{qid}) 再調整。
     */
    public QuotationLine upsertItemPrice(int QID, int IIID, String itemName, String category, BigDecimal unitPrice) {
        Quotation quotation = quotationDAO.findById(QID);
        if (quotation == null) throw new IllegalArgumentException("報價單不存在");
        if (!quotation.isEditable()) throw new IllegalStateException("報價單已上鎖, 無法編輯");

        QuotationLine line = quotationLineDAO.findByQuotationAndSourceItem(QID, IIID);
        if (line == null) {
            line = new QuotationLine();
            line.setQID(QID);
            line.setSourceItemId(IIID);
            line.setCostType("PER_PAX");
            line.setCurrencyCode("TWD");
            line.setQuantity(Math.max(quotation.getGroupSize(), 1));
            line.setRefundable(true);
            line.setCreatedAt(LocalDateTime.now());
            List<QuotationLine> existing = quotationLineDAO.findByQuotation(QID);
            line.setSortOrder(existing.size());
        }
        line.setItemName(itemName);
        line.setCategory(category == null ? "other" : category);
        line.setUnitPrice(unitPrice == null ? BigDecimal.ZERO : unitPrice);

        recalculateLine(line, quotation);
        quotationLineDAO.save(line);
        touchQuotation(quotation);
        return line;
    }

    /** 團體人數或加成規則變動時, 整張報價單全部重新計算 (FOC、加成、退傭都會跟著變)。 */
    public void recalculateAll(int QID) {
        Quotation quotation = quotationDAO.findById(QID);
        if (quotation == null) return;
        for (QuotationLine line : quotationLineDAO.findByQuotation(QID)) {
            recalculateLine(line, quotation);
            quotationLineDAO.save(line);
        }
        touchQuotation(quotation);
    }

    public void setExpiresAt(int QID, LocalDateTime expiresAt) {
        Quotation quotation = quotationDAO.findById(QID);
        if (quotation == null) return;
        quotation.setExpiresAt(expiresAt);
        touchQuotation(quotation);
    }

    /** 團體人數/有效期限這種基礎設定; 加成規則已移除, 團體人數變動要重新算 FOC, 交給 recalculateAll 處理。 */
    public void updateGroupSize(int QID, int groupSize) {
        Quotation quotation = quotationDAO.findById(QID);
        if (quotation == null) return;
        if (!quotation.isEditable()) throw new IllegalStateException("報價單已上鎖, 無法編輯");
        quotation.setGroupSize(Math.max(groupSize, 1));
        quotationDAO.save(quotation);
        recalculateAll(QID);
    }

    /**
     * 調整這張報價單的基本報價/同業/直售加成設定 (模式 + 數值) 跟退傭 (模式 + 數值),
     * 以及公式建構器的四層算式 (基本/同業/直售/退傭, 對應 quotation.custom_*_formula)。
     * 每張報價單獨立設定, 不依賴加成規則範本。mode 只接受 "PERCENT" 或 "AMOUNT", 其他值一律視為 PERCENT。
     * 每一組 mode/value 都是可選的 (null = 這次不改這一層)。
     * 公式參數是空字串代表「清空這一層的公式, 改回沿用上面的舊制 mode/value 當 fallback」; null 代表這次不動這一層的公式。
     *
     * formulaMode/marginSettingId: 「已儲存的／自填」切換——formulaMode="preset" 且 marginSettingId 有效時,
     * 同業/直售/退傭三層改成套用該筆「計算公式管理」規則 (MarginSetting), 不再吃這張報價單自己的
     * trade/retail/rebate 那幾組欄位 (但欄位本身不會被清掉, 只是暫時不生效, 之後改回「自填」就會繼續沿用)。
     * 基本報價沒有對應的已儲存規則, 不受這個切換影響, 一律用這張報價單自己的 basicMarkupMode/Value
     * 或 customBasicFormula。formulaMode 傳 null 代表這次不改切換狀態。
     */
    public void updateMarkupSettings(int QID, String basicMarkupMode, BigDecimal basicMarkupValue,
                                     String tradeMarkupMode, BigDecimal tradeMarkupValue,
                                     String retailMarkupMode, BigDecimal retailMarkupValue,
                                     String rebateMode, BigDecimal rebatePct,
                                     String basicFormula, String tradeFormula,
                                     String retailFormula, String rebateFormula,
                                     String formulaMode, Integer marginSettingId) {
        Quotation quotation = quotationDAO.findById(QID);
        if (quotation == null) return;
        if (!quotation.isEditable()) throw new IllegalStateException("報價單已上鎖, 無法編輯");

        if (basicMarkupValue != null) {
            quotation.setBasicMarkupMode("AMOUNT".equals(basicMarkupMode) ? "AMOUNT" : "PERCENT");
            quotation.setBasicMarkupValue(basicMarkupValue);
        }
        if (tradeMarkupValue != null) {
            quotation.setTradeMarkupMode("AMOUNT".equals(tradeMarkupMode) ? "AMOUNT" : "PERCENT");
            quotation.setTradeMarkupValue(tradeMarkupValue);
        }
        if (retailMarkupValue != null) {
            quotation.setRetailMarkupMode("AMOUNT".equals(retailMarkupMode) ? "AMOUNT" : "PERCENT");
            quotation.setRetailMarkupValue(retailMarkupValue);
        }
        if (rebatePct != null) {
            quotation.setRebateMode("AMOUNT".equals(rebateMode) ? "AMOUNT" : "PERCENT");
            quotation.setRebatePct(rebatePct);
        }
        if (basicFormula != null) quotation.setCustomBasicFormula(basicFormula.isBlank() ? null : basicFormula.trim());
        if (tradeFormula != null) quotation.setCustomTradeFormula(tradeFormula.isBlank() ? null : tradeFormula.trim());
        if (retailFormula != null) quotation.setCustomRetailFormula(retailFormula.isBlank() ? null : retailFormula.trim());
        if (rebateFormula != null) quotation.setCustomRebateFormula(rebateFormula.isBlank() ? null : rebateFormula.trim());

        if ("preset".equals(formulaMode)) {
            // 選「已儲存的」但沒選規則、或選了別間旅行社的規則 (被竄改的表單參數), 一律當作沒有真的生效,
            // 沿用 Quotation.isPresetFormulaModeActive() 同一套「formula_mode=preset 但 MSID=null 就當自填」的容錯規則,
            // 不擋下整次儲存, 只是這次「已儲存的」選不成功而已。
            MarginSetting picked = marginSettingId != null ? marginSettingDAO.findById(marginSettingId) : null;
            if (picked != null && picked.getAID() == quotation.getAID()) {
                quotation.setMSID(picked.getMSID());
            } else {
                quotation.setMSID(null);
            }
            quotation.setFormulaMode("preset");
        } else if ("custom".equals(formulaMode)) {
            quotation.setFormulaMode("custom");
        }
        // formulaMode 是其他值 (含 null) 代表這次沒有要改切換狀態, 維持原本存的 formula_mode/MSID 不動

        quotationDAO.save(quotation);
        recalculateAll(QID);
    }

    /**
     * 「已儲存的／自填」切換真正生效時 (quotation.isPresetFormulaModeActive()), 找出對應的 MarginSetting；
     * 沒生效 (自填, 或選了已儲存但規則被刪掉了) 就回傳 null, 呼叫端統一 fallback 回這張報價單自己的欄位。
     */
    private MarginSetting resolveActivePreset(Quotation quotation) {
        if (!quotation.isPresetFormulaModeActive()) return null;
        return marginSettingDAO.findById(quotation.getMSID());
    }

    // ------------------------------------------------------------
    // 區間價錢 (掛在單一報價項目底下的人數級距價錢)
    // ------------------------------------------------------------

    public List<QuotationLineTier> listTiers(int QLID) {
        return quotationLineTierDAO.findByLine(QLID);
    }

    /** 新增一條級距 (min~max 對應 price)。加完立刻重新計算這個項目, 因為目前團體人數可能剛好落在新級距裡。 */
    public QuotationLineTier addTier(int QLID, int minQty, Integer maxQty, BigDecimal price) {
        QuotationLine line = quotationLineDAO.findById(QLID);
        if (line == null) throw new IllegalArgumentException("找不到報價項目");
        Quotation quotation = quotationDAO.findById(line.getQID());
        if (!quotation.isEditable()) throw new IllegalStateException("報價單已上鎖, 無法編輯");

        List<QuotationLineTier> existing = quotationLineTierDAO.findByLine(QLID);
        QuotationLineTier tier = new QuotationLineTier(QLID, minQty, maxQty,
                price == null ? BigDecimal.ZERO : price, existing.size());
        quotationLineTierDAO.save(tier);

        recalculateLine(line, quotation);
        quotationLineDAO.save(line);
        touchQuotation(quotation);
        return tier;
    }

    public void deleteTier(int QLTID) {
        QuotationLineTier tier = quotationLineTierDAO.findById(QLTID);
        if (tier == null) return;
        QuotationLine line = quotationLineDAO.findById(tier.getQLID());
        Quotation quotation = line != null ? quotationDAO.findById(line.getQID()) : null;
        if (quotation != null && !quotation.isEditable()) throw new IllegalStateException("報價單已上鎖, 無法編輯");

        quotationLineTierDAO.delete(tier);
        if (line != null && quotation != null) {
            recalculateLine(line, quotation);
            quotationLineDAO.save(line);
            touchQuotation(quotation);
        }
    }

    /** 編輯一條既有的級距 (下限/上限/價錢)。 */
    public void updateTier(int QLTID, int minQty, Integer maxQty, BigDecimal price) {
        QuotationLineTier tier = quotationLineTierDAO.findById(QLTID);
        if (tier == null) throw new IllegalArgumentException("找不到這條級距");
        QuotationLine line = quotationLineDAO.findById(tier.getQLID());
        if (line == null) throw new IllegalArgumentException("找不到報價項目");
        Quotation quotation = quotationDAO.findById(line.getQID());
        if (!quotation.isEditable()) throw new IllegalStateException("報價單已上鎖, 無法編輯");

        tier.setMinQty(minQty);
        tier.setMaxQty(maxQty);
        tier.setPrice(price == null ? BigDecimal.ZERO : price);
        quotationLineTierDAO.save(tier);

        recalculateLine(line, quotation);
        quotationLineDAO.save(line);
        touchQuotation(quotation);
    }

    /** 「區間價錢管理」卡片是否顯示這個項目——單純畫面篩選用的 flag, 跟這個項目本身有沒有級距資料是分開的。 */
    public void updateLineTierManaged(int QLID, boolean tierManaged) {
        QuotationLine line = quotationLineDAO.findById(QLID);
        if (line == null) return;
        Quotation quotation = quotationDAO.findById(line.getQID());
        if (quotation != null && !quotation.isEditable()) throw new IllegalStateException("報價單已上鎖, 無法編輯");
        line.setTierManaged(tierManaged);
        quotationLineDAO.save(line);
        // 純粹是顯示用的 flag, 不影響任何金額計算, 不需要觸發 touchQuotation() 整單重算
    }

    /** 把某個項目目前的級距整組存成範本, 之後開新報價單可以直接套用, 不用每次重新輸入。 */
    public PriceTierTemplate saveLineTiersAsTemplate(int QLID, int AID, String name, Integer createdBy) {
        List<QuotationLineTier> tiers = quotationLineTierDAO.findByLine(QLID);
        if (tiers.isEmpty()) throw new IllegalStateException("這個項目還沒有設定任何級距, 沒有東西可以存成範本");

        PriceTierTemplate template = new PriceTierTemplate(AID, name, createdBy);
        priceTierTemplateDAO.save(template);

        int order = 0;
        for (QuotationLineTier t : tiers) {
            PriceTierTemplateRow row = new PriceTierTemplateRow(template.getPTTID(), t.getMinQty(), t.getMaxQty(), t.getPrice(), order++);
            priceTierTemplateRowDAO.save(row);
        }
        return template;
    }

    /** 套用一組範本到某個項目: 會整組取代掉這個項目原本的級距設定, 套用後畫面上仍可以再手動微調。 */
    public void applyTemplateToLine(int QLID, int PTTID) {
        QuotationLine line = quotationLineDAO.findById(QLID);
        if (line == null) throw new IllegalArgumentException("找不到報價項目");
        Quotation quotation = quotationDAO.findById(line.getQID());
        if (!quotation.isEditable()) throw new IllegalStateException("報價單已上鎖, 無法編輯");

        List<PriceTierTemplateRow> rows = priceTierTemplateRowDAO.findByTemplate(PTTID);
        if (rows.isEmpty()) throw new IllegalArgumentException("這個範本沒有任何級距內容");

        quotationLineTierDAO.deleteByLine(QLID);
        int order = 0;
        for (PriceTierTemplateRow row : rows) {
            QuotationLineTier tier = new QuotationLineTier(QLID, row.getMinQty(), row.getMaxQty(), row.getPrice(), order++);
            quotationLineTierDAO.save(tier);
        }

        recalculateLine(line, quotation);
        quotationLineDAO.save(line);
        touchQuotation(quotation);
    }

    public List<PriceTierTemplate> listTemplates(int AID) {
        return priceTierTemplateDAO.findByAgency(AID);
    }

    public void deleteTemplate(int PTTID) {
        PriceTierTemplate template = priceTierTemplateDAO.findById(PTTID);
        if (template == null) return;
        priceTierTemplateRowDAO.deleteByTemplate(PTTID);
        priceTierTemplateDAO.delete(template);
    }

    // ------------------------------------------------------------
    // 整團人數級距報價 (掛在整張報價單底下, 不同於上面「單一項目」的區間價錢)
    // ------------------------------------------------------------

    public List<QuotationGroupTier> listGroupTiers(int QID) {
        return quotationGroupTierDAO.findByQuotation(QID);
    }

    /** 新增一個人數級距 (min~max)。金額欄位不用填, 加完立刻依現有成本自動試算出結果。 */
    public QuotationGroupTier addGroupTier(int QID, int minQty, Integer maxQty) {
        Quotation quotation = quotationDAO.findById(QID);
        if (quotation == null) throw new IllegalArgumentException("報價單不存在");
        if (!quotation.isEditable()) throw new IllegalStateException("報價單已上鎖, 無法編輯");

        List<QuotationGroupTier> existing = quotationGroupTierDAO.findByQuotation(QID);
        QuotationGroupTier tier = new QuotationGroupTier(QID, Math.max(minQty, 1), maxQty, existing.size());
        quotationGroupTierDAO.save(tier);
        touchQuotation(quotation); // 會連帶觸發 recalculateGroupTiers, 把剛新增這筆的結果算出來
        return tier;
    }

    public void deleteGroupTier(int QGTID) {
        QuotationGroupTier tier = quotationGroupTierDAO.findById(QGTID);
        if (tier == null) return;
        Quotation quotation = quotationDAO.findById(tier.getQID());
        if (quotation != null && !quotation.isEditable()) throw new IllegalStateException("報價單已上鎖, 無法編輯");
        quotationGroupTierDAO.delete(tier);
        if (quotation != null) touchQuotation(quotation);
    }

    /** 「雜項的固定成本除以級距的」下限/平均/上限人數切換, 全部級距共用同一個設定 (掛在報價單上, 不是掛在單一級距)。 */
    public void updateGroupTierHeadcountMode(int QID, String mode) {
        Quotation quotation = quotationDAO.findById(QID);
        if (quotation == null) return;
        if (!quotation.isEditable()) throw new IllegalStateException("報價單已上鎖, 無法編輯");
        quotation.setGroupTierHeadcountMode(("AVERAGE".equals(mode) || "UPPER".equals(mode)) ? mode : "LOWER");
        quotationDAO.save(quotation);
        touchQuotation(quotation);
    }

    /**
     * 幣別／額外雜項金額／NP計算式／團費成本計算式: 一次套用到這張報價單「所有」人數級距, 不用一個個進去改。
     * 公式欄位驗證比照markup公式建構器的規則 (存檔前用樣本數字跑一次), 格式錯誤直接丟例外讓 controller 擋下來,
     * 不會存進一半、只有部分級距套用成功的情況。
     */
    public void applyGroupTierFormulaSettings(int QID, String currency, BigDecimal miscValue,
                                              String npFormula, String teamFormula) {
        Quotation quotation = quotationDAO.findById(QID);
        if (quotation == null) return;
        if (!quotation.isEditable()) throw new IllegalStateException("報價單已上鎖, 無法編輯");

        List<QuotationGroupTier> tiers = quotationGroupTierDAO.findByQuotation(QID);
        if (tiers.isEmpty()) return;

        String normalizedCurrency = (currency == null || currency.isBlank()) ? "TWD" : currency.trim().toUpperCase();
        String normalizedNpFormula = (npFormula == null || npFormula.isBlank()) ? null : npFormula.trim();
        String normalizedTeamFormula = (teamFormula == null || teamFormula.isBlank()) ? null : teamFormula.trim();

        // 存檔前先用樣本數字驗證兩條公式格式合不合法 (格式錯就整批擋下來, 不會有部分級距套用成功、部分沒套到)
        Map<String, BigDecimal> sample = new HashMap<>();
        sample.put("VARIABLE_COST", BigDecimal.valueOf(10000));
        sample.put("MISC", BigDecimal.valueOf(5000));
        sample.put("BASIC_PRICE", BigDecimal.valueOf(12000));
        BigDecimal sampleNp = normalizedNpFormula != null ? FormulaEngine.evaluate(normalizedNpFormula, sample) : BigDecimal.valueOf(15000);
        sample.put("NP", sampleNp);
        if (normalizedTeamFormula != null) FormulaEngine.validate(normalizedTeamFormula, sample);

        for (QuotationGroupTier tier : tiers) {
            tier.setCurrency(normalizedCurrency);
            tier.setMiscValue(miscValue == null ? BigDecimal.ZERO : miscValue);
            tier.setNpFormula(normalizedNpFormula);
            tier.setTeamFormula(normalizedTeamFormula);
            quotationGroupTierDAO.save(tier);
        }
        touchQuotation(quotation);
    }

    /**
     * 重新計算這張報價單底下「所有」人數級距的結果快照。
     * 在 touchQuotation() 裡統一呼叫, 只要報價單的成本/加成設定/人數有任何變動,
     * 所有已設定的人數級距就會跟著重新試算一次, 不用另外一個個手動觸發。
     */
    private void recalculateGroupTiers(Quotation quotation) {
        List<QuotationGroupTier> tiers = quotationGroupTierDAO.findByQuotation(quotation.getQID());
        if (tiers.isEmpty()) return;

        List<QuotationLine> lines = quotationLineDAO.findByQuotation(quotation.getQID());

        // 每人變動成本「不分級距、每個級距共用同一個值」, 只需要算一次, 不用放進迴圈裡每個級距重算一次
        BigDecimal variableCostPerPersonTwd = calculateVariableCostPerPersonTwd(quotation, lines);
        // 基本報價「每人」(用整單目前團體人數換算, 不分級距——跟每人變動成本同一個精神): 這張報價單目前的
        // 基本報價總額 (由 recalculateQuotationPricing() 分攤回每一列的 basicPrice 加總回來) 除以目前團體人數。
        // touchQuotation() 保證 recalculateQuotationPricing() 一定先跑過才會跑到這裡, 所以這裡讀到的
        // line.getBasicPrice() 一定是最新的。
        BigDecimal basicPriceTotal = lines.stream().map(line -> nz(line.getBasicPrice())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal basicPricePerPersonTwd = basicPriceTotal
                .divide(BigDecimal.valueOf(Math.max(quotation.getGroupSize(), 1)), SCALE, RoundingMode.HALF_UP);

        for (QuotationGroupTier tier : tiers) {
            applyGroupTierSnapshot(tier, quotation, lines, variableCostPerPersonTwd, basicPricePerPersonTwd);
            quotationGroupTierDAO.save(tier);
        }
    }

    /**
     * 算出單一個人數級距的結果, 寫回 tier 物件的快照欄位。這裡其實是兩組互相獨立、並存的算法:
     *
     *   (1) 總成本／每人成本／同業價（每人）／直售價（每人）／毛利率 —— 舊算法, 代表人數固定用「級距下限」
     *       (保守估算), 同業/直售加成直接乘這張報價單 (或套用中的已儲存規則) 的加成%, 沒有經過「基本報價」
     *       這一層, 純供級距試算參考。
     *   (2) 雜項（全團固定費用）／NP／團費成本 —— 對照供應商報價單常見算法的新算法：
     *       代表人數依 quotation.groupTierHeadcountMode (下限/平均/上限, 開放區間一律退回下限) 決定；
     *       雜項＝這個代表人數下「全團固定」項目的成本加總（換算台幣, 如果項目有掛區間價錢會依代表人數自動
     *       判斷單價）＋手動填的「額外雜項金額」換算成台幣；
     *       NP＝（每人變動成本＋雜項）套用 NP 計算式 (留空＝不調整, 直接等於這個和)；
     *       團費成本＝同一個基礎套用團費成本計算式 (留空＝直接等於 NP)，計算式可以引用 {VARIABLE_COST}
     *       {MISC} {BASIC_PRICE}（團費成本另外還多一個 {NP} 可用）。
     */
    private void applyGroupTierSnapshot(QuotationGroupTier tier, Quotation quotation, List<QuotationLine> lines,
                                        BigDecimal variableCostPerPersonTwd, BigDecimal basicPricePerPersonTwd) {
        // ---------- (1) 舊算法: 總成本/每人成本/同業價/直售價/毛利率, 代表人數固定用級距下限 ----------
        int legacyHeadcount = Math.max(tier.getMinQty(), 1);

        BigDecimal totalNetCost = calculateTotalNetCostForHeadcount(quotation, lines, legacyHeadcount);
        tier.setTotalNetCost(totalNetCost);

        BigDecimal netCostPerPax = totalNetCost
                .divide(BigDecimal.valueOf(legacyHeadcount), SCALE, RoundingMode.HALF_UP);
        tier.setNetCostPerPax(netCostPerPax);

        MarginSetting preset = resolveActivePreset(quotation);
        BigDecimal tradePricePerPax = preset != null
                ? applyMarkup(netCostPerPax, "PERCENT", preset.getTradeMarkupPct())
                : applyMarkup(netCostPerPax, quotation.getTradeMarkupMode(), quotation.getTradeMarkupValue());
        tier.setTradePricePerPax(tradePricePerPax);

        BigDecimal retailPricePerPax = preset != null
                ? applyMarkup(netCostPerPax, "PERCENT", preset.getRetailMarkupPct())
                : applyMarkup(netCostPerPax, quotation.getRetailMarkupMode(), quotation.getRetailMarkupValue());
        tier.setRetailPricePerPax(retailPricePerPax);

        // 毛利率 = (直售價 − 成本) ÷ 直售價 (以「每人」為單位算, 跟總價算出來的比例一樣)
        BigDecimal marginRatePct = BigDecimal.ZERO;
        if (retailPricePerPax.compareTo(BigDecimal.ZERO) > 0) {
            marginRatePct = retailPricePerPax.subtract(netCostPerPax)
                    .divide(retailPricePerPax, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
        }
        tier.setMarginRatePct(marginRatePct);

        // ---------- (2) 新算法: 雜項/NP/團費成本, 代表人數依 groupTierHeadcountMode 決定 ----------
        int representativeHeadcount = representativeHeadcountFor(tier, quotation.getGroupTierHeadcountMode());

        tier.setVariableCostPerPersonTwd(variableCostPerPersonTwd);

        BigDecimal autoMiscTwd = calculateFixedGroupCostForHeadcount(quotation, lines, representativeHeadcount);
        BigDecimal miscExchangeRate = resolveGroupTierCurrencyRate(quotation, tier.getCurrency());
        BigDecimal manualMiscTwd = nz(tier.getMiscValue()).multiply(miscExchangeRate);
        BigDecimal miscValueTwd = autoMiscTwd.add(manualMiscTwd).setScale(SCALE, RoundingMode.HALF_UP);
        tier.setMiscValueTwd(miscValueTwd);

        BigDecimal baseTwd = variableCostPerPersonTwd.add(miscValueTwd);

        Map<String, BigDecimal> tierVars = new java.util.HashMap<>();
        tierVars.put("VARIABLE_COST", variableCostPerPersonTwd);
        tierVars.put("MISC", miscValueTwd);
        tierVars.put("BASIC_PRICE", basicPricePerPersonTwd);

        BigDecimal npResultTwd = evaluateLayer(tier.getNpFormula(), tierVars, () -> baseTwd.setScale(SCALE, RoundingMode.HALF_UP));
        tier.setNpResultTwd(npResultTwd);

        tierVars.put("NP", npResultTwd);
        BigDecimal teamResultTwd = evaluateLayer(tier.getTeamFormula(), tierVars, () -> npResultTwd);
        tier.setTeamResultTwd(teamResultTwd);
    }

    /**
     * 「雜項（全團固定費用）／NP／團費成本」代表人數: 依 mode 決定要用級距的下限、平均、還是上限。
     * 開放區間 (max_qty 是 null, 「N人以上」這種) 沒有平均值/上限可算, 一律退回下限——跟 mode 選什麼無關。
     */
    private int representativeHeadcountFor(QuotationGroupTier tier, String mode) {
        int min = Math.max(tier.getMinQty(), 1);
        Integer max = tier.getMaxQty();
        if (max == null) return min; // 開放區間一律用下限
        if ("UPPER".equals(mode)) return Math.max(max, min);
        if ("AVERAGE".equals(mode)) return Math.max((min + max) / 2, min);
        return min; // 預設/"LOWER"
    }

    /**
     * 假設整團有 N 人, 只加總「全團固定」項目在這個代表人數下的成本 (換算台幣) ——
     * 跟 calculateTotalNetCostForHeadcount() 的 FIXED_GROUP 分支同一套邏輯 (數量不受 N 影響, 但如果項目本身
     * 掛了區間價錢 quotation_line_tier, 一樣依 N 判斷要用哪一段的單價, 例如遊覽車依人數自動判斷車輛大小),
     * 只是這裡只看 FIXED_GROUP, 不含 PER_PAX (那個是「每人變動成本」的範疇, 見 calculateVariableCostPerPersonTwd)。
     */
    private BigDecimal calculateFixedGroupCostForHeadcount(Quotation quotation, List<QuotationLine> lines, int N) {
        BigDecimal total = BigDecimal.ZERO;
        for (QuotationLine line : lines) {
            if (!"FIXED_GROUP".equals(line.getCostType())) continue;

            BigDecimal exchangeRate = line.getExchangeRate() != null && line.getExchangeRate().compareTo(BigDecimal.ZERO) > 0
                    ? line.getExchangeRate() : BigDecimal.ONE;

            BigDecimal resolvedUnitPrice = nz(line.getUnitPrice());
            List<QuotationLineTier> lineTiers = quotationLineTierDAO.findByLine(line.getQLID());
            QuotationLineTier matched = lineTiers.stream().filter(t -> t.matches(N)).findFirst().orElse(null);
            if (matched != null) resolvedUnitPrice = nz(matched.getPrice());

            BigDecimal perUnitCost = resolvedUnitPrice.add(nz(line.getFuelSurcharge())).add(nz(line.getTaxAmount()));
            BigDecimal lineCost = perUnitCost
                    .multiply(BigDecimal.valueOf(Math.max(line.getQuantity(), 0)))
                    .multiply(exchangeRate)
                    .setScale(SCALE, RoundingMode.HALF_UP);
            total = total.add(lineCost);
        }
        return total;
    }

    /**
     * 每人變動成本 (台幣) —— 只看「報價項目明細」裡「按人頭」項目的 NNet (已經是換算好台幣、扣過 FOC 的
     * 最終成本), 加總後除以「目前團體人數」(不是代表人數, 這個值不分級距、所有級距共用)。
     */
    private BigDecimal calculateVariableCostPerPersonTwd(Quotation quotation, List<QuotationLine> lines) {
        BigDecimal perPaxNetCostTotal = lines.stream()
                .filter(line -> !"FIXED_GROUP".equals(line.getCostType()))
                .map(line -> nz(line.getNetCost()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return perPaxNetCostTotal
                .divide(BigDecimal.valueOf(Math.max(quotation.getGroupSize(), 1)), SCALE, RoundingMode.HALF_UP);
    }

    /** 「額外雜項金額」換算台幣用的匯率: TWD／留空 = 1 (不轉換), 其他幣別查該旅行社的幣別管理設定。 */
    private BigDecimal resolveGroupTierCurrencyRate(Quotation quotation, String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank() || "TWD".equalsIgnoreCase(currencyCode)) return BigDecimal.ONE;
        Currency currency = currencyDAO.findByCode(currencyCode, quotation.getAID());
        return currency != null ? currency.getRateToTwd() : BigDecimal.ONE;
    }

    /**
     * 假設整團有 N 人 (某個級距的代表人數), 把所有項目的成本重新算一次、加總。
     *   - FIXED_GROUP (全團固定成本, 車資/導遊/領隊...): 不受 N 影響, 維持這筆項目本來填的數量。
     *     這就是「人越多, 固定成本平均下來每人越便宜」的來源。
     *   - PER_PAX (按人頭變動成本, 機票/飯店/餐/門票/保險...): 用「這筆項目原本的數量 相對於
     *     報價單目前團體人數」的比例, 等比例換算到 N 人的情境 (一般情況數量就等於目前人數,
     *     比例=1, 換算後等於 N; 如果某筆項目本來就不是 1人1份, 也能等比例縮放)。
     *   - 如果項目本身有掛「單一項目的人數級距」(quotation_line_tier), 一樣用 N 去找對應的價錢,
     *     覆蓋掉單價 —— 讓成本端的級距效果也會反映進整團的賣價試算裡。
     *   - FOC 折抵一樣用 N 去算 (跟原本 recalculateLine 用團體人數的邏輯一致)。
     */
    private BigDecimal calculateTotalNetCostForHeadcount(Quotation quotation, List<QuotationLine> lines, int N) {
        BigDecimal total = BigDecimal.ZERO;
        int currentGroupSize = Math.max(quotation.getGroupSize(), 1);

        for (QuotationLine line : lines) {
            boolean isFixedGroup = "FIXED_GROUP".equals(line.getCostType());
            BigDecimal exchangeRate = line.getExchangeRate() != null && line.getExchangeRate().compareTo(BigDecimal.ZERO) > 0
                    ? line.getExchangeRate() : BigDecimal.ONE;

            BigDecimal resolvedUnitPrice = nz(line.getUnitPrice());
            List<QuotationLineTier> lineTiers = quotationLineTierDAO.findByLine(line.getQLID());
            QuotationLineTier matched = lineTiers.stream().filter(t -> t.matches(N)).findFirst().orElse(null);
            if (matched != null) resolvedUnitPrice = nz(matched.getPrice());

            BigDecimal perUnitCost = resolvedUnitPrice.add(nz(line.getFuelSurcharge())).add(nz(line.getTaxAmount()));

            int billableQty;
            if (isFixedGroup) {
                billableQty = Math.max(line.getQuantity(), 0);
            } else {
                BigDecimal ratio = BigDecimal.valueOf(Math.max(line.getQuantity(), 0))
                        .divide(BigDecimal.valueOf(currentGroupSize), 6, RoundingMode.HALF_UP);
                int scaledQty = ratio.multiply(BigDecimal.valueOf(N)).setScale(0, RoundingMode.HALF_UP).intValue();

                int focQty = line.getFocRatio() > 0 ? N / line.getFocRatio() : 0; // floor 除法
                billableQty = Math.max(scaledQty - focQty, 0);
            }

            BigDecimal lineCost = perUnitCost
                    .multiply(BigDecimal.valueOf(billableQty))
                    .multiply(exchangeRate)
                    .setScale(SCALE, RoundingMode.HALF_UP);
            total = total.add(lineCost);
        }
        return total;
    }

    // ------------------------------------------------------------
    // 核心計算引擎
    // ------------------------------------------------------------

    /**
     * 算這一筆項目的「成本」(基本報價的其中一項): 匯率快照、FOC 折抵、區間價錢覆蓋、淨成本。
     * 注意: 同業價/直售價/退傭/利潤這幾欄不在這裡算——因為直售價現在是疊在同業價上面 (同業價+Agent利潤),
     * 這個「疊加」是整張報價單層級的關係, 不是單一項目能獨立算完的, 交給 recalculateQuotationPricing()
     * 統一在算完所有項目的成本後, 一次性算出整單的同業價/直售價, 再依each項目的成本佔比分攤回每一列。
     */
    private void recalculateLine(QuotationLine line, Quotation quotation) {
        // 匯率快照: 用該行程所屬旅行社的自訂匯率, 找不到就退回平台共用匯率, 都找不到就當 1:1
        Currency currency = currencyDAO.findByCode(line.getCurrencyCode(), quotation.getAID());
        BigDecimal exchangeRate = currency != null ? currency.getRateToTwd() : BigDecimal.ONE;
        line.setExchangeRate(exchangeRate);

        boolean isFixedGroup = "FIXED_GROUP".equals(line.getCostType());

        // FOC 折抵只對「按人頭計費」的項目有意義；全團固定一口價的項目不受人數影響, 沒有折抵名額這回事
        int focQty = 0;
        if (!isFixedGroup && line.getFocRatio() > 0) {
            focQty = quotation.getGroupSize() / line.getFocRatio(); // floor 除法 (int / int)
        }
        int billableQty = isFixedGroup ? Math.max(line.getQuantity(), 0) : Math.max(line.getQuantity() - focQty, 0);
        line.setFocQty(focQty);

        // 區間價錢: 如果這個項目有設定人數級距, 依「目前團體人數」找出對應的級距價錢, 覆蓋掉單價欄位
        // (級距價錢代表「這個人數區間下的價錢」, 找不到對應級距時退回原本填的單價, 並不擋下計算,
        //  避免級距沒涵蓋到的人數直接讓報價單算不出來; 有沒有對到級距由前端另外提示使用者留意)
        BigDecimal resolvedUnitPrice = nz(line.getUnitPrice());
        if (line.getQLID() != 0) {
            List<QuotationLineTier> tiers = quotationLineTierDAO.findByLine(line.getQLID());
            QuotationLineTier matched = tiers.stream()
                    .filter(t -> t.matches(quotation.getGroupSize()))
                    .findFirst().orElse(null);
            if (matched != null) {
                resolvedUnitPrice = nz(matched.getPrice());
            }
        }

        // 單項淨成本 = 單價 × 數量（機票另加燃油稅 + 稅金）, 換算成台幣
        BigDecimal perUnitCost = resolvedUnitPrice.add(nz(line.getFuelSurcharge())).add(nz(line.getTaxAmount()));

        // Net 總成本: 原始牌價金額, 用完整數量算, 完全沒調整過 (還沒扣 FOC 折抵這種「可扣的優惠」)
        BigDecimal grossCost = perUnitCost
                .multiply(BigDecimal.valueOf(Math.max(line.getQuantity(), 0)))
                .multiply(exchangeRate)
                .setScale(SCALE, RoundingMode.HALF_UP);
        line.setGrossCost(grossCost);

        // NNet 總淨成本: 扣掉 FOC 折抵名額後, 真正掏出來的最終進貨成本 (全團固定一口價沒有 FOC 折抵, 兩個數字會一樣)
        BigDecimal netCost = perUnitCost
                .multiply(BigDecimal.valueOf(billableQty))
                .multiply(exchangeRate)
                .setScale(SCALE, RoundingMode.HALF_UP);
        line.setNetCost(netCost);
    }

    /**
     * 整張報價單層級的定價引擎, 疊加式計算 (需求文件最新版, 5 層鏈路):
     *
     *   Net 總成本 (grossCost)   = 所有項目「原始牌價」加總 (單價×數量, 完全沒調整過)
     *   NNet 總淨成本 (netCost)  = 所有項目扣掉 FOC 折抵後的淨成本加總 (真正掏出來的最終進貨成本)
     *   基本報價 = NNet + 基本利潤     (basic_markup_mode=PERCENT: 利潤 = NNet × basic_markup_value%;
     *                                    basic_markup_mode=AMOUNT : 利潤 = basic_markup_value 這個固定金額)
     *   總同業價 = 基本報價 + 同業利潤 (trade_markup_mode 同上邏輯, 疊在「基本報價」上面而不是 NNet)
     *   總直售價 = 總同業價 + 直售(Agent)利潤 (retail_markup_mode 同上邏輯, 疊在「同業價」上面)
     *   退傭金額 = 總同業價 × 退傭%
     *
     * 算完整單的總數後, 再依「每個項目的 NNet 淨成本佔整單 NNet 的比例」把基本利潤/同業利潤/直售利潤/退傭
     * 分攤回每一列, 這樣「報價項目明細」表格上每一列加總起來就會等於整單的總數。分攤結果在各層 mode=PERCENT
     * 時, 效果跟「每項各自乘 %」數學上等價; 只有 mode=AMOUNT 時才會是「拿一筆固定金額按成本佔比分下去」。
     * NNet 淨成本全部是 0 的極端情況 (例如報價單還沒有任何項目, 或所有項目都是免費), 則平均分攤到每一列。
     */
    private void recalculateQuotationPricing(Quotation quotation) {
        List<QuotationLine> lines = quotationLineDAO.findByQuotation(quotation.getQID());
        if (lines.isEmpty()) return;

        // 用 stream 算總和 (而不是用一個會被重複賦值的迴圈變數), 這樣 netCostTotal 才會是
        // effectively final, 下面公式建構器的 lambda (() -> applyMarkup(netCostTotal, ...)) 才能直接捕捉它
        BigDecimal netCostTotal = lines.stream()
                .map(line -> nz(line.getNetCost()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 公式建構器變數鏈: NET_COST/GROUP_SIZE 一開始就有, 每算完一層就把結果補進去給下一層公式引用
        // (跟畫面上 quotation/edit.html 的公式建構器 dropzone 給的變數一一對應)。
        Map<String, BigDecimal> formulaVars = new java.util.HashMap<>();
        formulaVars.put("NET_COST", netCostTotal);
        formulaVars.put("GROUP_SIZE", BigDecimal.valueOf(quotation.getGroupSize()));

        // 基本報價沒有對應的「已儲存規則」(MarginSetting 沒有 basic 這一層), 一律用這張報價單自己的設定
        BigDecimal basicPriceTotal = evaluateLayer(quotation.getCustomBasicFormula(), formulaVars,
                () -> applyMarkup(netCostTotal, quotation.getBasicMarkupMode(), quotation.getBasicMarkupValue()));
        formulaVars.put("BASIC_PRICE", basicPriceTotal);

        // 同業/直售/退傭: 「已儲存的／自填」切換生效時 (isPresetFormulaModeActive()) 改吃 MarginSetting 的公式/百分比,
        // 沒生效就跟以前一樣完全吃這張報價單自己的 custom_*_formula / *_markup_mode / *_markup_value / rebate_*
        MarginSetting preset = resolveActivePreset(quotation);

        BigDecimal tradePriceTotal = preset != null
                ? evaluateLayer(preset.getTradeFormula(), formulaVars,
                () -> applyMarkup(basicPriceTotal, "PERCENT", preset.getTradeMarkupPct()))
                : evaluateLayer(quotation.getCustomTradeFormula(), formulaVars,
                () -> applyMarkup(basicPriceTotal, quotation.getTradeMarkupMode(), quotation.getTradeMarkupValue()));
        formulaVars.put("TRADE_PRICE", tradePriceTotal);

        BigDecimal retailPriceTotal = preset != null
                ? evaluateLayer(preset.getRetailFormula(), formulaVars,
                () -> applyMarkup(tradePriceTotal, "PERCENT", preset.getRetailMarkupPct()))
                : evaluateLayer(quotation.getCustomRetailFormula(), formulaVars,
                () -> applyMarkup(tradePriceTotal, quotation.getRetailMarkupMode(), quotation.getRetailMarkupValue()));
        formulaVars.put("RETAIL_PRICE", retailPriceTotal);

        // 退傭金額: 有填④公式 (已儲存規則的 rebate_formula, 或這張報價單自己的 custom_rebate_formula) 就直接算;
        // 沒填的話 fallback 回舊制 —— 已儲存規則一律是「同業價 × 退傭%」(MarginSetting 沒有 AMOUNT 模式);
        // 自填時 mode=PERCENT 是「同業價 × 退傭%」, mode=AMOUNT 直接是填的那個固定金額 (不是「加」在同業價上, 是退傭本身)
        BigDecimal rebateAmountTotal = preset != null
                ? evaluateLayer(preset.getRebateFormula(), formulaVars,
                () -> tradePriceTotal.multiply(pct(preset.getRebatePct())).setScale(SCALE, RoundingMode.HALF_UP))
                : evaluateLayer(quotation.getCustomRebateFormula(), formulaVars,
                () -> "AMOUNT".equals(quotation.getRebateMode())
                        ? nz(quotation.getRebatePct())
                        : tradePriceTotal.multiply(pct(quotation.getRebatePct())).setScale(SCALE, RoundingMode.HALF_UP));

        BigDecimal basicProfitTotal = basicPriceTotal.subtract(netCostTotal);     // 基本利潤 (NNet → 基本報價)
        BigDecimal companyProfitTotal = tradePriceTotal.subtract(netCostTotal);   // NNet → 同業價 的累積價差
        BigDecimal combinedMarginTotal = retailPriceTotal.subtract(netCostTotal); // NNet → 直售價 的累積價差

        boolean allZeroCost = netCostTotal.compareTo(BigDecimal.ZERO) == 0;
        int lineCount = lines.size();

        for (QuotationLine line : lines) {
            BigDecimal weight;
            if (allZeroCost) {
                weight = BigDecimal.ONE.divide(BigDecimal.valueOf(lineCount), 10, RoundingMode.HALF_UP);
            } else {
                weight = nz(line.getNetCost()).divide(netCostTotal, 10, RoundingMode.HALF_UP);
            }

            BigDecimal basicPrice = nz(line.getNetCost())
                    .add(basicProfitTotal.multiply(weight))
                    .setScale(SCALE, RoundingMode.HALF_UP);
            line.setBasicPrice(basicPrice);

            BigDecimal tradePrice = nz(line.getNetCost())
                    .add(companyProfitTotal.multiply(weight))
                    .setScale(SCALE, RoundingMode.HALF_UP);
            line.setTradePrice(tradePrice);

            BigDecimal retailPrice = nz(line.getNetCost())
                    .add(combinedMarginTotal.multiply(weight))
                    .setScale(SCALE, RoundingMode.HALF_UP);
            line.setRetailPrice(retailPrice);

            BigDecimal rebateAmount = rebateAmountTotal.multiply(weight).setScale(SCALE, RoundingMode.HALF_UP);
            line.setRebateAmount(rebateAmount);

            line.setProfitRetail(retailPrice.subtract(nz(line.getNetCost())).setScale(SCALE, RoundingMode.HALF_UP));
            line.setProfitTrade(tradePrice.subtract(nz(line.getNetCost())).subtract(rebateAmount).setScale(SCALE, RoundingMode.HALF_UP));

            quotationLineDAO.save(line);
        }
    }

    /**
     * 公式建構器單一層的算法: 這一層的公式欄位有填就用 FormulaEngine 算 (算不出來就直接丟例外讓外層知道,
     * 不要悄悄退回舊制, 避免使用者以為公式生效了其實沒有 —— 存檔前 QuotationController 已經先驗證過格式,
     * 正常情況不會在這裡才炸開); 公式欄位是空的才 fallback 呼叫 legacyCalc 算舊制 %/自填金額。
     */
    private BigDecimal evaluateLayer(String formula, Map<String, BigDecimal> vars, java.util.function.Supplier<BigDecimal> legacyCalc) {
        if (formula == null || formula.isBlank()) return legacyCalc.get();
        return FormulaEngine.evaluate(formula, vars).setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** base 加上一筆利潤: mode=PERCENT 時利潤=base×value%; mode=AMOUNT 時利潤=value 這個固定金額。 */
    private BigDecimal applyMarkup(BigDecimal base, String mode, BigDecimal value) {
        BigDecimal profit = "AMOUNT".equals(mode)
                ? nz(value)
                : base.multiply(pct(nz(value)));
        return base.add(profit).setScale(SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal pct(BigDecimal percent) {
        return nz(percent).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private void touchQuotation(Quotation quotation) {
        quotation.setUpdatedAt(LocalDateTime.now());
        quotationDAO.save(quotation);
        // 成本/加成設定/人數只要有變動, 整單的同業價/直售價都要重新算過, 分攤回每一列
        recalculateQuotationPricing(quotation);
        // 人數級距報價結果也要跟著重新試算 (這次沒有一起改成疊加算法, 維持原本各自獨立的算法)
        recalculateGroupTiers(quotation);
    }

    // ------------------------------------------------------------
    // 彙總 (報價單列表頁 / 匯出報表用)
    // ------------------------------------------------------------

    public List<QuotationLine> findLines(int QID) {
        return quotationLineDAO.findByQuotation(QID);
    }

    /** 回傳整張報價單的加總金額, key: grossCost / netCost / basicPrice / tradePrice / retailPrice / rebateAmount / profitTrade / profitRetail */
    public Map<String, BigDecimal> getTotals(int QID) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        BigDecimal grossCost = BigDecimal.ZERO, netCost = BigDecimal.ZERO, basicPrice = BigDecimal.ZERO,
                trade = BigDecimal.ZERO, retail = BigDecimal.ZERO,
                rebate = BigDecimal.ZERO, profitTrade = BigDecimal.ZERO, profitRetail = BigDecimal.ZERO;

        for (QuotationLine line : quotationLineDAO.findByQuotation(QID)) {
            grossCost = grossCost.add(nz(line.getGrossCost()));
            netCost = netCost.add(nz(line.getNetCost()));
            basicPrice = basicPrice.add(nz(line.getBasicPrice()));
            trade = trade.add(nz(line.getTradePrice()));
            retail = retail.add(nz(line.getRetailPrice()));
            rebate = rebate.add(nz(line.getRebateAmount()));
            profitTrade = profitTrade.add(nz(line.getProfitTrade()));
            profitRetail = profitRetail.add(nz(line.getProfitRetail()));
        }

        totals.put("grossCost", grossCost);
        totals.put("netCost", netCost);
        totals.put("basicPrice", basicPrice);
        totals.put("tradePrice", trade);
        totals.put("retailPrice", retail);
        totals.put("rebateAmount", rebate);
        totals.put("profitTrade", profitTrade);
        totals.put("profitRetail", profitRetail);
        return totals;
    }
}