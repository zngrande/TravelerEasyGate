package com.example.UsefulTravel.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "image_asset")
public class ImageAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "IAID")
    private int IAID;

    @Column(name = "AID")
    private int AID;

    @Column(name = "file_path")
    private String filePath; // 實際上是 ImageStorageService 回傳的識別碼 (本機實作是相對檔名)

    @Column(name = "original_filename")
    private String originalFilename;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "tags", columnDefinition = "TEXT")
    private String tags; // 逗號分隔

    @Column(name = "ai_description", columnDefinition = "TEXT")
    private String aiDescription;

    @Column(name = "matched_pid")
    private Integer matchedPid;

    @Column(name = "tag_status")
    private String tagStatus = "pending"; // pending / tagged / failed

    @Column(name = "uploaded_by")
    private Integer uploadedBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public ImageAsset() {}

    public ImageAsset(int AID, String filePath, String originalFilename, String contentType, Integer uploadedBy) {
        this.AID = AID;
        this.filePath = filePath;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.uploadedBy = uploadedBy;
    }

    public int getIAID() { return IAID; }
    public void setIAID(int IAID) { this.IAID = IAID; }

    public int getAID() { return AID; }
    public void setAID(int AID) { this.AID = AID; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public String getAiDescription() { return aiDescription; }
    public void setAiDescription(String aiDescription) { this.aiDescription = aiDescription; }

    public Integer getMatchedPid() { return matchedPid; }
    public void setMatchedPid(Integer matchedPid) { this.matchedPid = matchedPid; }

    public String getTagStatus() { return tagStatus; }
    public void setTagStatus(String tagStatus) { this.tagStatus = tagStatus; }

    public Integer getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(Integer uploadedBy) { this.uploadedBy = uploadedBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
