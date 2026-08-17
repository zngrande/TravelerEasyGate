package com.example.UsefulTravel.DAO;

import com.example.UsefulTravel.entity.Currency;

import java.util.List;

public interface CurrencyDAO {
    void save(Currency currency);
    Currency findById(int CID);
    Currency findByCode(String code, Integer AID); // 先找該旅行社自訂匯率, 找不到再退回平台共用匯率
    List<Currency> findAvailable(Integer AID); // 平台共用 + 該旅行社自訂
}
