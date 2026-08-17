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
