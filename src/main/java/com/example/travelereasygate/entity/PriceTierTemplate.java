package com.example.travelereasygate.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 存起來的一組級距範本 (例如「北海道遊覽車級距」), 下次開新報價單時可以直接套用整組級距,
 * 套用後還是可以再手動微調, 不會被鎖死。
 */
@Entity
@Table(name = "price_tier_template")
public class PriceTierTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PTTID")
    private int PTTID;

    @Column(name = "AID")
    private int AID;

    @Column(name = "name")
    private String name;

    @Column(name = "created_by")
    private Integer createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public PriceTierTemplate() {}

    public PriceTierTemplate(int AID, String name, Integer createdBy) {
        this.AID = AID;
        this.name = name;
        this.createdBy = createdBy;
    }

    public int getPTTID() { return PTTID; }
    public void setPTTID(int PTTID) { this.PTTID = PTTID; }

    public int getAID() { return AID; }
    public void setAID(int AID) { this.AID = AID; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getCreatedBy() { return createdBy; }
    public void setCreatedBy(Integer createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
