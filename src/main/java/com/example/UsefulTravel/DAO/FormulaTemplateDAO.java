package com.example.UsefulTravel.DAO;

import com.example.UsefulTravel.entity.FormulaTemplate;

import java.util.List;

public interface FormulaTemplateDAO {
    void save(FormulaTemplate template);
    FormulaTemplate findById(int FTID);
    List<FormulaTemplate> findByAgency(int AID);
    void delete(FormulaTemplate template);
}
