package com.example.UsefulTravel.DAO;

import com.example.UsefulTravel.entity.Agency;

public interface AgencyDAO {
    void save(Agency agency);
    Agency findById(int AID);
}
