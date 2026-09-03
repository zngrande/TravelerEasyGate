package com.example.travelereasygate.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * 掛在整張報價單底下的「人數級距報價結果」, e.g.
 *   15~20人 → 每人成本 26,667 / 同業價 30,667 / 直售價 34,667
 *   21~25人 → 每人成本 20,000 / ...
 * 跟 QuotationLineTier (掛在單一項目底下, 影響成本端) 是不同層級——
 * 這張是把整張報價單的成本在這個級距下重新算一次, 算出「這個級距每人賣多少錢」。
 *
 * total_net_cost / net_cost_per_pax / trade_price_per_pax / retail_price_per_pax / margin_rate_pct
 * 都是系統自動算出來的快照, 不開放手動輸入 (計算方式見 QuotationService#calculateGroupTierSnapshot)。
 */
@Entity
@Table(name = "quotation_group_tier")
public class QuotationGroupTier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "QGTID")
    private int QGTID;

    @Column(name = "QID")
    private int QID;

    @Column(name = "min_qty")
    private int minQty;

    @Column(name = "max_qty")
    private Integer maxQty; // null = 開放區間

    @Column(name = "sort_order")
    private int sortOrder = 0;

    @Column(name = "total_net_cost")
    private BigDecimal totalNetCost = BigDecimal.ZERO;

    @Column(name = "net_cost_per_pax")
    private BigDecimal netCostPerPax = BigDecimal.ZERO;

    @Column(name = "trade_price_per_pax")
    private BigDecimal tradePricePerPax = BigDecimal.ZERO;

    @Column(name = "retail_price_per_pax")
    private BigDecimal retailPricePerPax = BigDecimal.ZERO;

    @Column(name = "margin_rate_pct")
    private BigDecimal marginRatePct = BigDecimal.ZERO;

    // ------------------------------------------------------------
    // 雜項（全團固定費用）／NP／團費成本: 另一套算法, 跟上面 net_cost_per_pax/trade_price_per_pax/
    // retail_price_per_pax 這組（直接乘同業/直售加成%）並存、互不影響——這組是「每人變動成本＋雜項」換算成
    // 台幣後、再套用可自訂的計算公式，對照供應商報價單常見的算法。詳細計算方式見
    // QuotationService#applyGroupTierSnapshot()。
    // ------------------------------------------------------------

    // 這個級距試算用的幣別 (只影響「額外雜項金額」的換算; 明細項目本身的成本已經是換算好台幣的 NNet,
    // 不會再被這個幣別二次換算)。TWD 或留空 = 不轉換。
    @Column(name = "currency")
    private String currency = "TWD";

    // 手動加上去的補充雜項金額 (原始幣別, 換算成台幣前的數字), 例如明細表沒建的臨時雜支。預設 0。
    @Column(name = "misc_value")
    private BigDecimal miscValue = BigDecimal.ZERO;

    // 雜項（全團固定費用）換算成台幣後的快照 = 自動加總的全團固定項目成本(在這個級距代表人數下) + misc_value 換算成台幣
    @Column(name = "misc_value_twd")
    private BigDecimal miscValueTwd = BigDecimal.ZERO;

    // NP 計算式 (FormulaEngine 語法, 可用變數 {VARIABLE_COST} {MISC} {BASIC_PRICE}); 留空字串 = NP 直接等於
    // 「每人變動成本＋雜項」(不額外調整)。這裡刻意預設空字串、不是 null——實際的資料庫欄位 (至少在部分環境)
    // 是 NOT NULL、沒有 DEFAULT，之前這裡沒填預設值時是 Java 的 null，`addGroupTier()` 新增級距時如果沒有
    // 一起帶公式，存檔會直接因為 DB 的 NOT NULL 限制丟例外 (「Column 'np_formula' cannot be null」)、
    // 連這筆級距本身都新增不成功。空字串跟 null 在這個專案裡的判斷邏輯完全等價 (所有讀取這個欄位的地方都是
    // `formula == null || formula.isBlank()` 這種寫法，見 QuotationService.evaluateLayer())，所以這裡改成
    // 空字串不會影響「留空＝套用預設算法」這個語意，只是換一個不會被資料庫拒絕的表示方式。
    @Column(name = "np_formula", length = 500, nullable = false)
    private String npFormula = "";

    // NP 試算結果快照 (台幣)
    @Column(name = "np_result_twd")
    private BigDecimal npResultTwd = BigDecimal.ZERO;

    // 團費成本計算式 (FormulaEngine 語法, 可用變數 {VARIABLE_COST} {MISC} {BASIC_PRICE} {NP}); 留空字串 = 團費
    // 成本直接等於 NP (不額外調整)。跟上面 npFormula 同樣的理由, 預設空字串而不是 null。
    @Column(name = "team_formula", length = 500, nullable = false)
    private String teamFormula = "";

    // 團費成本試算結果快照 (台幣)
    @Column(name = "team_result_twd")
    private BigDecimal teamResultTwd = BigDecimal.ZERO;

    // 每人變動成本快照 (台幣) —— 只看「報價項目明細」裡按人頭項目的 NNet, 除以目前團體人數,
    // 不分級距, 這張報價單底下所有級距共用同一個值 (跟 net_cost_per_pax 不是同一套算法, 那個是分級距各自算的)
    @Column(name = "variable_cost_per_person_twd")
    private BigDecimal variableCostPerPersonTwd = BigDecimal.ZERO;

    public QuotationGroupTier() {}

    public QuotationGroupTier(int QID, int minQty, Integer maxQty, int sortOrder) {
        this.QID = QID;
        this.minQty = minQty;
        this.maxQty = maxQty;
        this.sortOrder = sortOrder;
    }

    public int getQGTID() { return QGTID; }
    public void setQGTID(int QGTID) { this.QGTID = QGTID; }

    public int getQID() { return QID; }
    public void setQID(int QID) { this.QID = QID; }

    public int getMinQty() { return minQty; }
    public void setMinQty(int minQty) { this.minQty = minQty; }

    public Integer getMaxQty() { return maxQty; }
    public void setMaxQty(Integer maxQty) { this.maxQty = maxQty; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public BigDecimal getTotalNetCost() { return totalNetCost; }
    public void setTotalNetCost(BigDecimal totalNetCost) { this.totalNetCost = totalNetCost; }

    public BigDecimal getNetCostPerPax() { return netCostPerPax; }
    public void setNetCostPerPax(BigDecimal netCostPerPax) { this.netCostPerPax = netCostPerPax; }

    public BigDecimal getTradePricePerPax() { return tradePricePerPax; }
    public void setTradePricePerPax(BigDecimal tradePricePerPax) { this.tradePricePerPax = tradePricePerPax; }

    public BigDecimal getRetailPricePerPax() { return retailPricePerPax; }
    public void setRetailPricePerPax(BigDecimal retailPricePerPax) { this.retailPricePerPax = retailPricePerPax; }

    public BigDecimal getMarginRatePct() { return marginRatePct; }
    public void setMarginRatePct(BigDecimal marginRatePct) { this.marginRatePct = marginRatePct; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public BigDecimal getMiscValue() { return miscValue; }
    public void setMiscValue(BigDecimal miscValue) { this.miscValue = miscValue; }

    public BigDecimal getMiscValueTwd() { return miscValueTwd; }
    public void setMiscValueTwd(BigDecimal miscValueTwd) { this.miscValueTwd = miscValueTwd; }

    public String getNpFormula() { return npFormula; }
    // 這裡刻意把 null 擋掉、一律存空字串——呼叫端 (例如 QuotationService.applyGroupTierFormulaSettings()) 判斷
    // 「使用者有沒有填公式」用的是它自己另外算好的區域變數, 不是呼叫這個 setter 之後再讀回來判斷, 所以在這裡
    // 把 null 轉成空字串, 不會影響呼叫端原本「留空＝null」的判斷邏輯, 只是確保最後真正存進這個 entity/資料庫
    // 的值不會是 null (資料庫欄位是 NOT NULL, 見上面欄位宣告的說明)。
    public void setNpFormula(String npFormula) { this.npFormula = npFormula == null ? "" : npFormula; }

    public BigDecimal getNpResultTwd() { return npResultTwd; }
    public void setNpResultTwd(BigDecimal npResultTwd) { this.npResultTwd = npResultTwd; }

    public String getTeamFormula() { return teamFormula; }
    public void setTeamFormula(String teamFormula) { this.teamFormula = teamFormula == null ? "" : teamFormula; }

    public BigDecimal getTeamResultTwd() { return teamResultTwd; }
    public void setTeamResultTwd(BigDecimal teamResultTwd) { this.teamResultTwd = teamResultTwd; }

    public BigDecimal getVariableCostPerPersonTwd() { return variableCostPerPersonTwd; }
    public void setVariableCostPerPersonTwd(BigDecimal variableCostPerPersonTwd) { this.variableCostPerPersonTwd = variableCostPerPersonTwd; }
}