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

    @Column(name = "latitude")
    private java.math.BigDecimal latitude; // 這個項目自己的座標, 不一定要連結 POI 資料庫才有

    @Column(name = "longitude")
    private java.math.BigDecimal longitude;

    @Column(name = "item_country")
    private String itemCountry; // 這個項目自己所在的國家 (從 AI 解析帶過來, 比行程層級的國家精確)

    @Column(name = "item_region")
    private String itemRegion; // 這個項目自己所在的地區/城市

    @Column(name = "time_slot")
    private String timeSlot; // breakfast/lunch/dinner/morning/noon/afternoon/evening, 用於自動整理排序 (早餐固定第一個/中午午餐/晚上晚餐/飯店排最後)

    @jakarta.persistence.Column(name = "show_on_map")
    private Boolean showOnMap = true; // 這個項目要不要顯示在地圖上 (可個別關掉, 不影響行程內容本身)

    // ---- 以下為「交通」類型項目 (item_type = transport) 專用的欄位 ----
    // 一般景點/餐廳/住宿只有單一地點, 但交通是「從A到B」, 所以另外存起始/目的地資訊,
    // 跟原本的 latitude/longitude (借用來代表「目的地」座標, 方便沿用同一套地圖渲染邏輯) 分開管理。
    @Column(name = "from_location")
    private String fromLocation; // 起始點名稱

    @Column(name = "from_address")
    private String fromAddress; // 起始地址 (沒填的話後端會依起始點名稱自動查詢帶入)

    @Column(name = "to_location")
    private String toLocation; // 目的地名稱

    @Column(name = "to_address")
    private String toAddress; // 目的地地址 (沒填的話後端會依目的地名稱自動查詢帶入)

    @Column(name = "transport_method")
    private String transportMethod; // 交通工具 (例如: 高鐵/飛機/遊覽車/渡輪/計程車...)

    @Column(name = "commute_duration")
    private String commuteDuration; // 通勤時間 (自由文字, 例如「約1小時30分」)

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

    public java.math.BigDecimal getLatitude() { return latitude; }
    public void setLatitude(java.math.BigDecimal latitude) { this.latitude = latitude; }

    public java.math.BigDecimal getLongitude() { return longitude; }
    public void setLongitude(java.math.BigDecimal longitude) { this.longitude = longitude; }

    public String getItemCountry() { return itemCountry; }
    public void setItemCountry(String itemCountry) { this.itemCountry = itemCountry; }

    public String getItemRegion() { return itemRegion; }
    public void setItemRegion(String itemRegion) { this.itemRegion = itemRegion; }

    public String getTimeSlot() { return timeSlot; }
    public void setTimeSlot(String timeSlot) { this.timeSlot = timeSlot; }

    public Boolean getShowOnMap() { return showOnMap; }
    public void setShowOnMap(Boolean showOnMap) { this.showOnMap = showOnMap; }

    public String getFromLocation() { return fromLocation; }
    public void setFromLocation(String fromLocation) { this.fromLocation = fromLocation; }

    public String getFromAddress() { return fromAddress; }
    public void setFromAddress(String fromAddress) { this.fromAddress = fromAddress; }

    public String getToLocation() { return toLocation; }
    public void setToLocation(String toLocation) { this.toLocation = toLocation; }

    public String getToAddress() { return toAddress; }
    public void setToAddress(String toAddress) { this.toAddress = toAddress; }

    public String getTransportMethod() { return transportMethod; }
    public void setTransportMethod(String transportMethod) { this.transportMethod = transportMethod; }

    public String getCommuteDuration() { return commuteDuration; }
    public void setCommuteDuration(String commuteDuration) { this.commuteDuration = commuteDuration; }
}