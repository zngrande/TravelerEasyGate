package com.example.travelereasygate.DAO;

import com.example.travelereasygate.entity.Quotation;

import java.util.List;

public interface QuotationDAO {
    void save(Quotation quotation);
    Quotation findById(int QID);
    List<Quotation> findByItinerary(int ITID); // 依版本新到舊排序
    int nextVersion(int ITID); // 這個行程下一個版本號
    void delete(Quotation quotation);
}
