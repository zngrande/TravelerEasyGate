package com.example.travelereasygate.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    private String category; // 景點 / 餐廳 / 飯店 / 休息站 / 機場 / 交通 / 購物 (中文, 使用者要求資料庫維持中文, 不要用英文)

    @Column(name = "name")
    private String name;

    @Column(name = "original_name", length = 500)
    private String originalName; // 原文地名 (例: 日文/英文原始拼寫), 讓 AI 解析出來的不同命名方式也能搜尋比對到同一個地點

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

    @Column(name = "agency_price")
    private BigDecimal agencyPrice; // 同行價/建議售價

    @Column(name = "supplier_contact")
    private String supplierContact; // 供應商窗口 (姓名/電話/LINE等)

    @Column(name = "supplier_notes", columnDefinition = "TEXT")
    private String supplierNotes; // 合作備註 (付款方式/取消政策等)

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

    // 使用者反映「加入景點會出現加入失敗：Unexpected token '<'...」跟「上傳圖片出現 poiId=undefined 的錯誤」——
    // 追查後發現根因: 這個專案裡有兩套 Jackson 同時在跑 (見 pom.xml 那份很長的註解)。Spring Boot 4 預設用
    // Jackson 3 (tools.jackson.*, 給 @ResponseBody/ResponseEntity 那些 JSON API 用), 另外額外加了一份
    // 「舊版」Jackson 2 (com.fasterxml.jackson.*, 只給 Thymeleaf 的 th:inline="javascript" 內嵌 JS 資料
    // 用, 例如 board.html 的 poiList、images/list.html 的 allPois)。這兩套 Jackson 對「getPID() 這種
    // 開頭是多個連續大寫字母的欄位」預設的命名規則不一樣: 實測 Jackson 2 (2.16.1/2.18.2 同一代, 預設沒開
    // MapperFeature.USE_STD_BEAN_NAMING) 會把 getPID()/getAID() 這種 getter 名稱整段轉小寫變成 JSON 欄位
    // 名稱 "pid"/"aid", 但 Jackson 3 預設會保留原本大小寫變成 "PID"/"AID" (整個專案所有即時 API,
    // 例如 /itinerary/day/{id}/items 回傳的 item.PID/item.IIID/item.IDID, 全部都是靠這個「保留大小寫」的
    // 行為才會跟 JS 裡到處寫的 poi.PID、item.IIID 這些大寫欄位名稱對得上)。
    // 結果: 用 Jackson 2 內嵌的 poiList/allPois 這種陣列, 裡面每個景點物件的 PID 欄位在 JS 裡其實變成小寫
    // 的 pid, 不是預期的大寫 PID——board.html 的 renderPoiQuickList()/景點快選面板「加入」按鈕
    // (onclick="addToCurrentDay(${poi.PID}, ...)")、images/list.html 的 populatePoiSelect()/景點選擇器
    // (opt.value = p.PID) 都在讀這個 (實際不存在的) 大寫 poi.PID, 拿到 JS 的 undefined, 组裝出來的請求
    // 參數變成字面上的字串 "undefined" (PID=undefined / poiId=undefined) 送到後端——後端拿 Integer 去解析
    // "undefined" 這個字串, 直接丟 NumberFormatException, 變成一頁看起來像系統錯誤的 HTML 例外頁 (不是
    // 預期的 JSON), 前端 res.json() 解析這個 HTML 就會丟出「Unexpected token '<', "<!DOCTYPE "...」。
    // 修法: 用 @JsonProperty 明確把這個欄位的 JSON 名稱釘死成 "PID" (不管是哪一套 Jackson 來序列化都一樣),
    // 從根本解掉兩套 Jackson 對這種欄位命名規則不一致的問題——對 Jackson 3 那邊完全沒有影響 (它預設本來就是
    // "PID", 這裡明講一次結果不變), 只有 Jackson 2 (Thymeleaf 內嵌 JS 用的那份) 的輸出會被修正回 "PID"。
    // AID 欄位雖然這次沒有實際回報的錯誤, 但同樣是「多個連續大寫字母」的欄位名稱, 會踩到一模一樣的問題,
    // 這裡一併預防性修掉, 避免以後哪個頁面用到 poi.AID 又重演一次一樣的 bug。
    @JsonProperty("PID")
    public int getPID() { return PID; }
    public void setPID(int PID) { this.PID = PID; }

    @JsonProperty("AID")
    public Integer getAID() { return AID; }
    public void setAID(Integer AID) { this.AID = AID; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }

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

    public BigDecimal getAgencyPrice() { return agencyPrice; }
    public void setAgencyPrice(BigDecimal agencyPrice) { this.agencyPrice = agencyPrice; }

    public String getSupplierContact() { return supplierContact; }
    public void setSupplierContact(String supplierContact) { this.supplierContact = supplierContact; }

    public String getSupplierNotes() { return supplierNotes; }
    public void setSupplierNotes(String supplierNotes) { this.supplierNotes = supplierNotes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}