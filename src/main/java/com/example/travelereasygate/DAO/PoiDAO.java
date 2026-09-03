package com.example.travelereasygate.DAO;

import com.example.travelereasygate.entity.Poi;

import java.util.List;

public interface PoiDAO {
    void save(Poi poi);
    Poi findById(int PID);
    List<Poi> findByAgencyOrShared(Integer AID);
    List<Poi> findByAgencyAndCountry(Integer AID, String country, String region);
    List<Poi> searchByKeyword(Integer AID, String keyword, String category);
    // 給 poi/list.html「國家 / 城市」篩選欄位用: 在 keyword/category 的基礎上再多一個 location 條件
    // (比對 poi.country OR poi.city, LIKE), location 是使用者用 country_city_code 自動完成選出來的
    // 顯示名稱 (見 PoiController.list())。location 為 null/空字串時行為跟上面三參數版一致。
    List<Poi> searchByKeyword(Integer AID, String keyword, String category, String location);
    void deleteById(int PID);

    // 「建立新行程」頁面「目的地國家/地區」欄位自動完成用: 只列出公司景點資料庫「實際存在」的國家/城市
    // (共用庫 + 自己的), 確保使用者選出來的地名一定查得到景點候選 (見 PoiDAOImpl 實作註解)
    List<String> findDistinctCountries(Integer AID);
    List<String> findDistinctCitiesByCountry(Integer AID, String country);
}
