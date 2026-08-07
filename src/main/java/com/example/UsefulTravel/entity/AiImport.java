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

    @Column(name = "status")
    private String status = "pending"; // pending / parsed / confirmed / failed

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "result_itinerary_id")
    private Integer resultItineraryId;

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

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Integer getResultItineraryId() { return resultItineraryId; }
    public void setResultItineraryId(Integer resultItineraryId) { this.resultItineraryId = resultItineraryId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
