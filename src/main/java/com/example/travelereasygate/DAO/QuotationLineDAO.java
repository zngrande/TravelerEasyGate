package com.example.travelereasygate.DAO;

import com.example.travelereasygate.entity.QuotationLine;

import java.util.List;

public interface QuotationLineDAO {
    void save(QuotationLine line);
    QuotationLine findById(int QLID);
    List<QuotationLine> findByQuotation(int QID); // 依 sort_order 排序
    QuotationLine findByQuotationAndSourceItem(int QID, int IIID); // 簡易報價頁用: 這個行程項目有沒有對應過一筆明細
    void delete(QuotationLine line);
    void deleteByQuotation(int QID);
}
