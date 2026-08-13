package com.example.UsefulTravel.DAO;

import com.example.UsefulTravel.entity.ItineraryItemOption;

import java.util.List;

public interface ItineraryItemOptionDAO {
    void save(ItineraryItemOption option);
    List<ItineraryItemOption> findByItem(int IIID);
    void deleteByItem(int IIID);
    void clearSelected(int IIID);
}
