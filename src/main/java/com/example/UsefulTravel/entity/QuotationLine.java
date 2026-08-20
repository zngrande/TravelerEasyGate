package com.example.UsefulTravel.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "quotation_line")
public class QuotationLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "QLID")
    private int QLID;

    @Column(name = "QID")
    private int QID;

    @Column(name = "CPID")
    private Integer CPID; // 來源元件, 自訂項目可為 NULL

    @Column(name = "source_item_id")
    private Integer sourceItemId; // 來源行程項目 (itinerary_item.IIID), 簡易報價編輯頁用來對應同一筆; 雜費/自訂項目可為 NULL

    @Column(name = "item_name")
    private String itemName;

    @Column(name = "category")
    private String category = "other"; // flight / hotel / meal / attraction / optional / other

    @Column(name = "cost_type")
    private String costType = "PER_PAX"; // PER_PAX 按人頭 / FIXED_GROUP 全團固定一口價

    @Column(name = "currency_code")
    private String currencyCode = "TWD";

    @Column(name = "exchange_rate")
    private BigDecimal exchangeRate = BigDecimal.ONE;

    @Column(name = "unit_price")
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @Column(name = "quantity")
    private int quantity = 1;

    @Column(name = "fuel_surcharge")
    private BigDecimal fuelSurcharge = BigDecimal.ZERO; // 燃油稅 (機票用)

    @Column(name = "tax_amount")
    private BigDecimal taxAmount = BigDecimal.ZERO; // 稅金 (機票用)

    @Column(name = "foc_ratio")
    private int focRatio = 0; // 每 N 人折抵 1 位, 0 = 不適用

    @Column(name = "foc_qty")
    private int focQty = 0; // 依 group_size 換算出的折抵人數快照

    @Column(name = "refundable")
    private boolean refundable = true;

    // ---- 以下皆為計算引擎產出的凍結快照 (台幣) ----
    @Column(name = "gross_cost")
    private BigDecimal grossCost = BigDecimal.ZERO;    // Net 總成本: 原始牌價金額 (單價×數量, 完全沒調整過, 還沒扣 FOC)

    @Column(name = "net_cost")
    private BigDecimal netCost = BigDecimal.ZERO;      // NNet 總淨成本: 扣掉 FOC/折讓/返利後, 真正掏出來的最終進貨成本

    @Column(name = "basic_price")
    private BigDecimal basicPrice = BigDecimal.ZERO;   // 基本報價: 以 NNet 為基準, 加上預期的基本利潤

    @Column(name = "trade_price")
    private BigDecimal tradePrice = BigDecimal.ZERO;   // 同業價 = 基本報價 + 同業預留利潤 (賣給同業的批發價)

    @Column(name = "retail_price")
    private BigDecimal retailPrice = BigDecimal.ZERO;  // 直售價 = 同業價 + 直售附加利潤 (賣給終端消費者)

    @Column(name = "rebate_amount")
    private BigDecimal rebateAmount = BigDecimal.ZERO; // 退傭金額

    @Column(name = "profit_trade")
    private BigDecimal profitTrade = BigDecimal.ZERO;  // 利潤(同業)

    @Column(name = "profit_retail")
    private BigDecimal profitRetail = BigDecimal.ZERO; // 利潤(直售)

    @Column(name = "note")
    private String note;

    @Column(name = "sort_order")
    private int sortOrder = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public QuotationLine() {}

    public int getQLID() { return QLID; }
    public void setQLID(int QLID) { this.QLID = QLID; }

    public int getQID() { return QID; }
    public void setQID(int QID) { this.QID = QID; }

    public Integer getCPID() { return CPID; }
    public void setCPID(Integer CPID) { this.CPID = CPID; }

    public Integer getSourceItemId() { return sourceItemId; }
    public void setSourceItemId(Integer sourceItemId) { this.sourceItemId = sourceItemId; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getCostType() { return costType; }
    public void setCostType(String costType) { this.costType = costType; }

    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }

    public BigDecimal getExchangeRate() { return exchangeRate; }
    public void setExchangeRate(BigDecimal exchangeRate) { this.exchangeRate = exchangeRate; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public BigDecimal getFuelSurcharge() { return fuelSurcharge; }
    public void setFuelSurcharge(BigDecimal fuelSurcharge) { this.fuelSurcharge = fuelSurcharge; }

    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }

    public int getFocRatio() { return focRatio; }
    public void setFocRatio(int focRatio) { this.focRatio = focRatio; }

    public int getFocQty() { return focQty; }
    public void setFocQty(int focQty) { this.focQty = focQty; }

    public boolean isRefundable() { return refundable; }
    public void setRefundable(boolean refundable) { this.refundable = refundable; }

    public BigDecimal getGrossCost() { return grossCost; }
    public void setGrossCost(BigDecimal grossCost) { this.grossCost = grossCost; }

    public BigDecimal getNetCost() { return netCost; }
    public void setNetCost(BigDecimal netCost) { this.netCost = netCost; }

    public BigDecimal getBasicPrice() { return basicPrice; }
    public void setBasicPrice(BigDecimal basicPrice) { this.basicPrice = basicPrice; }

    public BigDecimal getTradePrice() { return tradePrice; }
    public void setTradePrice(BigDecimal tradePrice) { this.tradePrice = tradePrice; }

    public BigDecimal getRetailPrice() { return retailPrice; }
    public void setRetailPrice(BigDecimal retailPrice) { this.retailPrice = retailPrice; }

    public BigDecimal getRebateAmount() { return rebateAmount; }
    public void setRebateAmount(BigDecimal rebateAmount) { this.rebateAmount = rebateAmount; }

    public BigDecimal getProfitTrade() { return profitTrade; }
    public void setProfitTrade(BigDecimal profitTrade) { this.profitTrade = profitTrade; }

    public BigDecimal getProfitRetail() { return profitRetail; }
    public void setProfitRetail(BigDecimal profitRetail) { this.profitRetail = profitRetail; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
