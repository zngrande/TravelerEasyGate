package com.example.UsefulTravel.DAO;

import com.example.UsefulTravel.entity.AiImport;

import java.util.List;

public interface AiImportDAO {
    void save(AiImport aiImport);
    AiImport findById(int IPID);
    List<AiImport> findByAgency(int AID);
}
