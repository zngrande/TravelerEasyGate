package com.example.UsefulTravel.DAO;

import com.example.UsefulTravel.entity.MarginSetting;

import java.util.List;

public interface MarginSettingDAO {
    void save(MarginSetting setting);
    MarginSetting findById(int MSID);
    List<MarginSetting> findByAgency(int AID);
    MarginSetting findDefault(int AID);
    void deleteById(int MSID);

    // 把這間旅行社目前所有規則的預設狀態都清掉, 用在「設定新的預設規則」之前, 確保同時間只會有一組預設
    void clearDefault(int AID);

    // 這個規則有沒有已經被某張報價單引用 (quotation.MSID), 引用中就不能刪除
    long countQuotationUsage(int MSID);
}