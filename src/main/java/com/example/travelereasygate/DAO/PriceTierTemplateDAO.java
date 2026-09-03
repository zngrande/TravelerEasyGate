package com.example.travelereasygate.DAO;

import com.example.travelereasygate.entity.PriceTierTemplate;

import java.util.List;

public interface PriceTierTemplateDAO {
    void save(PriceTierTemplate template);
    PriceTierTemplate findById(int PTTID);
    List<PriceTierTemplate> findByAgency(int AID);
    void delete(PriceTierTemplate template);
}
