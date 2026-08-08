package com.example.UsefulTravel.controller;

import com.example.UsefulTravel.entity.Itinerary;
import com.example.UsefulTravel.entity.ItineraryDay;
import com.example.UsefulTravel.entity.ItineraryItem;
import com.example.UsefulTravel.service.ItineraryService;
import com.example.UsefulTravel.service.PoiService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/itinerary")
public class ItineraryController {

    private final ItineraryService itineraryService;
    private final PoiService poiService;

    @Autowired
    public ItineraryController(ItineraryService itineraryService, PoiService poiService) {
        this.itineraryService = itineraryService;
        this.poiService = poiService;
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
                          @RequestParam int daysCount,
                          @RequestParam(required = false) String startDate,
                          HttpSession session) {
        Integer AID = (Integer) session.getAttribute("AID");
        Integer UID = (Integer) session.getAttribute("UID");
        if (AID == null || UID == null) return "redirect:/login";

        LocalDate parsedDate = (startDate != null && !startDate.isBlank()) ? LocalDate.parse(startDate) : null;
        Itinerary itinerary = itineraryService.createItinerary(AID, UID, title, country, daysCount, parsedDate);
        return "redirect:/itinerary/" + itinerary.getITID() + "/board";
    }

    // GET /itinerary/{id}/board → 積木式拖曳排版看板 (核心畫面)
    @GetMapping("/{id}/board")
    public String board(@PathVariable("id") int ITID, HttpSession session, Model model) {
        if (session.getAttribute("AID") == null) return "redirect:/login";

        List<ItineraryDay> days = itineraryService.getDays(ITID);
        model.addAttribute("itineraryId", ITID);
        model.addAttribute("days", days);
        model.addAttribute("poiList", poiService.listForAgency((Integer) session.getAttribute("AID")));
        return "itinerary/board";
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
