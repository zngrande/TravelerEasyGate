package com.example.UsefulTravel.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "currency")
public class Currency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CID")
    private int CID;

    @Column(name = "AID")
    private Integer AID; // NULL = 平台共用匯率

    @Column(name = "code")
    private String code; // TWD / JPY / USD ...

    @Column(name = "name")
    private String name;

    @Column(name = "rate_to_twd")
    private BigDecimal rateToTwd = BigDecimal.ONE;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Currency() {}

    public Currency(Integer AID, String code, String name, BigDecimal rateToTwd) {
        this.AID = AID;
        this.code = code;
        this.name = name;
        this.rateToTwd = rateToTwd;
    }

    public int getCID() { return CID; }
    public void setCID(int CID) { this.CID = CID; }

    public Integer getAID() { return AID; }
    public void setAID(Integer AID) { this.AID = AID; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BigDecimal getRateToTwd() { return rateToTwd; }
    public void setRateToTwd(BigDecimal rateToTwd) { this.rateToTwd = rateToTwd; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
