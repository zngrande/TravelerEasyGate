package com.example.UsefulTravel.DAO;

import com.example.UsefulTravel.entity.Poi;

import java.util.List;

public interface PoiDAO {
    void save(Poi poi);
    Poi findById(int PID);
    List<Poi> findByAgencyOrShared(Integer AID);
    List<Poi> findByAgencyAndCountry(Integer AID, String country, String region);
    List<Poi> searchByKeyword(Integer AID, String keyword, String category);
    void deleteById(int PID);

    // 「建立新行程」頁面「目的地國家/地區」欄位自動完成用: 只列出公司景點資料庫「實際存在」的國家/城市
    // (共用庫 + 自己的), 確保使用者選出來的地名一定查得到景點候選 (見 PoiDAOImpl 實作註解)
    List<String> findDistinctCountries(Integer AID);
    List<String> findDistinctCitiesByCountry(Integer AID, String country);
}
