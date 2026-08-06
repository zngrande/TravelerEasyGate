package com.example.UsefulTravel.service;

import com.example.UsefulTravel.DAO.PoiDAO;
import com.example.UsefulTravel.DAO.RouteSegmentDAO;
import com.example.UsefulTravel.entity.ItineraryItem;
import com.example.UsefulTravel.entity.Poi;
import com.example.UsefulTravel.entity.RouteSegment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * RouteService - 計算相鄰行程項目之間的拉車距離/時間, 並偵測迴頭路
 *
 * TODO (下一步要接的地方):
 *   1. 把 calculateDistance() 換成真正呼叫 Google Distance Matrix API 的邏輯
 *      (需要景點的經緯度, 也就是 Poi.latitude / Poi.longitude)
 *   2. 目前用「經緯度直線距離 (Haversine)」當作暫時的估算值, 讓系統先能跑起來
 *   3. 迴頭路判斷目前用簡化版: 若這一段的方位角和前一段方位角夾角超過閾值就標記,
 *      正式版建議改用 Google Directions API 回傳的實際路線來判斷
 */
@Service
public class RouteService {

    private final RouteSegmentDAO routeSegmentDAO;
    private final PoiDAO poiDAO;

    @Autowired
    public RouteService(RouteSegmentDAO routeSegmentDAO, PoiDAO poiDAO) {
        this.routeSegmentDAO = routeSegmentDAO;
        this.poiDAO = poiDAO;
    }

    public void calculateAndSaveSegments(int IDID, List<ItineraryItem> items) {
        for (int i = 0; i < items.size() - 1; i++) {
            ItineraryItem from = items.get(i);
            ItineraryItem to = items.get(i + 1);

            if (from.getPID() == null || to.getPID() == null) continue; // 自訂項目沒有座標, 先跳過

            Poi fromPoi = poiDAO.findById(from.getPID());
            Poi toPoi = poiDAO.findById(to.getPID());
            if (fromPoi == null || toPoi == null) continue;
            if (fromPoi.getLatitude() == null || toPoi.getLatitude() == null) continue;

            double distanceKm = haversineKm(
                    fromPoi.getLatitude().doubleValue(), fromPoi.getLongitude().doubleValue(),
                    toPoi.getLatitude().doubleValue(), toPoi.getLongitude().doubleValue());

            // 粗估車程: 市區均速抓 30km/h (正式版改用 Google Directions API 的實際時間)
            int estimatedMin = (int) Math.round((distanceKm / 30.0) * 60);

            boolean backtrack = i > 0 && isBacktrack(items, i, fromPoi, toPoi);

            RouteSegment segment = new RouteSegment(
                    IDID, from.getIIID(), to.getIIID(),
                    BigDecimal.valueOf(distanceKm).setScale(2, java.math.RoundingMode.HALF_UP),
                    estimatedMin, backtrack
            );
            routeSegmentDAO.save(segment);
        }
    }

    // 簡化版迴頭路判斷: 之後有更好的資料再優化, 目前先保守回傳 false, 避免誤判
    private boolean isBacktrack(List<ItineraryItem> items, int index, Poi from, Poi to) {
        return false;
    }

    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // 地球半徑(km)
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
