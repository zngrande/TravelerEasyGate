package com.example.UsefulTravel.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "itinerary_component")
public class ItineraryComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ICID")
    private int ICID;

    @Column(name = "ITID")
    private int ITID;

    @Column(name = "CPID")
    private int CPID;

    @Column(name = "day_number")
    private Integer dayNumber;

    @Column(name = "quantity")
    private int quantity = 1;

    @Column(name = "price_override")
    private BigDecimal priceOverride;

    public ItineraryComponent() {}

    public ItineraryComponent(int ITID, int CPID, Integer dayNumber, int quantity, BigDecimal priceOverride) {
        this.ITID = ITID;
        this.CPID = CPID;
        this.dayNumber = dayNumber;
        this.quantity = quantity;
        this.priceOverride = priceOverride;
    }

    public int getICID() { return ICID; }
    public void setICID(int ICID) { this.ICID = ICID; }

    public int getITID() { return ITID; }
    public void setITID(int ITID) { this.ITID = ITID; }

    public int getCPID() { return CPID; }
    public void setCPID(int CPID) { this.CPID = CPID; }

    public Integer getDayNumber() { return dayNumber; }
    public void setDayNumber(Integer dayNumber) { this.dayNumber = dayNumber; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public BigDecimal getPriceOverride() { return priceOverride; }
    public void setPriceOverride(BigDecimal priceOverride) { this.priceOverride = priceOverride; }
}
