package com.example.travelereasygate.DAO;

import com.example.travelereasygate.entity.Agency;

public interface AgencyDAO {
    void save(Agency agency);
    Agency findById(int AID);
}
