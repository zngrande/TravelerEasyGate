package com.example.UsefulTravel.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_import")
public class AiImport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "IPID")
    private int IPID;

    @Column(name = "AID")
    private int AID;

    @Column(name = "created_by")
    private int createdBy;

    @Column(name = "source_type")
    private String sourceType = "text"; // text / pdf / docx / url / image

    @Column(name = "raw_content", columnDefinition = "MEDIUMTEXT")
    private String rawContent;

    @Column(name = "extra_context", columnDefinition = "TEXT")
    private String extraContext; // 使用者填的「行程重點資訊」(出發/抵達機場+時間、行程說明), 格式化文字, AI 解析時當額外參考、review 頁面顯示回去給使用者看

    @Column(name = "status")
    private String status = "pending"; // pending / parsed / confirmed / failed

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "result_itinerary_id")
    private Integer resultItineraryId;

    @Column(name = "template_style")
    private String templateStyle = "default"; // wenqing / luxury / corporate / default

    @Column(name = "suggested_title")
    private String suggestedTitle; // AI 建議的行程標題

    @Column(name = "suggested_country")
    private String suggestedCountry; // AI 判斷出的國家

    @Column(name = "suggested_region")
    private String suggestedRegion; // AI 判斷出的地區/城市 (跟國家分開, 例如國家=日本, 地區=北海道)

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public AiImport() {}

    public AiImport(int AID, int createdBy, String sourceType, String rawContent) {
        this.AID = AID;
        this.createdBy = createdBy;
        this.sourceType = sourceType;
        this.rawContent = rawContent;
    }

    public int getIPID() { return IPID; }
    public void setIPID(int IPID) { this.IPID = IPID; }

    public int getAID() { return AID; }
    public void setAID(int AID) { this.AID = AID; }

    public int getCreatedBy() { return createdBy; }
    public void setCreatedBy(int createdBy) { this.createdBy = createdBy; }

    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }

    public String getRawContent() { return rawContent; }
    public void setRawContent(String rawContent) { this.rawContent = rawContent; }

    public String getExtraContext() { return extraContext; }
    public void setExtraContext(String extraContext) { this.extraContext = extraContext; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Integer getResultItineraryId() { return resultItineraryId; }
    public void setResultItineraryId(Integer resultItineraryId) { this.resultItineraryId = resultItineraryId; }

    public String getTemplateStyle() { return templateStyle; }
    public void setTemplateStyle(String templateStyle) { this.templateStyle = templateStyle; }

    public String getSuggestedTitle() { return suggestedTitle; }
    public void setSuggestedTitle(String suggestedTitle) { this.suggestedTitle = suggestedTitle; }

    public String getSuggestedCountry() { return suggestedCountry; }
    public void setSuggestedCountry(String suggestedCountry) { this.suggestedCountry = suggestedCountry; }

    public String getSuggestedRegion() { return suggestedRegion; }
    public void setSuggestedRegion(String suggestedRegion) { this.suggestedRegion = suggestedRegion; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
