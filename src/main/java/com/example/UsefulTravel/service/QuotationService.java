package com.example.UsefulTravel.service;

import com.example.UsefulTravel.DAO.*;
import com.example.UsefulTravel.entity.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
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
    private final MarginSettingDAO marginSettingDAO;
    private final CurrencyDAO currencyDAO;
    private final TravelComponentDAO travelComponentDAO;
    private final QuotationLineTierDAO quotationLineTierDAO;
    private final PriceTierTemplateDAO priceTierTemplateDAO;
    private final PriceTierTemplateRowDAO priceTierTemplateRowDAO;
    private final QuotationGroupTierDAO quotationGroupTierDAO;

    @Autowired
    public QuotationService(QuotationDAO quotationDAO, QuotationLineDAO quotationLineDAO,
                            MarginSettingDAO marginSettingDAO, CurrencyDAO currencyDAO,
                            TravelComponentDAO travelComponentDAO, QuotationLineTierDAO quotationLineTierDAO,
                            PriceTierTemplateDAO priceTierTemplateDAO, PriceTierTemplateRowDAO priceTierTemplateRowDAO,
                            QuotationGroupTierDAO quotationGroupTierDAO) {
        this.quotationDAO = quotationDAO;
        this.quotationLineDAO = quotationLineDAO;
        this.marginSettingDAO = marginSettingDAO;
        this.currencyDAO = currencyDAO;
        this.travelComponentDAO = travelComponentDAO;
        this.quotationLineTierDAO = quotationLineTierDAO;
        this.priceTierTemplateDAO = priceTierTemplateDAO;
        this.priceTierTemplateRowDAO = priceTierTemplateRowDAO;
        this.quotationGroupTierDAO = quotationGroupTierDAO;
    }

    // ------------------------------------------------------------
    // 報價單版本管理
    // ------------------------------------------------------------

    /** 新增一個報價單版本 (同一行程下 version 自動遞增)。 */
    public Quotation createQuotation(int ITID, int AID, Integer MSID, int groupSize, int createdBy) {
        int version = quotationDAO.nextVersion(ITID);
        Quotation q = new Quotation(ITID, AID, version, MSID, Math.max(groupSize, 1), createdBy);
        q.setCreatedAt(LocalDateTime.now());
        q.setUpdatedAt(LocalDateTime.now());
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
    public Quotation findOrCreateDraftQuotation(int ITID, int AID, Integer MSID, int groupSize, int createdBy) {
        for (Quotation q : quotationDAO.findByItinerary(ITID)) {
            if (q.isEditable()) return q; // findByItinerary 是新到舊排序, 第一筆 draft 就是最新的草稿
        }
        return createQuotation(ITID, AID, MSID, groupSize, createdBy);
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

    public void updateGroupSizeAndMargin(int QID, int groupSize, Integer MSID) {
        Quotation quotation = quotationDAO.findById(QID);
        if (quotation == null) return;
        if (!quotation.isEditable()) throw new IllegalStateException("報價單已上鎖, 無法編輯");
        quotation.setGroupSize(Math.max(groupSize, 1));
        quotation.setMSID(MSID);
        quotationDAO.save(quotation);
        recalculateAll(QID);
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

    /**
     * 重新計算這張報價單底下「所有」人數級距的結果快照。
     * 在 touchQuotation() 裡統一呼叫, 只要報價單的成本/加成規則/人數有任何變動,
     * 所有已設定的人數級距就會跟著重新試算一次, 不用另外一個個手動觸發。
     */
    private void recalculateGroupTiers(Quotation quotation) {
        List<QuotationGroupTier> tiers = quotationGroupTierDAO.findByQuotation(quotation.getQID());
        if (tiers.isEmpty()) return;

        List<QuotationLine> lines = quotationLineDAO.findByQuotation(quotation.getQID());
        MarginSetting setting = quotation.getMSID() != null ? marginSettingDAO.findById(quotation.getMSID()) : null;
        BigDecimal tradeMarkupPct = setting != null ? setting.getTradeMarkupPct() : BigDecimal.ZERO;
        BigDecimal retailMarkupPct = setting != null ? setting.getRetailMarkupPct() : BigDecimal.ZERO;

        for (QuotationGroupTier tier : tiers) {
            applyGroupTierSnapshot(tier, quotation, lines, tradeMarkupPct, retailMarkupPct);
            quotationGroupTierDAO.save(tier);
        }
    }

    /**
     * 算出單一個人數級距的結果, 寫回 tier 物件的快照欄位。
     * 代表人數 = 級距下限 (min_qty) —— 保守作法, 就算真的只湊到下限人數, 固定成本也要 cover 得住。
     */
    private void applyGroupTierSnapshot(QuotationGroupTier tier, Quotation quotation, List<QuotationLine> lines,
                                        BigDecimal tradeMarkupPct, BigDecimal retailMarkupPct) {
        int representativeHeadcount = Math.max(tier.getMinQty(), 1);

        BigDecimal totalNetCost = calculateTotalNetCostForHeadcount(quotation, lines, representativeHeadcount);
        tier.setTotalNetCost(totalNetCost);

        BigDecimal netCostPerPax = totalNetCost
                .divide(BigDecimal.valueOf(representativeHeadcount), SCALE, RoundingMode.HALF_UP);
        tier.setNetCostPerPax(netCostPerPax);

        BigDecimal tradePricePerPax = netCostPerPax
                .multiply(BigDecimal.ONE.add(pct(tradeMarkupPct)))
                .setScale(SCALE, RoundingMode.HALF_UP);
        tier.setTradePricePerPax(tradePricePerPax);

        BigDecimal retailPricePerPax = netCostPerPax
                .multiply(BigDecimal.ONE.add(pct(retailMarkupPct)))
                .setScale(SCALE, RoundingMode.HALF_UP);
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

    private void recalculateLine(QuotationLine line, Quotation quotation) {
        MarginSetting setting = quotation.getMSID() != null ? marginSettingDAO.findById(quotation.getMSID()) : null;
        BigDecimal tradeMarkupPct = setting != null ? setting.getTradeMarkupPct() : BigDecimal.ZERO;
        BigDecimal retailMarkupPct = setting != null ? setting.getRetailMarkupPct() : BigDecimal.ZERO;
        BigDecimal rebatePct = setting != null ? setting.getRebatePct() : BigDecimal.ZERO;

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

        // 單項淨成本 = 單價 × 數量（機票另加燃油稅 + 稅金）, 換算成台幣後扣 FOC 名額 (全團固定則不扣 FOC, 只看數量)
        BigDecimal perUnitCost = resolvedUnitPrice.add(nz(line.getFuelSurcharge())).add(nz(line.getTaxAmount()));
        BigDecimal netCost = perUnitCost
                .multiply(BigDecimal.valueOf(billableQty))
                .multiply(exchangeRate)
                .setScale(SCALE, RoundingMode.HALF_UP);
        line.setNetCost(netCost);

        // 同業價 = 淨成本 ×（1 + 同業加成%）
        BigDecimal tradePrice = netCost
                .multiply(BigDecimal.ONE.add(pct(tradeMarkupPct)))
                .setScale(SCALE, RoundingMode.HALF_UP);
        line.setTradePrice(tradePrice);

        // 直售價 = 淨成本 ×（1 + 直售加成%）
        BigDecimal retailPrice = netCost
                .multiply(BigDecimal.ONE.add(pct(retailMarkupPct)))
                .setScale(SCALE, RoundingMode.HALF_UP);
        line.setRetailPrice(retailPrice);

        // 退傭金額 = 同業價 × 退傭%
        BigDecimal rebateAmount = tradePrice.multiply(pct(rebatePct)).setScale(SCALE, RoundingMode.HALF_UP);
        line.setRebateAmount(rebateAmount);

        // 利潤（直售） = 直售價 − 淨成本
        line.setProfitRetail(retailPrice.subtract(netCost).setScale(SCALE, RoundingMode.HALF_UP));

        // 利潤（同業） = 同業價 − 淨成本 − 退傭金額
        line.setProfitTrade(tradePrice.subtract(netCost).subtract(rebateAmount).setScale(SCALE, RoundingMode.HALF_UP));
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
        // 成本/加成規則/人數只要有變動, 所有已設定的「整團人數級距」都要跟著重新試算一次
        recalculateGroupTiers(quotation);
    }

    // ------------------------------------------------------------
    // 彙總 (報價單列表頁 / 匯出報表用)
    // ------------------------------------------------------------

    public List<QuotationLine> findLines(int QID) {
        return quotationLineDAO.findByQuotation(QID);
    }

    /** 回傳整張報價單的加總金額, key: netCost / tradePrice / retailPrice / rebate / profitTrade / profitRetail */
    public Map<String, BigDecimal> getTotals(int QID) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        BigDecimal netCost = BigDecimal.ZERO, trade = BigDecimal.ZERO, retail = BigDecimal.ZERO,
                rebate = BigDecimal.ZERO, profitTrade = BigDecimal.ZERO, profitRetail = BigDecimal.ZERO;

        for (QuotationLine line : quotationLineDAO.findByQuotation(QID)) {
            netCost = netCost.add(nz(line.getNetCost()));
            trade = trade.add(nz(line.getTradePrice()));
            retail = retail.add(nz(line.getRetailPrice()));
            rebate = rebate.add(nz(line.getRebateAmount()));
            profitTrade = profitTrade.add(nz(line.getProfitTrade()));
            profitRetail = profitRetail.add(nz(line.getProfitRetail()));
        }

        totals.put("netCost", netCost);
        totals.put("tradePrice", trade);
        totals.put("retailPrice", retail);
        totals.put("rebateAmount", rebate);
        totals.put("profitTrade", profitTrade);
        totals.put("profitRetail", profitRetail);
        return totals;
    }
}