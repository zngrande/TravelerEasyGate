package com.example.UsefulTravel.DAO;

import com.example.UsefulTravel.entity.FormulaTemplateLine;

import java.util.List;

public interface FormulaTemplateLineDAO {
    void save(FormulaTemplateLine line);
    List<FormulaTemplateLine> findByTemplate(int FTID);
    void deleteByTemplate(int FTID);
}
