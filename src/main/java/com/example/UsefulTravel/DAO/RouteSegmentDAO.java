package com.example.UsefulTravel.DAO;

import com.example.UsefulTravel.entity.RouteSegment;

import java.util.List;

public interface RouteSegmentDAO {
    void save(RouteSegment segment);
    void update(RouteSegment segment); // 更新已存在的路段 (distanceKm/durationMin/transportMode 等欄位)
    List<RouteSegment> findByDay(int IDID);
    void deleteByDay(int IDID);
    RouteSegment findById(int RSID);
    void updateTransportMode(int RSID, String mode);
}
