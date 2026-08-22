package com.example.UsefulTravel.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

// 記錄「某間旅行社把共用庫的某筆景點改成自己專屬的版本」這件事, 讓共用庫本身完全不會被異動:
// - 編輯共用庫景點時: 複製一份成為這間旅行社自己的 POI (overridePid 指向新複本), 之後這間旅行社
//   看到的會是自己的複本, 共用庫原始那筆對這間旅行社來說會被過濾掉 (其他旅行社完全不受影響)。
// - 刪除共用庫景點時: 沒有複本可以指, overridePid 是 null, 純粹代表「這間旅行社選擇隱藏這筆」。
// 一間旅行社對同一筆共用庫景點只會有一筆紀錄 (AID + originalPid 唯一), 見 migration_poi_override.sql。
@Entity
@Table(name = "poi_override")
public class PoiOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "OID")
    private int OID;

    @Column(name = "AID")
    private int AID;

    @Column(name = "original_pid")
    private int originalPid; // 共用庫裡原本那筆景點的 PID

    @Column(name = "override_pid")
    private Integer overridePid; // 這間旅行社自己的複本 PID; null = 純隱藏、沒有複本

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public PoiOverride() {}

    public PoiOverride(int AID, int originalPid, Integer overridePid) {
        this.AID = AID;
        this.originalPid = originalPid;
        this.overridePid = overridePid;
    }

    public int getOID() { return OID; }
    public void setOID(int OID) { this.OID = OID; }

    public int getAID() { return AID; }
    public void setAID(int AID) { this.AID = AID; }

    public int getOriginalPid() { return originalPid; }
    public void setOriginalPid(int originalPid) { this.originalPid = originalPid; }

    public Integer getOverridePid() { return overridePid; }
    public void setOverridePid(Integer overridePid) { this.overridePid = overridePid; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
