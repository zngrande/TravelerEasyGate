package com.example.travelereasygate.controller;

import com.example.travelereasygate.DAO.CountryCityCodeDAO;
import com.example.travelereasygate.entity.CountryCityCode;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 國家/城市通用代碼參考表的查詢入口 (目前只有查詢用的 API, 沒有另外做管理畫面;
 * 代碼資料由 db/migration_ai_itinerary_context.sql 內建的初始資料提供, 之後可直接在資料庫增修)。
 *
 * /autocomplete 是既有的打字篩選用 API (機場等單選欄位在用); /countries、/cities 是「建立新行程」頁面
 * 「目的地國家/地區」多選清單新增的, 回傳整包資料不做關鍵字篩選、也不設筆數上限 (交給前端自己篩選/勾選,
 * 這張表資料量本來就小)。
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

    // GET /reference/country-city/countries → 「建立新行程」頁面「目的地國家」多選清單用: 回傳整張參考表
    // 裡的全部國家 (資料量小, 不用分頁/關鍵字篩選, 前端自己做打字篩選/勾選)
    @GetMapping("/countries")
    @ResponseBody
    public ResponseEntity<?> countries(HttpSession session) {
        if (session.getAttribute("AID") == null) return ResponseEntity.status(401).build();
        List<Map<String, Object>> result = countryCityCodeDAO.findByType("country").stream()
                .map(c -> Map.<String, Object>of("code", c.getCode(), "name", c.getName()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    // GET /reference/country-city/cities?countryCodes=JP,TW → 依已選定的國家代碼清單 (逗號分隔, 多國行程
    // 時會有多個) 回傳這些國家底下的城市, 用於「地區/城市」多選面板 (依國家分組顯示)
    @GetMapping("/cities")
    @ResponseBody
    public ResponseEntity<?> cities(@RequestParam(required = false) String countryCodes, HttpSession session) {
        if (session.getAttribute("AID") == null) return ResponseEntity.status(401).build();
        List<String> codes = (countryCodes == null || countryCodes.isBlank())
                ? List.of()
                : Arrays.stream(countryCodes.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
        List<Map<String, Object>> result = countryCityCodeDAO.findCitiesByCountryCodes(codes).stream()
                .map(c -> Map.<String, Object>of(
                        "code", c.getCode(),
                        "name", c.getName(),
                        "countryCode", c.getCountryCode() != null ? c.getCountryCode() : ""))
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }
}
