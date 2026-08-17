package com.example.UsefulTravel.DAO;

import com.example.UsefulTravel.entity.QuotationLineTier;

import java.util.List;

public interface QuotationLineTierDAO {
    void save(QuotationLineTier tier);
    QuotationLineTier findById(int QLTID);
    List<QuotationLineTier> findByLine(int QLID); // 依 sort_order 排序
    void delete(QuotationLineTier tier);
    void deleteByLine(int QLID);
}
