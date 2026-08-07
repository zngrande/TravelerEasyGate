package com.example.UsefulTravel.DAO;

import com.example.UsefulTravel.entity.AiParsedDay;

import java.util.List;

public interface AiParsedDayDAO {
    void save(AiParsedDay day);
    AiParsedDay findById(int APDID);
    List<AiParsedDay> findByImport(int IPID);
}
