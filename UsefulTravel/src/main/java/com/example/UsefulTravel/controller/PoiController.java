package com.example.UsefulTravel.controller;

import com.example.UsefulTravel.entity.Poi;
import com.example.UsefulTravel.service.PoiService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@Controller
@RequestMapping("/poi")
public class PoiController {

    private final PoiService poiService;

    @Autowired
    public PoiController(PoiService poiService) {
        this.poiService = poiService;
    }

    // GET /poi → 景點/飯店資料庫列表 (含搜尋)
    @GetMapping
    public String list(@RequestParam(required = false) String keyword,
                        @RequestParam(required = false) String category,
                        HttpSession session, Model model) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return "redirect:/login";

        model.addAttribute("pois", poiService.search(AID, keyword, category));
        model.addAttribute("keyword", keyword);
        model.addAttribute("category", category);
        return "poi/list";
    }

    // GET /poi/new → 新增景點表單
    @GetMapping("/new")
    public String newForm(HttpSession session) {
        if (session.getAttribute("AID") == null) return "redirect:/login";
        return "poi/new";
    }

    // POST /poi/new → 新增一筆景點/餐廳/飯店資料
    @PostMapping("/new")
    public String create(@RequestParam String category,
                          @RequestParam String name,
                          @RequestParam(required = false) String country,
                          @RequestParam(required = false) String city,
                          @RequestParam(required = false) String address,
                          @RequestParam(required = false) BigDecimal latitude,
                          @RequestParam(required = false) BigDecimal longitude,
                          @RequestParam(required = false) Integer suggestedStayMin,
                          @RequestParam(required = false) String description,
                          HttpSession session) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return "redirect:/login";

        Poi poi = new Poi(AID, category, name, country, city, address, latitude, longitude);
        if (suggestedStayMin != null) poi.setSuggestedStayMin(suggestedStayMin);
        poi.setDescription(description);
        poiService.save(poi);
        return "redirect:/poi";
    }
}
