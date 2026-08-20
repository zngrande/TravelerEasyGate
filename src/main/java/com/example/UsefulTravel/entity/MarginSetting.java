package com.example.UsefulTravel.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "margin_setting")
public class MarginSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MSID")
    private int MSID;

    @Column(name = "AID")
    private int AID;

    @Column(name = "name")
    private String name;

    @Column(name = "trade_markup_pct")
    private BigDecimal tradeMarkupPct = BigDecimal.ZERO; // 同業加成%

    @Column(name = "retail_markup_pct")
    private BigDecimal retailMarkupPct = BigDecimal.ZERO; // 直售加成%

    @Column(name = "rebate_pct")
    private BigDecimal rebatePct = BigDecimal.ZERO; // 退傭% (舊制% —— 保留給還沒轉成公式的舊資料 fallback 用)

    // ------------------------------------------------------------
    // 計算公式 (取代上面三個 %欄位): 用價格變數 + 運算子組成的運算式字串,
    // 例如 "{NET_COST} * 1.15 + 2000"。這三個欄位 nullable，空白代表這組規則
    // 還是舊制 %，QuotationService 算價錢時會自動 fallback 回上面的 %欄位。
    // ------------------------------------------------------------
    @Column(name = "trade_formula", length = 500)
    private String tradeFormula; // 同業價公式, 可用變數: {NET_COST} {GROUP_SIZE}

    @Column(name = "retail_formula", length = 500)
    private String retailFormula; // 直售價公式, 可用變數: {NET_COST} {GROUP_SIZE} {TRADE_PRICE}

    @Column(name = "rebate_formula", length = 500)
    private String rebateFormula; // 退傭金額公式, 可用變數: {NET_COST} {GROUP_SIZE} {TRADE_PRICE}

    @Column(name = "is_default")
    private boolean isDefault = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public MarginSetting() {}

    public MarginSetting(int AID, String name, BigDecimal tradeMarkupPct, BigDecimal retailMarkupPct, BigDecimal rebatePct) {
        this.AID = AID;
        this.name = name;
        this.tradeMarkupPct = tradeMarkupPct;
        this.retailMarkupPct = retailMarkupPct;
        this.rebatePct = rebatePct;
    }

    public MarginSetting(int AID, String name, String tradeFormula, String retailFormula, String rebateFormula) {
        this.AID = AID;
        this.name = name;
        this.tradeFormula = tradeFormula;
        this.retailFormula = retailFormula;
        this.rebateFormula = rebateFormula;
    }

    public int getMSID() { return MSID; }
    public void setMSID(int MSID) { this.MSID = MSID; }

    public int getAID() { return AID; }
    public void setAID(int AID) { this.AID = AID; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BigDecimal getTradeMarkupPct() { return tradeMarkupPct; }
    public void setTradeMarkupPct(BigDecimal tradeMarkupPct) { this.tradeMarkupPct = tradeMarkupPct; }

    public BigDecimal getRetailMarkupPct() { return retailMarkupPct; }
    public void setRetailMarkupPct(BigDecimal retailMarkupPct) { this.retailMarkupPct = retailMarkupPct; }

    public BigDecimal getRebatePct() { return rebatePct; }
    public void setRebatePct(BigDecimal rebatePct) { this.rebatePct = rebatePct; }

    public String getTradeFormula() { return tradeFormula; }
    public void setTradeFormula(String tradeFormula) { this.tradeFormula = tradeFormula; }

    public String getRetailFormula() { return retailFormula; }
    public void setRetailFormula(String retailFormula) { this.retailFormula = retailFormula; }

    public String getRebateFormula() { return rebateFormula; }
    public void setRebateFormula(String rebateFormula) { this.rebateFormula = rebateFormula; }

    // 這組規則是不是「公式制」(只要三個公式欄位有任一個填了字就算) —— 給畫面顯示/邏輯判斷用
    @Transient
    public boolean isFormulaMode() {
        return notBlank(tradeFormula) || notBlank(retailFormula) || notBlank(rebateFormula);
    }

    // 給下拉選單/清單用的一行摘要, 公式制顯示公式字串, 舊制% 就顯示百分比, 兩種資料可以混著出現不會壞畫面
    @Transient
    public String getSummary() {
        String trade = notBlank(tradeFormula) ? tradeFormula : ("同業+" + tradeMarkupPct + "%");
        String retail = notBlank(retailFormula) ? retailFormula : ("直售+" + retailMarkupPct + "%");
        String rebate = notBlank(rebateFormula) ? rebateFormula : ("退傭" + rebatePct + "%");
        return name + "（" + trade + " / " + retail + " / " + rebate + "）";
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean aDefault) { isDefault = aDefault; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
