package com.example.UsefulTravel.controller;

import com.example.UsefulTravel.entity.Poi;
import com.example.UsefulTravel.service.GoogleMapsClient;
import com.example.UsefulTravel.service.ImageAssetService;
import com.example.UsefulTravel.service.PoiService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/poi")
public class PoiController {

    private final PoiService poiService;
    private final GoogleMapsClient googleMapsClient;
    private final ImageAssetService imageAssetService;

    @Autowired
    public PoiController(PoiService poiService, GoogleMapsClient googleMapsClient, ImageAssetService imageAssetService) {
        this.poiService = poiService;
        this.googleMapsClient = googleMapsClient;
        this.imageAssetService = imageAssetService;
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

    // POST /poi/new → 新增一筆景點/餐廳/飯店資料 (經緯度沒填會自動地理編碼)
    @PostMapping("/new")
    public String create(@RequestParam String category,
                          @RequestParam String name,
                          @RequestParam(required = false) String originalName,
                          @RequestParam(required = false) String country,
                          @RequestParam(required = false) String city,
                          @RequestParam(required = false) String address,
                          @RequestParam(required = false) BigDecimal latitude,
                          @RequestParam(required = false) BigDecimal longitude,
                          @RequestParam(required = false) Integer suggestedStayMin,
                          @RequestParam(required = false) String description,
                          @RequestParam(required = false) BigDecimal agencyPrice,
                          @RequestParam(required = false) String supplierContact,
                          @RequestParam(required = false) String supplierNotes,
                          HttpSession session) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return "redirect:/login";

        Poi poi = new Poi(AID, category, name, country, city, address, latitude, longitude);
        poi.setOriginalName(originalName);
        if (suggestedStayMin != null) poi.setSuggestedStayMin(suggestedStayMin);
        poi.setDescription(description);
        poi.setAgencyPrice(agencyPrice);
        poi.setSupplierContact(supplierContact);
        poi.setSupplierNotes(supplierNotes);

        if (latitude == null || longitude == null) {
            String query = String.join(" ",
                    name, city != null ? city : "", country != null ? country : "").trim();
            GoogleMapsClient.GeocodeResult geo = googleMapsClient.findPlace(query, country);
            if (geo == null) {
                geo = googleMapsClient.geocode(query, country);
            }
            if (geo != null) {
                poi.setLatitude(BigDecimal.valueOf(geo.latitude));
                poi.setLongitude(BigDecimal.valueOf(geo.longitude));
            }
        }

        poiService.save(poi);
        return "redirect:/poi";
    }

    // GET /poi/autocomplete?keyword=成田&category=airport → 給輸入框打字即時篩選用 (JSON)
    // category 可選 (例如只找機場), 不給就整個 POI 資料庫一起比對
    @GetMapping("/autocomplete")
    @ResponseBody
    public ResponseEntity<?> autocomplete(@RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) String category,
                                          HttpSession session) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return ResponseEntity.status(401).build();

        List<Poi> matches = poiService.search(AID, keyword, category);
        List<Map<String, Object>> result = matches.stream()
                .limit(20)
                .map(p -> Map.<String, Object>of(
                        "pid", p.getPID(),
                        "name", p.getName() != null ? p.getName() : "",
                        "originalName", p.getOriginalName() != null ? p.getOriginalName() : "",
                        "category", p.getCategory() != null ? p.getCategory() : ""))
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    // GET /poi/{id}/description → 取得這個景點目前的介紹說明 (給行程編輯畫面的編輯表單用, AJAX)
    @GetMapping("/{id}/description")
    @ResponseBody
    public ResponseEntity<?> getDescription(@PathVariable("id") int PID, HttpSession session) {
        if (session.getAttribute("AID") == null) return ResponseEntity.status(401).build();
        Poi poi = poiService.findById(PID);
        if (poi == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(java.util.Map.of("description", poi.getDescription() != null ? poi.getDescription() : ""));
    }

    // POST /poi/{id}/description → 在行程編輯畫面修改景點介紹說明後, 同步存回 POI 資料庫 (只更新這一個欄位, AJAX)
    @PostMapping("/{id}/description")
    @ResponseBody
    public ResponseEntity<?> updateDescription(@PathVariable("id") int PID,
                                                @RequestParam String description,
                                                HttpSession session) {
        if (session.getAttribute("AID") == null) return ResponseEntity.status(401).build();
        try {
            poiService.updateDescription(PID, description);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // GET /poi/{id}/edit → 編輯景點表單
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable("id") int PID, HttpSession session, Model model) {
        if (session.getAttribute("AID") == null) return "redirect:/login";

        Poi poi = poiService.findById(PID);
        if (poi == null) return "redirect:/poi";

        model.addAttribute("poi", poi);
        model.addAttribute("images", imageAssetService.listForPoi(PID));
        return "poi/edit";
    }

    // POST /poi/{id}/edit → 儲存編輯 (經緯度留空會自動重新地理編碼)
    @PostMapping("/{id}/edit")
    public String edit(@PathVariable("id") int PID,
                        @RequestParam String category,
                        @RequestParam String name,
                        @RequestParam(required = false) String originalName,
                        @RequestParam(required = false) String country,
                        @RequestParam(required = false) String city,
                        @RequestParam(required = false) String address,
                        @RequestParam(required = false) BigDecimal latitude,
                        @RequestParam(required = false) BigDecimal longitude,
                        @RequestParam(required = false) Integer suggestedStayMin,
                        @RequestParam(required = false) String description,
                        @RequestParam(required = false) BigDecimal agencyPrice,
                        @RequestParam(required = false) String supplierContact,
                        @RequestParam(required = false) String supplierNotes,
                        HttpSession session) {
        if (session.getAttribute("AID") == null) return "redirect:/login";

        Poi poi = poiService.findById(PID);
        if (poi == null) return "redirect:/poi";

        poi.setCategory(category);
        poi.setName(name);
        poi.setOriginalName(originalName);
        poi.setCountry(country);
        poi.setCity(city);
        poi.setAddress(address);
        poi.setSuggestedStayMin(suggestedStayMin);
        poi.setDescription(description);
        poi.setAgencyPrice(agencyPrice);
        poi.setSupplierContact(supplierContact);
        poi.setSupplierNotes(supplierNotes);

        if (latitude != null && longitude != null) {
            poi.setLatitude(latitude);
            poi.setLongitude(longitude);
        } else {
            // 經緯度留空: 用最新的名稱/地址重新查一次, 找不到就保留原本的值 (不清空)
            // 先試 Places API (對店名/景點名準確率高很多), 找不到再 fallback Geocoding API (適合純地址)
            String query = String.join(" ",
                    name, city != null ? city : "", country != null ? country : "").trim();
            GoogleMapsClient.GeocodeResult geo = googleMapsClient.findPlace(query, country);
            if (geo == null) {
                geo = googleMapsClient.geocode(query, country);
            }
            if (geo != null) {
                poi.setLatitude(BigDecimal.valueOf(geo.latitude));
                poi.setLongitude(BigDecimal.valueOf(geo.longitude));
            }
        }

        poiService.save(poi);
        return "redirect:/poi/" + PID + "/edit";
    }

    // POST /poi/{id}/delete → 刪除景點
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable("id") int PID, HttpSession session,
                          org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        if (session.getAttribute("AID") == null) return "redirect:/login";
        try {
            poiService.delete(PID);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("deleteError",
                    "刪除失敗：" + (e.getMessage() != null ? e.getMessage() : e.toString()));
        }
        return "redirect:/poi";
    }
}
