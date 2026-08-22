package com.example.UsefulTravel.controller;

import com.example.UsefulTravel.DAO.CountryCityCodeDAO;
import com.example.UsefulTravel.entity.CountryCityCode;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 國家/城市通用代碼參考表的查詢入口 (目前只有自動完成用的 API, 沒有另外做管理畫面;
 * 代碼資料由 db/migration_ai_itinerary_context.sql 內建的初始資料提供, 之後可直接在資料庫增修)
 */
@Controller
@RequestMapping("/reference/country-city")
public class ReferenceCodeController {

    private final CountryCityCodeDAO countryCityCodeDAO;

    @Autowired
    public ReferenceCodeController(CountryCityCodeDAO countryCityCodeDAO) {
        this.countryCityCodeDAO = countryCityCodeDAO;
    }

    // GET /reference/country-city/autocomplete?keyword=東&type=city → 給輸入框打字即時篩選用 (JSON)
    @GetMapping("/autocomplete")
    @ResponseBody
    public ResponseEntity<?> autocomplete(@RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) String type,
                                          HttpSession session) {
        if (session.getAttribute("AID") == null) return ResponseEntity.status(401).build();

        List<CountryCityCode> matches = countryCityCodeDAO.searchByKeyword(keyword, type);
        List<Map<String, Object>> result = matches.stream()
                .map(c -> Map.<String, Object>of(
                        "type", c.getType(),
                        "code", c.getCode(),
                        "name", c.getName(),
                        "countryCode", c.getCountryCode() != null ? c.getCountryCode() : ""))
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }
}
