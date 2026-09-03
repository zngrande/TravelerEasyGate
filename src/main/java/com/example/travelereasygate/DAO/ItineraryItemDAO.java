package com.example.travelereasygate.DAO;

import com.example.travelereasygate.entity.ItineraryItem;

import java.util.List;

public interface ItineraryItemDAO {
    void save(ItineraryItem item);
    ItineraryItem findById(int IIID);
    List<ItineraryItem> findByDay(int IDID);
    void deleteById(int IIID);
    void updateSortOrder(int IIID, int sortOrder);
    void clearPidReferences(int PID);
}
