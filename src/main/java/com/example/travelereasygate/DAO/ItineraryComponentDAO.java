package com.example.travelereasygate.DAO;

import com.example.travelereasygate.entity.ItineraryComponent;

import java.util.List;

public interface ItineraryComponentDAO {
    void save(ItineraryComponent ic);
    List<ItineraryComponent> findByItinerary(int ITID);
}
