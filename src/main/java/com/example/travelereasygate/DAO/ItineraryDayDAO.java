package com.example.travelereasygate.DAO;

import com.example.travelereasygate.entity.ItineraryDay;

import java.util.List;

public interface ItineraryDayDAO {
    void save(ItineraryDay day);
    ItineraryDay findById(int IDID);
    List<ItineraryDay> findByItinerary(int ITID);
    void deleteById(int IDID);
}
