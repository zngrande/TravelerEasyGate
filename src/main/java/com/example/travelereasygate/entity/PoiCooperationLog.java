package com.example.travelereasygate.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "poi_cooperation_log")
public class PoiCooperationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PCLID")
    private int PCLID;

    @Column(name = "PID")
    private int PID;

    @Column(name = "log_date")
    private LocalDate logDate;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_by")
    private Integer createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public PoiCooperationLog() {}

    public PoiCooperationLog(int PID, LocalDate logDate, String note, Integer createdBy) {
        this.PID = PID;
        this.logDate = logDate;
        this.note = note;
        this.createdBy = createdBy;
    }

    public int getPCLID() { return PCLID; }
    public void setPCLID(int PCLID) { this.PCLID = PCLID; }

    public int getPID() { return PID; }
    public void setPID(int PID) { this.PID = PID; }

    public LocalDate getLogDate() { return logDate; }
    public void setLogDate(LocalDate logDate) { this.logDate = logDate; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public Integer getCreatedBy() { return createdBy; }
    public void setCreatedBy(Integer createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
