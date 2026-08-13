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
 * 距離/時間來源: 優先呼叫 GoogleMapsClient (Distance Matrix API) 拿實際開車時間,
 *   如果沒設定 google.maps.api.key 或呼叫失敗, 自動 fallback 成經緯度直線距離估算 (市區均速 30km/h)
 *
 * 通勤時間規則 (依需求): 以 Google 回傳的時間 (或 fallback 估算值) 為基準,
 *   乘以 1.5 倍當作實際安全通勤時間緩衝, 再四捨五入到最接近的 30 分鐘 (只會是整點或 30 分)
 *
 * 迴頭路判斷: 比較「上一段路線」跟「這一段路線」的方位角, 如果轉向角度超過閾值 (預設 120 度),
 *   代表這一段路是往回走的方向, 標記為疑似迴頭路
 */
@Service
public class RouteService {

    private static final double SAFETY_MULTIPLIER = 1.5;
    private static final int ROUND_TO_MINUTES = 30;
    private static final double BACKTRACK_ANGLE_THRESHOLD = 120.0; // 度數, 轉向角度超過這個就當迴頭路

    private final RouteSegmentDAO routeSegmentDAO;
    private final PoiDAO poiDAO;
    private final GoogleMapsClient googleMapsClient;

    @Autowired
    public RouteService(RouteSegmentDAO routeSegmentDAO, PoiDAO poiDAO, GoogleMapsClient googleMapsClient) {
        this.routeSegmentDAO = routeSegmentDAO;
        this.poiDAO = poiDAO;
        this.googleMapsClient = googleMapsClient;
    }

    public void calculateAndSaveSegments(int IDID, List<ItineraryItem> items) {
        Double prevBearing = null;

        for (int i = 0; i < items.size() - 1; i++) {
            ItineraryItem from = items.get(i);
            ItineraryItem to = items.get(i + 1);

            double[] fromCoord = resolveCoordinates(from);
            double[] toCoord = resolveCoordinates(to);
            if (fromCoord == null || toCoord == null) { prevBearing = null; continue; }

            double fromLat = fromCoord[0], fromLng = fromCoord[1];
            double toLat = toCoord[0], toLng = toCoord[1];

            double distanceKm;
            double rawMinutes;

            // 優先用 Google Distance Matrix API 拿實際開車距離/時間
            GoogleMapsClient.DistanceResult googleResult = googleMapsClient.getDrivingDistance(fromLat, fromLng, toLat, toLng);
            if (googleResult != null) {
                distanceKm = googleResult.distanceKm;
                rawMinutes = googleResult.durationMin;
            } else {
                // Fallback: 經緯度直線距離估算, 市區均速抓 30km/h
                distanceKm = haversineKm(fromLat, fromLng, toLat, toLng);
                rawMinutes = (distanceKm / 30.0) * 60;
            }

            // 通勤安全緩衝: 1.5倍後四捨五入到最近的30分鐘 (符合行事曆時間軸只顯示整點/30分的需求)
            int bufferedMin = roundToNearest30(rawMinutes * SAFETY_MULTIPLIER);

            // 迴頭路判斷: 跟上一段的方位角比較轉向角度
            double currentBearing = bearing(fromLat, fromLng, toLat, toLng);
            boolean backtrack = prevBearing != null && angleDiff(prevBearing, currentBearing) > BACKTRACK_ANGLE_THRESHOLD;
            prevBearing = currentBearing;

            RouteSegment segment = new RouteSegment(
                    IDID, from.getIIID(), to.getIIID(),
                    BigDecimal.valueOf(distanceKm).setScale(2, java.math.RoundingMode.HALF_UP),
                    bufferedMin, backtrack
            );
            routeSegmentDAO.save(segment);
        }
    }

    // 優先用項目自己存的座標 (自訂項目/已選定選項都有), 沒有的話才去查連結的 POI (相容舊資料)
    private double[] resolveCoordinates(ItineraryItem item) {
        if (item.getLatitude() != null && item.getLongitude() != null) {
            return new double[]{item.getLatitude().doubleValue(), item.getLongitude().doubleValue()};
        }
        if (item.getPID() != null) {
            Poi poi = poiDAO.findById(item.getPID());
            if (poi != null && poi.getLatitude() != null) {
                return new double[]{poi.getLatitude().doubleValue(), poi.getLongitude().doubleValue()};
            }
        }
        return null;
    }

    // 四捨五入到最近的 30 分鐘 (最少 30 分鐘, 不會出現 0 分鐘的通勤時間)
    private int roundToNearest30(double minutes) {
        int rounded = (int) (Math.round(minutes / ROUND_TO_MINUTES) * ROUND_TO_MINUTES);
        return Math.max(rounded, ROUND_TO_MINUTES);
    }

    // 計算從 (lat1,lon1) 到 (lat2,lon2) 的方位角 (0~360度, 0=正北, 順時針)
    private double bearing(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double deltaLambda = Math.toRadians(lon2 - lon1);

        double y = Math.sin(deltaLambda) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2) - Math.sin(phi1) * Math.cos(phi2) * Math.cos(deltaLambda);
        double theta = Math.atan2(y, x);
        return (Math.toDegrees(theta) + 360) % 360;
    }

    // 兩個方位角之間的夾角 (0~180度)
    private double angleDiff(double a, double b) {
        double diff = Math.abs(a - b) % 360;
        return diff > 180 ? 360 - diff : diff;
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
