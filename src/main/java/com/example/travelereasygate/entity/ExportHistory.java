package com.example.travelereasygate.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "export_history")
public class ExportHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "EHID")
    private int EHID;

    @Column(name = "ITID")
    private int ITID;

    @Column(name = "format")
    private String format; // pdf / pptx / docx

    @Column(name = "file_url")
    private String fileUrl;

    @Column(name = "generated_by")
    private Integer generatedBy;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    public ExportHistory() {}

    public ExportHistory(int ITID, String format, String fileUrl, Integer generatedBy) {
        this.ITID = ITID;
        this.format = format;
        this.fileUrl = fileUrl;
        this.generatedBy = generatedBy;
    }

    public int getEHID() { return EHID; }
    public void setEHID(int EHID) { this.EHID = EHID; }

    public int getITID() { return ITID; }
    public void setITID(int ITID) { this.ITID = ITID; }

    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }

    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

    public Integer getGeneratedBy() { return generatedBy; }
    public void setGeneratedBy(Integer generatedBy) { this.generatedBy = generatedBy; }

    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
}
