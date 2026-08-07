package com.example.UsefulTravel.DAO;

import com.example.UsefulTravel.entity.AiParsedItem;

import java.util.List;

public interface AiParsedItemDAO {
    void save(AiParsedItem item);
    List<AiParsedItem> findByDay(int APDID);
}
