package com.example.travelereasygate.DAO;

import com.example.travelereasygate.entity.PoiOverride;

public interface PoiOverrideDAO {
    void save(PoiOverride override);
    // 查這間旅行社有沒有已經改寫/隱藏過某筆共用庫景點, 沒有的話回傳 null
    PoiOverride findByAgencyAndOriginal(int AID, int originalPid);
    // 反過來查: 這個 PID 是不是這間旅行社某筆 override 紀錄的「複本」PID, 是的話回傳那筆紀錄
    // (可以從中拿到 originalPid), 不是的話回傳 null。給 PoiService.delete() 判斷「刪除的這筆
    // 是不是編輯共用庫景點產生出來的複本」用。
    PoiOverride findByAgencyAndOverridePid(int AID, int overridePid);
    void delete(PoiOverride override);
}
