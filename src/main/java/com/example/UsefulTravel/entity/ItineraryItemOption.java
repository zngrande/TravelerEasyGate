package com.example.UsefulTravel.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "itinerary_item_option")
public class ItineraryItemOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "IIOID")
    private int IIOID;

    @Column(name = "IIID")
    private int IIID;

    @Column(name = "name")
    private String name;

    @Column(name = "latitude")
    private BigDecimal latitude;

    @Column(name = "longitude")
    private BigDecimal longitude;

    @Column(name = "is_selected")
    private boolean selected;

    public ItineraryItemOption() {}

    public ItineraryItemOption(int IIID, String name, BigDecimal latitude, BigDecimal longitude, boolean selected) {
        this.IIID = IIID;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.selected = selected;
    }

    public int getIIOID() { return IIOID; }
    public void setIIOID(int IIOID) { this.IIOID = IIOID; }

    public int getIIID() { return IIID; }
    public void setIIID(int IIID) { this.IIID = IIID; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }

    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }

    public boolean isSelected() { return selected; }
    public void setSelected(boolean selected) { this.selected = selected; }
}
