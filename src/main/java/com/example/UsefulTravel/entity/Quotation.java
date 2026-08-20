package com.example.UsefulTravel.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "quotation")
public class Quotation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "QID")
    private int QID;

    @Column(name = "ITID")
    private int ITID;

    @Column(name = "AID")
    private int AID;

    @Column(name = "version")
    private int version = 1;

    @Column(name = "MSID")
    private Integer MSID;

    // 加成規則的套用方式: "preset" = 套用 MSID 指到的已存規則 (預設) ; "custom" = 忽略 MSID,
    // 改用下面三個 custom_*_formula 欄位 —— 這張報價單專屬的公式, 不會存進公司共用的規則庫。
    @Column(name = "formula_mode")
    private String formulaMode = "preset";

    @Column(name = "custom_trade_formula", length = 500)
    private String customTradeFormula;

    @Column(name = "custom_retail_formula", length = 500)
    private String customRetailFormula;

    @Column(name = "custom_rebate_formula", length = 500)
    private String customRebateFormula;

    @Column(name = "group_size")
    private int groupSize = 1;

    @Column(name = "status")
    private String status = "draft"; // draft / locked / confirmed / expired

    @Column(name = "note")
    private String note;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "created_by")
    private int createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    public Quotation() {}

    public Quotation(int ITID, int AID, int version, Integer MSID, int groupSize, int createdBy) {
        this.ITID = ITID;
        this.AID = AID;
        this.version = version;
        this.MSID = MSID;
        this.groupSize = groupSize;
        this.createdBy = createdBy;
    }

    public int getQID() { return QID; }
    public void setQID(int QID) { this.QID = QID; }

    public int getITID() { return ITID; }
    public void setITID(int ITID) { this.ITID = ITID; }

    public int getAID() { return AID; }
    public void setAID(int AID) { this.AID = AID; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public Integer getMSID() { return MSID; }
    public void setMSID(Integer MSID) { this.MSID = MSID; }

    public String getFormulaMode() { return formulaMode; }
    public void setFormulaMode(String formulaMode) { this.formulaMode = formulaMode; }

    public String getCustomTradeFormula() { return customTradeFormula; }
    public void setCustomTradeFormula(String customTradeFormula) { this.customTradeFormula = customTradeFormula; }

    public String getCustomRetailFormula() { return customRetailFormula; }
    public void setCustomRetailFormula(String customRetailFormula) { this.customRetailFormula = customRetailFormula; }

    public String getCustomRebateFormula() { return customRebateFormula; }
    public void setCustomRebateFormula(String customRebateFormula) { this.customRebateFormula = customRebateFormula; }

    // 這張報價單目前是不是「自填公式」模式 —— 給畫面顯示/邏輯判斷用
    @Transient
    public boolean isCustomFormulaMode() {
        return "custom".equals(formulaMode);
    }

    public int getGroupSize() { return groupSize; }
    public void setGroupSize(int groupSize) { this.groupSize = groupSize; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public int getCreatedBy() { return createdBy; }
    public void setCreatedBy(int createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getLockedAt() { return lockedAt; }
    public void setLockedAt(LocalDateTime lockedAt) { this.lockedAt = lockedAt; }

    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(LocalDateTime confirmedAt) { this.confirmedAt = confirmedAt; }

    // 是否已過期 (只看時間, 不寫回資料庫的狀態欄位, 由呼叫端決定要不要正式標記成 expired)
    @Transient
    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }

    // 是否可編輯 (draft 才能改, 一旦上鎖/確認就凍結金額快照)
    @Transient
    public boolean isEditable() {
        return "draft".equals(status);
    }
}
