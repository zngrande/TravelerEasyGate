package com.example.UsefulTravel.DAO;

import com.example.UsefulTravel.entity.RouteSegment;

import java.util.List;

public interface RouteSegmentDAO {
    void save(RouteSegment segment);
    List<RouteSegment> findByDay(int IDID);
    void deleteByDay(int IDID);
}
