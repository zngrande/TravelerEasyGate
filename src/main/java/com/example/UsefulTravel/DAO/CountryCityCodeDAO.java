package com.example.UsefulTravel.DAO;

import com.example.UsefulTravel.entity.CountryCityCode;

import java.util.List;

public interface CountryCityCodeDAO {
    void save(CountryCityCode countryCityCode);
    CountryCityCode findById(int CCID);
    // keyword 比對 name 或 code (自動完成用); type 可選 country/city, 不給就兩種都比對
    List<CountryCityCode> searchByKeyword(String keyword, String type);
}
