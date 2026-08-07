package com.example.UsefulTravel.service;

import com.example.UsefulTravel.DAO.PoiDAO;
import com.example.UsefulTravel.entity.Poi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PoiService {

    private final PoiDAO poiDAO;

    @Autowired
    public PoiService(PoiDAO poiDAO) {
        this.poiDAO = poiDAO;
    }

    public void save(Poi poi) {
        poiDAO.save(poi);
    }

    public Poi findById(int PID) {
        return poiDAO.findById(PID);
    }

    public List<Poi> listForAgency(int AID) {
        return poiDAO.findByAgencyOrShared(AID);
    }

    public List<Poi> search(int AID, String keyword, String category) {
        return poiDAO.searchByKeyword(AID, keyword, category);
    }

    public void delete(int PID) {
        poiDAO.deleteById(PID);
    }
}
