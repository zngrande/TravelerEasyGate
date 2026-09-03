package com.example.travelereasygate.DAO;

import com.example.travelereasygate.entity.PriceTierTemplateRow;

import java.util.List;

public interface PriceTierTemplateRowDAO {
    void save(PriceTierTemplateRow row);
    List<PriceTierTemplateRow> findByTemplate(int PTTID); // 依 sort_order 排序
    void deleteByTemplate(int PTTID);
}
