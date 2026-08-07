package com.example.UsefulTravel.DAO;

import com.example.UsefulTravel.entity.ItineraryDay;

import java.util.List;

public interface ItineraryDayDAO {
    void save(ItineraryDay day);
    ItineraryDay findById(int IDID);
    List<ItineraryDay> findByItinerary(int ITID);
}
