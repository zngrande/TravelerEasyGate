package com.example.UsefulTravel.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "poi")
public class Poi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PID")
    private int PID;

    @Column(name = "AID")
    private Integer AID; // null = 平台共用庫

    @Column(name = "category")
    private String category; // attraction / restaurant / hotel / rest_stop

    @Column(name = "name")
    private String name;

    @Column(name = "country")
    private String country;

    @Column(name = "city")
    private String city;

    @Column(name = "address")
    private String address;

    @Column(name = "latitude")
    private BigDecimal latitude;

    @Column(name = "longitude")
    private BigDecimal longitude;

    @Column(name = "suggested_stay_min")
    private Integer suggestedStayMin = 60;

    @Column(name = "open_hours")
    private String openHours;

    @Column(name = "description")
    private String description;

    @Column(name = "star_rating")
    private BigDecimal starRating;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public Poi() {}

    public Poi(Integer AID, String category, String name, String country, String city,
               String address, BigDecimal latitude, BigDecimal longitude) {
        this.AID = AID;
        this.category = category;
        this.name = name;
        this.country = country;
        this.city = city;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public int getPID() { return PID; }
    public void setPID(int PID) { this.PID = PID; }

    public Integer getAID() { return AID; }
    public void setAID(Integer AID) { this.AID = AID; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }

    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }

    public Integer getSuggestedStayMin() { return suggestedStayMin; }
    public void setSuggestedStayMin(Integer suggestedStayMin) { this.suggestedStayMin = suggestedStayMin; }

    public String getOpenHours() { return openHours; }
    public void setOpenHours(String openHours) { this.openHours = openHours; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getStarRating() { return starRating; }
    public void setStarRating(BigDecimal starRating) { this.starRating = starRating; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
