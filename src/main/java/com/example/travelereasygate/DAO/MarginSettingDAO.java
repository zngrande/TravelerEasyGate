package com.example.travelereasygate.DAO;

import com.example.travelereasygate.entity.MarginSetting;

import java.util.List;

public interface MarginSettingDAO {
    void save(MarginSetting setting);
    MarginSetting findById(int MSID);
    List<MarginSetting> findByAgency(int AID);
    void deleteById(int MSID);

    // 「報價定價規則」跟「NP／團費成本規則」各自獨立的預設規則查詢/清除, 取代原本共用一個
    // is_default 欄位的 findDefault()/clearDefault() (見 MarginSetting#defaultPricing/defaultTier
    // 欄位上的說明)。
    MarginSetting findDefaultPricing(int AID);
    MarginSetting findDefaultTier(int AID);
    void clearDefaultPricing(int AID);
    void clearDefaultTier(int AID);

    // 這個規則有沒有已經被某張報價單引用 (quotation.MSID), 引用中就不能刪除
    long countQuotationUsage(int MSID);
}