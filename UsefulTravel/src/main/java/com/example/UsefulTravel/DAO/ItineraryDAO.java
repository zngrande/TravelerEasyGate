package com.example.UsefulTravel.DAO;

import com.example.UsefulTravel.entity.Itinerary;

import java.util.List;

public interface ItineraryDAO {
    void save(Itinerary itinerary);
    Itinerary findById(int ITID);
    List<Itinerary> findByAgency(int AID);
    void deleteById(int ITID);
}
