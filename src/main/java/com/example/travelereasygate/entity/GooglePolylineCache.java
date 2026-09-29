package com.example.travelereasygate.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * GooglePolylineCache - 快取 Google Directions API 回傳的「兩點間實際路網折線」(encoded polyline)
 *
 * 用途: 看板地圖預覽、匯出企劃書/Word 內嵌地圖圖片 (GoogleMapsClient.buildStaticMapUrl()) 都會呼叫
 * Directions API 把每一段路線畫成貼著實際道路的折線。這幾個呼叫端 (ItineraryController 地圖預覽、
 * TemplateMergeService、ExportService 匯出) 常常會用「同樣兩個座標點」重複查詢 (例如同一份行程被
 * 多次匯出、或看板地圖被重新整理), 加這層快取後同一組座標配對不會重複打 Google API。
 *
 * 快取 key 的座標四捨五入規則、沒有設定過期時間的理由, 跟 GoogleDistanceCache 完全一致, 詳見該類別的說明。
 */
@Entity
@Table(name = "google_polyline_cache",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_google_polyline_cache",
                columnNames = {"from_lat", "from_lng", "to_lat", "to_lng", "mode"}))
public class GooglePolylineCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "GPCID")
    private int GPCID;

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

    @Column(name = "polyline", columnDefinition = "TEXT")
    private String polyline;

    @Column(name = "cached_at")
    private LocalDateTime cachedAt;

    public GooglePolylineCache() {}

    public GooglePolylineCache(BigDecimal fromLat, BigDecimal fromLng, BigDecimal toLat, BigDecimal toLng,
                                String mode, String polyline) {
        this.fromLat = fromLat;
        this.fromLng = fromLng;
        this.toLat = toLat;
        this.toLng = toLng;
        this.mode = mode;
        this.polyline = polyline;
    }

    public int getGPCID() { return GPCID; }
    public void setGPCID(int GPCID) { this.GPCID = GPCID; }

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

    public String getPolyline() { return polyline; }
    public void setPolyline(String polyline) { this.polyline = polyline; }

    public LocalDateTime getCachedAt() { return cachedAt; }
    public void setCachedAt(LocalDateTime cachedAt) { this.cachedAt = cachedAt; }
}
