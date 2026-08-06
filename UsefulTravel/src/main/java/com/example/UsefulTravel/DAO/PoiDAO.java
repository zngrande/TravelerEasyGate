package com.example.UsefulTravel.DAO;

import com.example.UsefulTravel.entity.Poi;

import java.util.List;

public interface PoiDAO {
    void save(Poi poi);
    Poi findById(int PID);
    List<Poi> findByAgencyOrShared(Integer AID);
    List<Poi> searchByKeyword(Integer AID, String keyword, String category);
    void deleteById(int PID);
}
