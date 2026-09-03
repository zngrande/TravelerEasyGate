package com.example.travelereasygate.DAO;

import com.example.travelereasygate.entity.PoiCooperationLog;

import java.util.List;

public interface PoiCooperationLogDAO {
    void save(PoiCooperationLog log);
    List<PoiCooperationLog> findByPoi(int PID);
}
