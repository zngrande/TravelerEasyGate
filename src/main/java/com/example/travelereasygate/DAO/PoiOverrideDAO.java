package com.example.travelereasygate.DAO;

import com.example.travelereasygate.entity.PoiOverride;

public interface PoiOverrideDAO {
    void save(PoiOverride override);
    // 查這間旅行社有沒有已經改寫/隱藏過某筆共用庫景點, 沒有的話回傳 null
    PoiOverride findByAgencyAndOriginal(int AID, int originalPid);
}
