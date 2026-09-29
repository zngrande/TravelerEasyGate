package com.example.travelereasygate.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * GoogleDistanceCache - 快取 Google Distance Matrix API 的查詢結果 (兩點間距離/時間)
 *
 * 背景: 行程看板每次拖曳排序、加/刪項目、AI 排程都會把當天整條路線的路段快取清空重算
 * (見 ItineraryService.recalculateRoutes()), 而很多熱門景點之間的座標配對會在不同天、不同行程、
 * 甚至不同旅行社之間反覆出現。沒有這層快取的話, 同一組座標會被反覆重新呼叫 Google API,
 * 完全是浪費——這正是這個系統短期內把 Google Cloud 額度燒掉一大筆 (Distance Matrix API 帳單)
 * 的主要原因 (詳見 2026-09-07 對話紀錄的診斷)。
 *
 * 座標會四捨五入到小數點後 5 位 (約 1.1 公尺精度) 再當作查詢 key (見 GoogleMapsClient.roundForCacheKey()),
 * 讓「幾乎同一個點」但精確度略有落差的座標 (例如同一個景點在不同筆資料裡輸入時小數點位數不同)
 * 也能命中同一筆快取, 進一步提高命中率。
 *
 * 沒有設定過期時間: Distance Matrix 在沒有指定 departure_time (即時路況) 的情況下, 回傳的是
 * 「一般路況」估算值, 短期內 (幾個月甚至更久) 不太會因為時間經過而有顯著變化, 比起「準確度略降」,
 * 省下的 API 費用效益高很多。之後如果想要加有效期限, 直接在 DAO 查詢加上 cachedAt 篩選條件即可,
 * 不需要改資料庫結構。
 */
@Entity
@Table(name = "google_distance_cache",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_google_distance_cache",
                columnNames = {"from_lat", "from_lng", "to_lat", "to_lng", "mode"}))
public class GoogleDistanceCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "GDCID")
    private int GDCID;

    @Column(name = "from_lat", precision = 8, scale = 5, nullable = false)
    private BigDecimal fromLat;

    @Column(name = "from_lng", precision = 8, scale = 5, nullable = false)
    private BigDecimal fromLng;

    @Column(name = "to_lat", precision = 8, scale = 5, nullable = false)
    private BigDecimal toLat;

    @Column(name = "to_lng", precision = 8, scale = 5, nullable = false)
    private BigDecimal toLng;

    @Column(name = "mode", length = 20, nullable = false)
    private String mode;

    @Column(name = "distance_km")
    private BigDecimal distanceKm;

    @Column(name = "duration_min")
    private Integer durationMin;

    @Column(name = "cached_at")
    private LocalDateTime cachedAt;

    public GoogleDistanceCache() {}

    public GoogleDistanceCache(BigDecimal fromLat, BigDecimal fromLng, BigDecimal toLat, BigDecimal toLng,
                                String mode, BigDecimal distanceKm, Integer durationMin) {
        this.fromLat = fromLat;
        this.fromLng = fromLng;
        this.toLat = toLat;
        this.toLng = toLng;
        this.mode = mode;
        this.distanceKm = distanceKm;
        this.durationMin = durationMin;
    }

    public int getGDCID() { return GDCID; }
    public void setGDCID(int GDCID) { this.GDCID = GDCID; }

    public BigDecimal getFromLat() { return fromLat; }
    public void setFromLat(BigDecimal fromLat) { this.fromLat = fromLat; }

    public BigDecimal getFromLng() { return fromLng; }
    public void setFromLng(BigDecimal fromLng) { this.fromLng = fromLng; }

    public BigDecimal getToLat() { return toLat; }
    public void setToLat(BigDecimal toLat) { this.toLat = toLat; }

    public BigDecimal getToLng() { return toLng; }
    public void setToLng(BigDecimal toLng) { this.toLng = toLng; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public BigDecimal getDistanceKm() { return distanceKm; }
    public void setDistanceKm(BigDecimal distanceKm) { this.distanceKm = distanceKm; }

    public Integer getDurationMin() { return durationMin; }
    public void setDurationMin(Integer durationMin) { this.durationMin = durationMin; }

    public LocalDateTime getCachedAt() { return cachedAt; }
    public void setCachedAt(LocalDateTime cachedAt) { this.cachedAt = cachedAt; }
}
