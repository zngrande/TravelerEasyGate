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

    // GET /poi/autocomplete?keyword=成田&category=機場 → 給輸入框打字即時篩選用 (JSON)
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

    // GET /poi/countries?keyword= → 「建立新行程」頁面「目的地國家」欄位自動完成用。
    // 原本這個欄位是純自由文字, 跟後端 AI 排程篩選候選景點用的是同一套自由文字比對, 但完全不保證使用者
    // 打的地名資料庫裡真的有 (使用者建立「義大利/羅馬、威尼斯、比薩、米蘭」行程時就是打了一個資料庫裡
    // 根本沒有資料的國家, AI 排程當然找不到任何候選, 只能建立空白行程)。改成只列出「公司景點資料庫裡
    // 實際存在」的國家 (共用庫+自己的, 見 PoiService/PoiDAOImpl), 選出來的國家保證查得到景點候選。
    @GetMapping("/countries")
    @ResponseBody
    public ResponseEntity<?> countries(@RequestParam(required = false) String keyword, HttpSession session) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return ResponseEntity.status(401).build();
        String kw = keyword == null ? "" : keyword.trim();
        List<String> result = poiService.listCountries(AID).stream()
                .filter(c -> kw.isEmpty() || c.contains(kw))
                .limit(20)
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    // GET /poi/cities?country=日本&keyword= → 「建立新行程」頁面「地區/城市」欄位自動完成用,
    // 依已選定的「目的地國家」篩選出該國實際存在的城市清單 (「地區依國家判斷」)。
    @GetMapping("/cities")
    @ResponseBody
    public ResponseEntity<?> cities(@RequestParam String country,
                                    @RequestParam(required = false) String keyword,
                                    HttpSession session) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return ResponseEntity.status(401).build();
        String kw = keyword == null ? "" : keyword.trim();
        List<String> result = poiService.listCitiesByCountry(AID, country).stream()
                .filter(c -> kw.isEmpty() || c.contains(kw))
                .limit(20)
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
    // iiid 選填: 從哪個行程項目觸發的, 如果這筆景點是共用庫的, 存檔會建立公司專屬複本, 順便把這個項目
    // 的連結改指向新複本 (見 PoiService.updateDescription 的說明)
    @PostMapping("/{id}/description")
    @ResponseBody
    public ResponseEntity<?> updateDescription(@PathVariable("id") int PID,
                                                @RequestParam String description,
                                                @RequestParam(required = false) Integer iiid,
                                                HttpSession session) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return ResponseEntity.status(401).build();
        try {
            Poi saved = poiService.updateDescription(AID, PID, description, iiid);
            return ResponseEntity.ok(java.util.Map.of("pid", saved.getPID()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // GET /poi/{id}/edit → 編輯景點表單
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable("id") int PID, HttpSession session, Model model) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return "redirect:/login";

        Poi poi = poiService.findById(PID);
        if (poi == null) return "redirect:/poi";
        // 別間旅行社自建的景點 (AID 不是 null 也不是自己) 不能編輯; 共用庫 (AID == null) 大家都能編輯 (存檔時會走複製流程)
        if (poi.getAID() != null && !poi.getAID().equals(AID)) return "redirect:/poi";

        model.addAttribute("poi", poi);
        model.addAttribute("images", imageAssetService.listForPoi(PID, AID));
        model.addAttribute("isSharedPoi", poi.getAID() == null);
        return "poi/edit";
    }

    // POST /poi/{id}/edit → 儲存編輯 (經緯度留空會自動重新地理編碼)
    // 編輯共用庫 (AID IS NULL) 的景點時: 不會直接改到共用庫本身 (會影響其他旅行社), 而是複製一份
    // 變成這間旅行社自己專屬的景點, 之後這間旅行社看到的都是自己的複本, 共用庫原始那筆對它來說會被隱藏。
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
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return "redirect:/login";

        Poi original = poiService.findById(PID);
        if (original == null) return "redirect:/poi";
        if (original.getAID() != null && !original.getAID().equals(AID)) return "redirect:/poi";

        boolean isShared = original.getAID() == null;

        BigDecimal finalLatitude = latitude;
        BigDecimal finalLongitude = longitude;
        if (finalLatitude == null || finalLongitude == null) {
            // 經緯度留空: 用最新的名稱/地址重新查一次, 找不到就保留原本的值 (不清空)
            // 先試 Places API (對店名/景點名準確率高很多), 找不到再 fallback Geocoding API (適合純地址)
            String query = String.join(" ",
                    name, city != null ? city : "", country != null ? country : "").trim();
            GoogleMapsClient.GeocodeResult geo = googleMapsClient.findPlace(query, country);
            if (geo == null) {
                geo = googleMapsClient.geocode(query, country);
            }
            if (geo != null) {
                finalLatitude = BigDecimal.valueOf(geo.latitude);
                finalLongitude = BigDecimal.valueOf(geo.longitude);
            } else {
                finalLatitude = original.getLatitude();
                finalLongitude = original.getLongitude();
            }
        }

        if (isShared) {
            Poi copy = new Poi(AID, category, name, country, city, address, finalLatitude, finalLongitude);
            copy.setOriginalName(originalName);
            copy.setSuggestedStayMin(suggestedStayMin);
            copy.setDescription(description);
            copy.setAgencyPrice(agencyPrice);
            copy.setSupplierContact(supplierContact);
            copy.setSupplierNotes(supplierNotes);
            Poi saved = poiService.overrideSharedPoi(AID, original, copy);
            return "redirect:/poi/" + saved.getPID() + "/edit";
        }

        original.setCategory(category);
        original.setName(name);
        original.setOriginalName(originalName);
        original.setCountry(country);
        original.setCity(city);
        original.setAddress(address);
        original.setSuggestedStayMin(suggestedStayMin);
        original.setDescription(description);
        original.setAgencyPrice(agencyPrice);
        original.setSupplierContact(supplierContact);
        original.setSupplierNotes(supplierNotes);
        original.setLatitude(finalLatitude);
        original.setLongitude(finalLongitude);

        poiService.save(original);
        return "redirect:/poi/" + PID + "/edit";
    }

    // POST /poi/{id}/delete → 刪除景點
    // 共用庫的景點「刪除」不會真的刪掉 (其他旅行社還要看得到), 只會記一筆隱藏紀錄, 讓這間旅行社之後看不到它
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable("id") int PID, HttpSession session,
                          org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return "redirect:/login";
        Poi poi = poiService.findById(PID);
        if (poi == null) return "redirect:/poi";
        if (poi.getAID() != null && !poi.getAID().equals(AID)) return "redirect:/poi";

        try {
            if (poi.getAID() == null) {
                poiService.hideSharedPoi(AID, PID);
            } else {
                poiService.delete(PID);
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("deleteError",
                    "刪除失敗：" + (e.getMessage() != null ? e.getMessage() : e.toString()));
        }
        return "redirect:/poi";
    }
}
