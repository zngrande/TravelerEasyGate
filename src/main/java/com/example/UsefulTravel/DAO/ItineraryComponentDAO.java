package com.example.UsefulTravel.DAO;

import com.example.UsefulTravel.entity.ItineraryComponent;

import java.util.List;

public interface ItineraryComponentDAO {
    void save(ItineraryComponent ic);
    List<ItineraryComponent> findByItinerary(int ITID);
}
