package com.example.UsefulTravel.controller;

import com.example.UsefulTravel.entity.Itinerary;
import com.example.UsefulTravel.entity.ItineraryDay;
import com.example.UsefulTravel.entity.ItineraryItem;
import com.example.UsefulTravel.entity.Poi;
import com.example.UsefulTravel.service.GoogleMapsClient;
import com.example.UsefulTravel.service.ItineraryService;
import com.example.UsefulTravel.service.PoiService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/itinerary")
public class ItineraryController {

    private final ItineraryService itineraryService;
    private final PoiService poiService;
    private final GoogleMapsClient googleMapsClient;

    @Autowired
    public ItineraryController(ItineraryService itineraryService, PoiService poiService, GoogleMapsClient googleMapsClient) {
        this.itineraryService = itineraryService;
        this.poiService = poiService;
        this.googleMapsClient = googleMapsClient;
    }

    // GET /itinerary/new → 建立行程表單
    @GetMapping("/new")
    public String newForm(HttpSession session) {
        if (session.getAttribute("AID") == null) return "redirect:/login";
        return "itinerary/new";
    }

    // POST /itinerary/new → 建立行程 + 自動產生 Day1~DayN 骨架
    @PostMapping("/new")
    public String create(@RequestParam String title,
                          @RequestParam String country,
                          @RequestParam(required = false) String region,
                          @RequestParam int daysCount,
                          @RequestParam(required = false) String startDate,
                          HttpSession session) {
        Integer AID = (Integer) session.getAttribute("AID");
        Integer UID = (Integer) session.getAttribute("UID");
        if (AID == null || UID == null) return "redirect:/login";

        LocalDate parsedDate = (startDate != null && !startDate.isBlank()) ? LocalDate.parse(startDate) : null;
        Itinerary itinerary = itineraryService.createItinerary(AID, UID, title, country, region, daysCount, parsedDate);
        return "redirect:/itinerary/" + itinerary.getITID() + "/board";
    }

    // GET /itinerary/{id}/board → 積木式拖曳排版看板 (核心畫面)
    @GetMapping("/{id}/board")
    public String board(@PathVariable("id") int ITID, HttpSession session, Model model) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return "redirect:/login";

        Itinerary itinerary = itineraryService.getItinerary(ITID);
        List<ItineraryDay> days = itineraryService.getDays(ITID);
        model.addAttribute("itineraryId", ITID);
        model.addAttribute("itinerary", itinerary);
        model.addAttribute("days", days);
        // 左側資料庫依這個行程的國家/地區自動篩選相關景點, 而不是列出旅行社所有國家的資料
        model.addAttribute("poiList", poiService.listForItinerary(AID,
                itinerary != null ? itinerary.getCountry() : null,
                itinerary != null ? itinerary.getRegion() : null));
        model.addAttribute("googleMapsConfigured", googleMapsClient.isConfigured());
        model.addAttribute("googleMapsApiKey", googleMapsClient.getApiKey());
        return "itinerary/board";
    }

    // POST /itinerary/{id}/delete → 刪除整個行程
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable("id") int ITID, HttpSession session, org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        if (session.getAttribute("AID") == null) return "redirect:/login";
        try {
            itineraryService.deleteItinerary(ITID);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("deleteError",
                    "刪除失敗：" + (e.getMessage() != null ? e.getMessage() : e.toString()));
        }
        return "redirect:/agency/dashboard";
    }

    // GET /itinerary/day/{IDID}/items → 取某一天的行程項目 (給前端拖曳元件用的資料 API)
    @GetMapping("/day/{IDID}/items")
    @ResponseBody
    public List<ItineraryItem> getItems(@PathVariable int IDID) {
        return itineraryService.getItems(IDID);
    }

    // POST /itinerary/day/{IDID}/items → 把景點/餐廳/自訂項目加入某一天
    @PostMapping("/day/{IDID}/items")
    @ResponseBody
    public ItineraryItem addItem(@PathVariable int IDID,
                                  @RequestParam(required = false) Integer PID,
                                  @RequestParam String itemType,
                                  @RequestParam(required = false) String customName) {
        return itineraryService.addItem(IDID, PID, itemType, customName);
    }

    // POST /itinerary/day/{IDID}/items/custom → 新增自訂項目 (不連結 POI 資料庫, 但會自動地理編碼)
    // rawName 可以用「或」分隔多個候選點, 例如「花蓮翰品酒店或día酒店或某某民宿」
    // locationHint 選填: 貼 Google 地圖網址或地址, 定位比純打名稱準確
    @PostMapping("/day/{IDID}/items/custom")
    @ResponseBody
    public ItineraryItem addCustomItem(@PathVariable int IDID,
                                        @RequestParam String itemType,
                                        @RequestParam String rawName,
                                        @RequestParam(required = false) Integer stayDurationMin,
                                        @RequestParam(required = false) String locationHint) {
        return itineraryService.addCustomItem(IDID, itemType, rawName, stayDurationMin, locationHint);
    }

    // POST /itinerary/day/{IDID}/items/{IIID}/edit → 編輯看板上已存在的項目 (名稱/停留時間/重新定位)
    @PostMapping("/day/{IDID}/items/{IIID}/edit")
    @ResponseBody
    public void editItem(@PathVariable int IIID,
                          @RequestParam String customName,
                          @RequestParam(required = false) Integer stayDurationMin,
                          @RequestParam(required = false) String locationHint) {
        itineraryService.updateItemDetails(IIID, customName, stayDurationMin, locationHint);
    }

    // GET /itinerary/day/{IDID}/items/{IIID}/options → 取得這個項目的候選點列表 (只有用「或」分隔新增的項目才有多筆)
    @GetMapping("/day/{IDID}/items/{IIID}/options")
    @ResponseBody
    public List<com.example.UsefulTravel.entity.ItineraryItemOption> getItemOptions(@PathVariable int IIID) {
        return itineraryService.getItemOptions(IIID);
    }

    // POST /itinerary/day/{IDID}/items/{IIID}/select-option → 切換要用哪個候選點 (地圖會跟著換)
    @PostMapping("/day/{IDID}/items/{IIID}/select-option")
    @ResponseBody
    public void selectItemOption(@PathVariable int IIID, @RequestParam int optionId) {
        itineraryService.selectItemOption(IIID, optionId);
    }

    // DELETE /itinerary/day/{IDID}/items/{IIID} → 移除項目
    @DeleteMapping("/day/{IDID}/items/{IIID}")
    @ResponseBody
    public void removeItem(@PathVariable int IDID, @PathVariable int IIID) {
        itineraryService.removeItem(IIID, IDID);
    }

    // GET /itinerary/day/{IDID}/routes → 取得該天所有相鄰項目的拉車距離/時間/迴頭路警示
    @GetMapping("/day/{IDID}/routes")
    @ResponseBody
    public List<com.example.UsefulTravel.entity.RouteSegment> getRoutes(@PathVariable int IDID) {
        return itineraryService.getRoutes(IDID);
    }

    // GET /itinerary/day/{IDID}/map → 取得這一天所有已定位項目的座標, 給看板畫地圖 / 匯出靜態地圖圖片用
    @GetMapping("/day/{IDID}/map")
    @ResponseBody
    public Map<String, Object> getMapData(@PathVariable int IDID) {
        List<ItineraryItem> items = itineraryService.getItems(IDID);
        List<Map<String, Object>> points = new ArrayList<>();
        List<double[]> coords = new ArrayList<>();

        for (ItineraryItem item : items) {
            double lat, lng;
            if (item.getLatitude() != null && item.getLongitude() != null) {
                lat = item.getLatitude().doubleValue();
                lng = item.getLongitude().doubleValue();
            } else if (item.getPID() != null) {
                Poi poi = poiService.findById(item.getPID());
                if (poi == null || poi.getLatitude() == null) continue;
                lat = poi.getLatitude().doubleValue();
                lng = poi.getLongitude().doubleValue();
            } else {
                continue;
            }

            Map<String, Object> point = new HashMap<>();
            point.put("lat", lat);
            point.put("lng", lng);
            point.put("name", item.getCustomName());
            points.add(point);
            coords.add(new double[]{lat, lng});
        }

        Map<String, Object> result = new HashMap<>();
        result.put("points", points);
        result.put("configured", googleMapsClient.isConfigured());
        result.put("staticMapUrl", googleMapsClient.buildStaticMapUrl(coords, 600, 400));
        return result;
    }

    // GET /itinerary/day/{IDID}/suggest-between?fromIIID=X&toIIID=Y → 智慧中途點推薦
    // 找出公司資料庫裡, 落在這兩個景點之間「順路」範圍內的其他景點/餐廳/休息站
    @GetMapping("/day/{IDID}/suggest-between")
    @ResponseBody
    public List<Poi> suggestBetween(@PathVariable int IDID,
                                     @RequestParam int fromIIID, @RequestParam int toIIID,
                                     HttpSession session) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return List.of();
        return itineraryService.suggestPoiBetween(AID, IDID, fromIIID, toIIID);
    }

    // POST /itinerary/day/{IDID}/start-time → 更新這天的出發時間 (時間軸看板用)
    @PostMapping("/day/{IDID}/start-time")
    @ResponseBody
    public void updateStartTime(@PathVariable int IDID, @RequestParam String startTime) {
        itineraryService.updateDayStartTime(IDID, java.time.LocalTime.parse(startTime));
    }

    // POST /itinerary/day/{IDID}/items/{IIID}/add-to-poi → 把這個項目寫進公司 POI 資料庫並自動連結
    @PostMapping("/day/{IDID}/items/{IIID}/add-to-poi")
    @ResponseBody
    public ResponseEntity<?> addItemToPoi(@PathVariable int IDID, @PathVariable int IIID, HttpSession session) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return ResponseEntity.status(401).build();

        try {
            itineraryService.addItemToPoi(AID, IIID);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // POST /itinerary/day/{IDID}/reorder → 拖曳排序後儲存新順序
    // body 範例: { "order": [12, 15, 13, 14] }  <- IIID 陣列
    @PostMapping("/day/{IDID}/reorder")
    @ResponseBody
    public void reorder(@PathVariable int IDID, @RequestBody Map<String, List<Integer>> body) {
        itineraryService.reorderItems(IDID, body.get("order"));
    }
}
