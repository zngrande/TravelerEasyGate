package com.example.UsefulTravel.controller;

import com.example.UsefulTravel.DAO.CountryCityCodeDAO;
import com.example.UsefulTravel.DAO.ImageAssetDAO;
import com.example.UsefulTravel.DAO.ItineraryDAO;
import com.example.UsefulTravel.entity.CountryCityCode;
import com.example.UsefulTravel.entity.ImageAsset;
import com.example.UsefulTravel.entity.Itinerary;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
public class AgencyController {

    private final ItineraryDAO itineraryDAO;
    private final ImageAssetDAO imageAssetDAO;
    private final CountryCityCodeDAO countryCityCodeDAO;

    @Autowired
    public AgencyController(ItineraryDAO itineraryDAO, ImageAssetDAO imageAssetDAO, CountryCityCodeDAO countryCityCodeDAO) {
        this.itineraryDAO = itineraryDAO;
        this.imageAssetDAO = imageAssetDAO;
        this.countryCityCodeDAO = countryCityCodeDAO;
    }

    @GetMapping("/agency/dashboard")
    public String dashboard(HttpSession session, Model model) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return "redirect:/login";

        List<Itinerary> itineraries = itineraryDAO.findByAgency(AID);
        model.addAttribute("name", session.getAttribute("name"));
        model.addAttribute("itineraries", itineraries);

        // 篩選用: 使用者要求可以打「JP」這種國家/城市代碼篩選 (不用顯示代碼出來, 只是搜尋條件多一種),
        // 這裡把每個行程的 country/region 換算成對應的代碼字串, 塞進一個 Map<ITID, "代碼1 代碼2 ...">
        // 給前端當隱藏屬性用；country/region 本身可能是「日本、泰國」這種頓號/逗號混合的多國字串,
        // 拆解規則跟 PoiDAOImpl.splitLocationTokens() 同一套 (頓號/逗號全半形/斜線/直線/空白皆可分隔)。
        Map<String, String> countryCodeByName = new HashMap<>();
        for (CountryCityCode c : countryCityCodeDAO.findByType("country")) {
            if (c.getName() != null) countryCodeByName.put(c.getName(), c.getCode());
        }
        Map<String, String> cityCodeByName = new HashMap<>();
        for (CountryCityCode c : countryCityCodeDAO.findByType("city")) {
            if (c.getName() != null) cityCodeByName.put(c.getName(), c.getCode());
        }
        Map<Integer, String> filterCodesByItinerary = new HashMap<>();
        for (Itinerary it : itineraries) {
            Set<String> codes = new LinkedHashSet<>();
            for (String token : splitLocationTokens(it.getCountry())) {
                String code = countryCodeByName.get(token);
                if (code != null) codes.add(code);
            }
            for (String token : splitLocationTokens(it.getRegion())) {
                String code = cityCodeByName.get(token);
                if (code != null) codes.add(code);
            }
            filterCodesByItinerary.put(it.getITID(), String.join(" ", codes));
        }
        model.addAttribute("filterCodesByItinerary", filterCodesByItinerary);

        // 統計卡片: 依 status 欄位即時算出目前真實數量
        // status 欄位定義 (Itinerary.java): draft / confirmed / departed / completed
        // 進行中行程 = 還在草稿、OP 還在編輯排版的行程 (draft)
        // 已完成行程 = 在行程排版看板按下「完成行程」之後的行程 (completed, 舊資料的 confirmed/departed 也視為已完成)
        long ongoingCount = itineraries.stream()
                .filter(it -> it.getStatus() == null || "draft".equals(it.getStatus()))
                .count();
        long completedCount = itineraries.stream()
                .filter(it -> it.getStatus() != null && !"draft".equals(it.getStatus()))
                .count();
        model.addAttribute("ongoingCount", ongoingCount);
        model.addAttribute("completedCount", completedCount);

        // 素材庫圖片: 這個旅行社在「圖片資源庫」上傳的圖片總數
        List<ImageAsset> images = imageAssetDAO.findByAgency(AID);
        model.addAttribute("imageCount", images.size());

        // Hero Banner 背景輪播: 從素材庫圖片隨機挑幾張循環播放
        // (沒有上傳過圖片的話 heroImageUrls 會是空陣列, 前端會自動 fallback 回預設底圖)
        List<ImageAsset> shuffled = new ArrayList<>(images);
        Collections.shuffle(shuffled);
        List<String> heroImageUrls = new ArrayList<>();
        for (ImageAsset img : shuffled) {
            heroImageUrls.add("/images/" + img.getIAID() + "/file");
            if (heroImageUrls.size() >= 8) break;
        }
        model.addAttribute("heroImageUrls", heroImageUrls);

        return "agency/dashboard";
    }

    // 把「日本、泰國」「熊本 福岡」這種用頓號/逗號(全半形)/斜線/直線/空白混著分隔的合併字串拆成 token 清單,
    // 跟 PoiDAOImpl 的同名邏輯共用同一套拆解規則 (國家/地區欄位共用)。
    private List<String> splitLocationTokens(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split("[、,，/|\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }
}
