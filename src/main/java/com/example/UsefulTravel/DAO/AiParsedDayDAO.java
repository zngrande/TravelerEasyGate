package com.example.UsefulTravel.DAO;

import com.example.UsefulTravel.entity.AiParsedDay;

import java.util.List;

public interface AiParsedDayDAO {
    void save(AiParsedDay day);
    List<AiParsedDay> findByImport(int IPID);
}
