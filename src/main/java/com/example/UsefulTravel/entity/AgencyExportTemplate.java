package com.example.UsefulTravel.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "agency_export_template")
public class AgencyExportTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "AETID")
    private int AETID;

    @Column(name = "AID")
    private int AID;

    @Column(name = "name")
    private String name;

    // CUSTOMER (給客戶看的 .docx 企劃書範本) 或 AGENCY (給同業看的 .xlsx 報價單範本)
    @Column(name = "template_type")
    private String templateType = "CUSTOMER";

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "is_default")
    private boolean isDefault;

    @Column(name = "uploaded_by")
    private Integer uploadedBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public AgencyExportTemplate() {}

    public AgencyExportTemplate(int AID, String name, String filePath, Integer uploadedBy) {
        this.AID = AID;
        this.name = name;
        this.filePath = filePath;
        this.uploadedBy = uploadedBy;
    }

    public AgencyExportTemplate(int AID, String name, String templateType, String filePath, Integer uploadedBy) {
        this.AID = AID;
        this.name = name;
        this.templateType = templateType;
        this.filePath = filePath;
        this.uploadedBy = uploadedBy;
    }

    public int getAETID() { return AETID; }
    public void setAETID(int AETID) { this.AETID = AETID; }

    public int getAID() { return AID; }
    public void setAID(int AID) { this.AID = AID; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTemplateType() { return templateType; }
    public void setTemplateType(String templateType) { this.templateType = templateType; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean aDefault) { isDefault = aDefault; }

    public Integer getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(Integer uploadedBy) { this.uploadedBy = uploadedBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
