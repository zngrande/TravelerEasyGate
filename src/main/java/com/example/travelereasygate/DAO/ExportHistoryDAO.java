package com.example.travelereasygate.DAO;

import com.example.travelereasygate.entity.ExportHistory;

import java.util.List;

public interface ExportHistoryDAO {
    void save(ExportHistory history);
    List<ExportHistory> findByItinerary(int ITID);
}
