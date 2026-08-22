package com.example.UsefulTravel.DAO;

import com.example.UsefulTravel.entity.CountryCityCode;

import java.util.List;

public interface CountryCityCodeDAO {
    void save(CountryCityCode countryCityCode);
    CountryCityCode findById(int CCID);
    // keyword 比對 name 或 code (自動完成用); type 可選 country/city, 不給就兩種都比對
    List<CountryCityCode> searchByKeyword(String keyword, String type);

    // 「建立新行程」頁面國家/地區多選清單用: 這張表資料量小 (十幾到幾十筆), 不用分頁/關鍵字篩選,
    // 整包撈回來給前端自己做勾選/打字篩選 (searchByKeyword 有 setMaxResults(20) 的上限, 不適合這裡)
    List<CountryCityCode> findByType(String type);
    // 依已選定的國家代碼清單 (多選, 多國行程) 回傳這些國家底下的城市, 依國家代碼分組排序
    List<CountryCityCode> findCitiesByCountryCodes(List<String> countryCodes);
}
