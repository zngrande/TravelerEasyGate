package com.example.travelereasygate.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "component")
public class TravelComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CPID")
    private int CPID;

    @Column(name = "AID")
    private int AID;

    @Column(name = "type")
    private String type; // flight / bus / guide / insurance / meal / hotel_grade / ticket / optional_tour / other
                          // (固定分類清單見 TravelComponentController.TYPE_LABELS)

    @Column(name = "name")
    private String name;

    @Column(name = "default_price")
    private BigDecimal defaultPrice;

    @Column(name = "description")
    private String description;

    @Column(name = "currency_code")
    private String currencyCode = "TWD";

    @Column(name = "refundable")
    private boolean refundable = true;

    // 使用者要求「元件庫加入按人頭或全團固定一口價」, 跟報價單項目 (QuotationLine.costType) 同一套值,
    // 拉進報價單時直接沿用這裡填的計費方式當預設值, 使用者還是可以在報價單裡改。
    @Column(name = "cost_type")
    private String costType = "PER_PAX"; // PER_PAX 按人頭 / FIXED_GROUP 全團固定一口價

    public TravelComponent() {}

    public TravelComponent(int AID, String type, String name, BigDecimal defaultPrice, String description) {
        this.AID = AID;
        this.type = type;
        this.name = name;
        this.defaultPrice = defaultPrice;
        this.description = description;
    }

    public int getCPID() { return CPID; }
    public void setCPID(int CPID) { this.CPID = CPID; }

    public int getAID() { return AID; }
    public void setAID(int AID) { this.AID = AID; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BigDecimal getDefaultPrice() { return defaultPrice; }
    public void setDefaultPrice(BigDecimal defaultPrice) { this.defaultPrice = defaultPrice; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }

    public boolean isRefundable() { return refundable; }
    public void setRefundable(boolean refundable) { this.refundable = refundable; }

    public String getCostType() { return costType; }
    public void setCostType(String costType) { this.costType = (costType == null || costType.isBlank()) ? "PER_PAX" : costType; }
}
