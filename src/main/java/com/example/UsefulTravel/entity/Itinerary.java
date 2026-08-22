package com.example.UsefulTravel.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "itinerary")
public class Itinerary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ITID")
    private int ITID;

    @Column(name = "AID")
    private int AID;

    @Column(name = "created_by")
    private int createdBy; // staff_user.UID

    @Column(name = "title")
    private String title;

    @Column(name = "country")
    private String country;

    @Column(name = "region")
    private String region; // 地區/城市, 例如「花蓮」「北海道」, 跟 country 分開方便篩選 POI

    @Column(name = "days_count")
    private int daysCount = 1;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "group_size")
    private Integer groupSize;

    @Column(name = "status")
    private String status = "draft"; // draft / confirmed / departed / completed

    @Column(name = "template_style")
    private String templateStyle = "default"; // wenqing / luxury / corporate / default (匯出企劃書時套用的風格)

    @Column(name = "arrange_mode")
    private String arrangeMode = "meal_time"; // all_last / meal_time, 記錄上次用哪個「自動整理」模式, 看板選單重新整理頁面後還記得

    @Column(name = "is_locked")
    private boolean isLocked = false; // 上鎖後其他人不能編輯行程內容, 避免多人協作互相覆蓋

    @Column(name = "locked_by")
    private Integer lockedBy; // staff_user.UID, 誰上的鎖

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description; // 行程重點說明 (選填, 建立行程時填寫), 例如「五天四夜, 第一天到東京...」

    public Itinerary() {}

    public Itinerary(int AID, int createdBy, String title, String country, int daysCount) {
        this.AID = AID;
        this.createdBy = createdBy;
        this.title = title;
        this.country = country;
        this.daysCount = daysCount;
    }

    public int getITID() { return ITID; }
    public void setITID(int ITID) { this.ITID = ITID; }

    public int getAID() { return AID; }
    public void setAID(int AID) { this.AID = AID; }

    public int getCreatedBy() { return createdBy; }
    public void setCreatedBy(int createdBy) { this.createdBy = createdBy; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public int getDaysCount() { return daysCount; }
    public void setDaysCount(int daysCount) { this.daysCount = daysCount; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public Integer getGroupSize() { return groupSize; }
    public void setGroupSize(Integer groupSize) { this.groupSize = groupSize; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTemplateStyle() { return templateStyle; }
    public void setTemplateStyle(String templateStyle) { this.templateStyle = templateStyle; }

    public String getArrangeMode() { return arrangeMode; }
    public void setArrangeMode(String arrangeMode) { this.arrangeMode = arrangeMode; }

    public boolean isLocked() { return isLocked; }
    public void setLocked(boolean locked) { isLocked = locked; }

    public Integer getLockedBy() { return lockedBy; }
    public void setLockedBy(Integer lockedBy) { this.lockedBy = lockedBy; }

    public LocalDateTime getLockedAt() { return lockedAt; }
    public void setLockedAt(LocalDateTime lockedAt) { this.lockedAt = lockedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}