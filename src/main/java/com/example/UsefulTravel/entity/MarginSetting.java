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
    private BigDecimal rebatePct = BigDecimal.ZERO; // 退傭%

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

    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean aDefault) { isDefault = aDefault; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
