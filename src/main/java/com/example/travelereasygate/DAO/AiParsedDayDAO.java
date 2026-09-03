package com.example.travelereasygate.DAO;

import com.example.travelereasygate.entity.AiParsedDay;

import java.util.List;

public interface AiParsedDayDAO {
    void save(AiParsedDay day);
    AiParsedDay findById(int APDID);
    List<AiParsedDay> findByImport(int IPID);
}
