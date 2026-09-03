package com.example.travelereasygate.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "route_segment")
public class RouteSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "RSID")
    private int RSID;

    @Column(name = "IDID")
    private int IDID;

    @Column(name = "from_item_id")
    private int fromItemId;

    @Column(name = "to_item_id")
    private int toItemId;

    @Column(name = "distance_km")
    private BigDecimal distanceKm;

    @Column(name = "duration_min")
    private Integer durationMin;

    @Column(name = "transport_mode")
    private String transportMode = "driving";

    @Column(name = "is_backtrack")
    private boolean isBacktrack = false;

    @Column(name = "calculated_at")
    private LocalDateTime calculatedAt;

    public RouteSegment() {}

    public RouteSegment(int IDID, int fromItemId, int toItemId, BigDecimal distanceKm,
                         Integer durationMin, boolean isBacktrack) {
        this.IDID = IDID;
        this.fromItemId = fromItemId;
        this.toItemId = toItemId;
        this.distanceKm = distanceKm;
        this.durationMin = durationMin;
        this.isBacktrack = isBacktrack;
    }

    public int getRSID() { return RSID; }
    public void setRSID(int RSID) { this.RSID = RSID; }

    public int getIDID() { return IDID; }
    public void setIDID(int IDID) { this.IDID = IDID; }

    public int getFromItemId() { return fromItemId; }
    public void setFromItemId(int fromItemId) { this.fromItemId = fromItemId; }

    public int getToItemId() { return toItemId; }
    public void setToItemId(int toItemId) { this.toItemId = toItemId; }

    public BigDecimal getDistanceKm() { return distanceKm; }
    public void setDistanceKm(BigDecimal distanceKm) { this.distanceKm = distanceKm; }

    public Integer getDurationMin() { return durationMin; }
    public void setDurationMin(Integer durationMin) { this.durationMin = durationMin; }

    public String getTransportMode() { return transportMode; }
    public void setTransportMode(String transportMode) { this.transportMode = transportMode; }

    public boolean isBacktrack() { return isBacktrack; }
    public void setBacktrack(boolean backtrack) { isBacktrack = backtrack; }

    public LocalDateTime getCalculatedAt() { return calculatedAt; }
    public void setCalculatedAt(LocalDateTime calculatedAt) { this.calculatedAt = calculatedAt; }
}
