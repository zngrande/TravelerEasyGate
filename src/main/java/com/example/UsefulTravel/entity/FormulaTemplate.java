package com.example.UsefulTravel.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 存起來的一組公式範本 (例如「一般團公式」), 底下掛 1~4 列 FormulaTemplateLine
 * (basic/trade/retail/rebate 各一條)。套用到報價單後還是可以再手動微調, 不會被鎖死，
 * 邏輯跟 PriceTierTemplate 一致。
 */
@Entity
@Table(name = "formula_template")
public class FormulaTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "FTID")
    private int FTID;

    @Column(name = "AID")
    private int AID;

    @Column(name = "name")
    private String name;

    @Column(name = "created_by")
    private Integer createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public FormulaTemplate() {}

    public FormulaTemplate(int AID, String name, Integer createdBy) {
        this.AID = AID;
        this.name = name;
        this.createdBy = createdBy;
    }

    public int getFTID() { return FTID; }
    public void setFTID(int FTID) { this.FTID = FTID; }

    public int getAID() { return AID; }
    public void setAID(int AID) { this.AID = AID; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getCreatedBy() { return createdBy; }
    public void setCreatedBy(Integer createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
