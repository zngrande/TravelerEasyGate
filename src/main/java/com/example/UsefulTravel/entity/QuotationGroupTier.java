package com.example.UsefulTravel.entity;

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
}
