package com.example.UsefulTravel.DAO;

import com.example.UsefulTravel.entity.ExportHistory;

import java.util.List;

public interface ExportHistoryDAO {
    void save(ExportHistory history);
    List<ExportHistory> findByItinerary(int ITID);
}
