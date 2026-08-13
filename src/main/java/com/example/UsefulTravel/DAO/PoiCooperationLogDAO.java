package com.example.UsefulTravel.DAO;

import com.example.UsefulTravel.entity.PoiCooperationLog;

import java.util.List;

public interface PoiCooperationLogDAO {
    void save(PoiCooperationLog log);
    List<PoiCooperationLog> findByPoi(int PID);
}
