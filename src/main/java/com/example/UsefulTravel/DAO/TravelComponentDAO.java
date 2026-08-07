package com.example.UsefulTravel.DAO;

import com.example.UsefulTravel.entity.TravelComponent;

import java.util.List;

public interface TravelComponentDAO {
    void save(TravelComponent component);
    TravelComponent findById(int CPID);
    List<TravelComponent> findByAgency(int AID);
}
