package com.example.UsefulTravel.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "ai_parsed_item")
public class AiParsedItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "APIID")
    private int APIID;

    @Column(name = "APDID")
    private int APDID;

    @Column(name = "item_type")
    private String itemType; // attraction / meal / hotel / transport / highlight

    @Column(name = "name")
    private String name;

    @Column(name = "name_en")
    private String nameEn; // AI 解析時原文附帶的英文/外文名稱 (原文沒有就是 null), 用來比對 POI 的 original_name 提高命中率

    @Column(name = "time_slot")
    private String timeSlot; // morning / noon / afternoon / evening / breakfast / lunch / dinner

    @Column(name = "note")
    private String note;

    // ---- 以下為 item_type = 'transport' (航班/高鐵/包車等) 專用欄位, 其餘類型一律是 null ----
    @Column(name = "from_location")
    private String fromLocation;

    @Column(name = "to_location")
    private String toLocation;

    @Column(name = "transport_method")
    private String transportMethod; // 飛機/高鐵/遊覽車/渡輪...

    @Column(name = "transport_number")
    private String transportNumber; // 航班/車次編號, 例如 CI100, 原文沒提到就是 null

    @Column(name = "departure_time")
    private java.time.LocalTime departureTime;

    @Column(name = "arrival_time")
    private java.time.LocalTime arrivalTime;

    @Column(name = "item_country")
    private String itemCountry; // 這個項目自己所在的國家 (跟整個行程共用的 country 分開, 更準確)

    @Column(name = "item_region")
    private String itemRegion; // 這個項目自己所在的地區/城市

    @Column(name = "matched_pid")
    private Integer matchedPid; // 自動比對到公司 POI 資料庫的結果

    @Column(name = "stay_minutes")
    private Integer stayMinutes; // AI 預估的停留時間(分鐘), 景點/餐廳/住宿才會有值

    @Column(name = "sort_order")
    private int sortOrder;

    public AiParsedItem() {}

    public AiParsedItem(int APDID, String itemType, String name, String timeSlot, String note, int sortOrder) {
        this.APDID = APDID;
        this.itemType = itemType;
        this.name = name;
        this.timeSlot = timeSlot;
        this.note = note;
        this.sortOrder = sortOrder;
    }

    public int getAPIID() { return APIID; }
    public void setAPIID(int APIID) { this.APIID = APIID; }

    public int getAPDID() { return APDID; }
    public void setAPDID(int APDID) { this.APDID = APDID; }

    public String getItemType() { return itemType; }
    public void setItemType(String itemType) { this.itemType = itemType; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getNameEn() { return nameEn; }
    public void setNameEn(String nameEn) { this.nameEn = nameEn; }

    public String getTimeSlot() { return timeSlot; }
    public void setTimeSlot(String timeSlot) { this.timeSlot = timeSlot; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public String getFromLocation() { return fromLocation; }
    public void setFromLocation(String fromLocation) { this.fromLocation = fromLocation; }

    public String getToLocation() { return toLocation; }
    public void setToLocation(String toLocation) { this.toLocation = toLocation; }

    public String getTransportMethod() { return transportMethod; }
    public void setTransportMethod(String transportMethod) { this.transportMethod = transportMethod; }

    public String getTransportNumber() { return transportNumber; }
    public void setTransportNumber(String transportNumber) { this.transportNumber = transportNumber; }

    public java.time.LocalTime getDepartureTime() { return departureTime; }
    public void setDepartureTime(java.time.LocalTime departureTime) { this.departureTime = departureTime; }

    public java.time.LocalTime getArrivalTime() { return arrivalTime; }
    public void setArrivalTime(java.time.LocalTime arrivalTime) { this.arrivalTime = arrivalTime; }

    public String getItemCountry() { return itemCountry; }
    public void setItemCountry(String itemCountry) { this.itemCountry = itemCountry; }

    public String getItemRegion() { return itemRegion; }
    public void setItemRegion(String itemRegion) { this.itemRegion = itemRegion; }

    public Integer getMatchedPid() { return matchedPid; }
    public void setMatchedPid(Integer matchedPid) { this.matchedPid = matchedPid; }

    public Integer getStayMinutes() { return stayMinutes; }
    public void setStayMinutes(Integer stayMinutes) { this.stayMinutes = stayMinutes; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
