package com.example.travelereasygate.DAO;

import com.example.travelereasygate.entity.AiParsedItem;

import java.util.List;

public interface AiParsedItemDAO {
    void save(AiParsedItem item);
    AiParsedItem findById(int APIID);
    List<AiParsedItem> findByDay(int APDID);
    void clearMatchedPid(int PID);
}
