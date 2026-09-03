package com.example.travelereasygate.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "agency")
public class Agency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "AID")
    private int AID;

    @Column(name = "name")
    private String name;

    @Column(name = "license_no")
    private String licenseNo;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "plan_type")
    private String planType = "trial";

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public Agency() {}

    public Agency(String name, String licenseNo, String contactPhone, String contactEmail) {
        this.name = name;
        this.licenseNo = licenseNo;
        this.contactPhone = contactPhone;
        this.contactEmail = contactEmail;
    }

    public int getAID() { return AID; }
    public void setAID(int AID) { this.AID = AID; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLicenseNo() { return licenseNo; }
    public void setLicenseNo(String licenseNo) { this.licenseNo = licenseNo; }

    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }

    public String getPlanType() { return planType; }
    public void setPlanType(String planType) { this.planType = planType; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
