package com.example.travelereasygate.DAO;

import com.example.travelereasygate.entity.QuotationGroupTier;

import java.util.List;

public interface QuotationGroupTierDAO {
    void save(QuotationGroupTier tier);
    QuotationGroupTier findById(int QGTID);
    List<QuotationGroupTier> findByQuotation(int QID); // 依 sort_order / min_qty 排序
    void delete(QuotationGroupTier tier);
    void deleteByQuotation(int QID);
}
