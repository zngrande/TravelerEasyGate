package com.example.UsefulTravel.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "component")
public class TravelComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CPID")
    private int CPID;

    @Column(name = "AID")
    private int AID;

    @Column(name = "type")
    private String type; // flight / meal / hotel_grade / optional_tour

    @Column(name = "name")
    private String name;

    @Column(name = "default_price")
    private BigDecimal defaultPrice;

    @Column(name = "description")
    private String description;

    public TravelComponent() {}

    public TravelComponent(int AID, String type, String name, BigDecimal defaultPrice, String description) {
        this.AID = AID;
        this.type = type;
        this.name = name;
        this.defaultPrice = defaultPrice;
        this.description = description;
    }

    public int getCPID() { return CPID; }
    public void setCPID(int CPID) { this.CPID = CPID; }

    public int getAID() { return AID; }
    public void setAID(int AID) { this.AID = AID; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BigDecimal getDefaultPrice() { return defaultPrice; }
    public void setDefaultPrice(BigDecimal defaultPrice) { this.defaultPrice = defaultPrice; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
