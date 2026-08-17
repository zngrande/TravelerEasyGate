package com.example.UsefulTravel.DAO;

import com.example.UsefulTravel.entity.PriceTierTemplate;

import java.util.List;

public interface PriceTierTemplateDAO {
    void save(PriceTierTemplate template);
    PriceTierTemplate findById(int PTTID);
    List<PriceTierTemplate> findByAgency(int AID);
    void delete(PriceTierTemplate template);
}
