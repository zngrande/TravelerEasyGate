package com.example.UsefulTravel.entity;

import jakarta.persistence.*;
import java.time.LocalTime;

@Entity
@Table(name = "itinerary_item")
public class ItineraryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "IIID")
    private int IIID;

    @Column(name = "IDID")
    private int IDID;

    @Column(name = "PID")
    private Integer PID; // 對應 poi, 自訂項目時可為 null

    @Column(name = "item_type")
    private String itemType; // attraction / meal / hotel / transport / optional / free_time

    @Column(name = "custom_name")
    private String customName;

    @Column(name = "sort_order")
    private int sortOrder;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(name = "stay_duration_min")
    private Integer stayDurationMin;

    @Column(name = "note")
    private String note;

    public ItineraryItem() {}

    public ItineraryItem(int IDID, Integer PID, String itemType, String customName, int sortOrder) {
        this.IDID = IDID;
        this.PID = PID;
        this.itemType = itemType;
        this.customName = customName;
        this.sortOrder = sortOrder;
    }

    public int getIIID() { return IIID; }
    public void setIIID(int IIID) { this.IIID = IIID; }

    public int getIDID() { return IDID; }
    public void setIDID(int IDID) { this.IDID = IDID; }

    public Integer getPID() { return PID; }
    public void setPID(Integer PID) { this.PID = PID; }

    public String getItemType() { return itemType; }
    public void setItemType(String itemType) { this.itemType = itemType; }

    public String getCustomName() { return customName; }
    public void setCustomName(String customName) { this.customName = customName; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    public Integer getStayDurationMin() { return stayDurationMin; }
    public void setStayDurationMin(Integer stayDurationMin) { this.stayDurationMin = stayDurationMin; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
