package com.example.UsefulTravel.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 國家/城市通用代碼對照表 (例: 日本=JP, 東京=TYO)。
 * 全域共用、不分旅行社的獨立參考表, 不影響既有 poi.country / poi.city 欄位。
 */
@Entity
@Table(name = "country_city_code")
public class CountryCityCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CCID")
    private int CCID;

    @Column(name = "type")
    private String type; // country / city

    @Column(name = "code")
    private String code; // 例: JP、TYO

    @Column(name = "name")
    private String name; // 例: 日本、東京

    @Column(name = "country_code")
    private String countryCode; // type=city 時, 所屬國家的 code; type=country 時為 null

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "sort_order")
    private Integer sortOrder;

    public CountryCityCode() {}

    public CountryCityCode(String type, String code, String name, String countryCode) {
        this.type = type;
        this.code = code;
        this.name = name;
        this.countryCode = countryCode;
    }

    public int getCCID() { return CCID; }
    public void setCCID(int CCID) { this.CCID = CCID; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
