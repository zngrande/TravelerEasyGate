package com.example.travelereasygate.controller;

import com.example.travelereasygate.entity.Poi;
import com.example.travelereasygate.service.GoogleMapsClient;
import com.example.travelereasygate.service.ImageAssetService;
import com.example.travelereasygate.service.PermissionService;
import com.example.travelereasygate.service.PoiService;
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
    private final PermissionService permissionService;

    @Autowired
    public PoiController(PoiService poiService, GoogleMapsClient googleMapsClient,
                          ImageAssetService imageAssetService, PermissionService permissionService) {
        this.poiService = poiService;
        this.googleMapsClient = googleMapsClient;
        this.imageAssetService = imageAssetService;
        this.permissionService = permissionService;
    }

    // 景點資料庫是「編輯行程」的準備工作 (排版時要用), 對應側邊欄「資料庫管理」的顯示條件,
    // 只有 ADMIN / EDITOR 能用；QUOTER / VIEWER 直接打網址進來也會被這裡擋掉、導回首頁。
    private boolean canEditItinerary(HttpSession session) {
        return permissionService.canEditItinerary((String) session.getAttribute("role"));
    }

    // GET /poi → 景點/飯店資料庫列表 (含搜尋)
    @GetMapping
    public String list(@RequestParam(required = false) String keyword,
                        @RequestParam(required = false) String category,
                        @RequestParam(required = false) String location,
                        HttpSession session, Model model) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return "redirect:/login";
        if (!canEditItinerary(session)) return "redirect:/agency/dashboard";

        model.addAttribute("pois", poiService.search(AID, keyword, category, location));
        model.addAttribute("keyword", keyword);
        model.addAttribute("category", category);
        model.addAttribute("location", location);
        return "poi/list";
    }

    // GET /poi/new → 新增景點表單
    @GetMapping("/new")
    public String newForm(HttpSession session) {
        if (session.getAttribute("AID") == null) return "redirect:/login";
        if (!canEditItinerary(session)) return "redirect:/agency/dashboard";
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
        if (!canEditItinerary(session)) return "redirect:/agency/dashboard";

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
        if (!canEditItinerary(session)) return ResponseEntity.status(403).build();

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

    // GET /poi/countries?keyword= → 「建立新行程」頁面「目的地國家」欄位比對「景點資料庫實際有哪些國家」用
    // (country_city_code 參考表選單裡, 沒在這份清單內的國家會標示 ⚠ 警示, 見 itinerary/new.html)。
    // 原本這個欄位是純自由文字, 跟後端 AI 排程篩選候選景點用的是同一套自由文字比對, 但完全不保證使用者
    // 打的地名資料庫裡真的有 (使用者建立「義大利/羅馬、威尼斯、比薩、米蘭」行程時就是打了一個資料庫裡
    // 根本沒有資料的國家, AI 排程當然找不到任何候選, 只能建立空白行程)。
    //
    // 注意: 這裡回傳的是「資料庫裡實際存在哪些國家」的完整清單, 用來跟另一份清單做「有沒有資料」的存在性
    // 比對 (前端組成 Set 直接查 has()), 不是打字篩選建議清單那種只需要列前幾筆的情境, 所以不能加 .limit(20)
    // ——本來這裡仿造 /poi/autocomplete 的寫法多加了 .limit(20), 結果實際資料庫有 30 幾個不同國家, 超過
    // 20 筆的部分 (含日本, 依資料庫排序不保證落在前 20 筆內) 會被截掉不列入回傳清單, 對照時就會被前端誤判
    // 成「資料庫沒有這個國家的資料」而顯示警示, 但那些國家其實都有大量景點資料, 是這裡的程式邏輯錯誤,
    // 不是真的缺資料 (keyword 篩選則保留, 用於 /poi/autocomplete 以外、有打字關鍵字時的情境)。
    @GetMapping("/countries")
    @ResponseBody
    public ResponseEntity<?> countries(@RequestParam(required = false) String keyword, HttpSession session) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return ResponseEntity.status(401).build();
        if (!canEditItinerary(session)) return ResponseEntity.status(403).build();
        String kw = keyword == null ? "" : keyword.trim();
        List<String> result = poiService.listCountries(AID).stream()
                .filter(c -> kw.isEmpty() || c.contains(kw))
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    // GET /poi/cities?country=日本&keyword= → 同上, 「地區/城市」欄位比對「這個國家在景點資料庫裡實際有
    // 哪些城市」用。同樣是完整存在性清單, 不能加 .limit(20) (理由同上面 countries() 的說明)。
    @GetMapping("/cities")
    @ResponseBody
    public ResponseEntity<?> cities(@RequestParam String country,
                                    @RequestParam(required = false) String keyword,
                                    HttpSession session) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return ResponseEntity.status(401).build();
        if (!canEditItinerary(session)) return ResponseEntity.status(403).build();
        String kw = keyword == null ? "" : keyword.trim();
        List<String> result = poiService.listCitiesByCountry(AID, country).stream()
                .filter(c -> kw.isEmpty() || c.contains(kw))
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    // GET /poi/{id}/description → 取得這個景點目前的介紹說明 (給行程編輯畫面的編輯表單用, AJAX)
    @GetMapping("/{id}/description")
    @ResponseBody
    public ResponseEntity<?> getDescription(@PathVariable("id") int PID, HttpSession session) {
        if (session.getAttribute("AID") == null) return ResponseEntity.status(401).build();
        if (!canEditItinerary(session)) return ResponseEntity.status(403).build();
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
        if (!canEditItinerary(session)) return ResponseEntity.status(403).build();
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
        if (!canEditItinerary(session)) return "redirect:/agency/dashboard";

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
        if (!canEditItinerary(session)) return "redirect:/agency/dashboard";

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
    // 使用者要求：共用庫的景點/餐廳（AID=NULL）不能被任何旅行社刪除（也不再提供「隱藏」這個替代動作），
    // 一律擋下並顯示錯誤訊息，只有這間旅行社自己新增的景點（AID = 自己的 AID）才能真的刪除。
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable("id") int PID, HttpSession session,
                          org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return "redirect:/login";
        if (!canEditItinerary(session)) return "redirect:/agency/dashboard";
        Poi poi = poiService.findById(PID);
        if (poi == null) return "redirect:/poi";
        if (poi.getAID() != null && !poi.getAID().equals(AID)) return "redirect:/poi";

        if (poi.getAID() == null) {
            String category = poi.getCategory() != null ? poi.getCategory() : "景點/餐廳";
            redirectAttributes.addFlashAttribute("deleteError",
                    "「" + poi.getName() + "」為共用" + category + "，無法刪除");
            return "redirect:/poi";
        }

        try {
            poiService.delete(PID);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("deleteError",
                    "刪除失敗：" + (e.getMessage() != null ? e.getMessage() : e.toString()));
        }
        return "redirect:/poi";
    }
}
