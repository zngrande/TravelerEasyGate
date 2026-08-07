package com.example.UsefulTravel.controller;

import com.example.UsefulTravel.DAO.ItineraryComponentDAO;
import com.example.UsefulTravel.DAO.TravelComponentDAO;
import com.example.UsefulTravel.entity.ItineraryComponent;
import com.example.UsefulTravel.entity.TravelComponent;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@Controller
public class TravelComponentController {

    private final TravelComponentDAO travelComponentDAO;
    private final ItineraryComponentDAO itineraryComponentDAO;

    @Autowired
    public TravelComponentController(TravelComponentDAO travelComponentDAO, ItineraryComponentDAO itineraryComponentDAO) {
        this.travelComponentDAO = travelComponentDAO;
        this.itineraryComponentDAO = itineraryComponentDAO;
    }

    // GET /component → 公司元件庫列表 (航班/餐食/住宿/自費項目)
    @GetMapping("/component")
    public String list(HttpSession session, Model model) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return "redirect:/login";

        model.addAttribute("components", travelComponentDAO.findByAgency(AID));
        return "component/list";
    }

    // POST /component/new → 新增一筆元件
    @PostMapping("/component/new")
    public String create(@RequestParam String type,
                          @RequestParam String name,
                          @RequestParam(required = false) BigDecimal defaultPrice,
                          @RequestParam(required = false) String description,
                          HttpSession session) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return "redirect:/login";

        travelComponentDAO.save(new TravelComponent(AID, type, name, defaultPrice, description));
        return "redirect:/component";
    }

    // GET /itinerary/{id}/components → 這個行程目前掛的報價元件
    @GetMapping("/itinerary/{id}/components")
    public String itineraryComponents(@PathVariable("id") int ITID, HttpSession session, Model model) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return "redirect:/login";

        model.addAttribute("itineraryId", ITID);
        model.addAttribute("attached", itineraryComponentDAO.findByItinerary(ITID));
        model.addAttribute("allComponents", travelComponentDAO.findByAgency(AID));
        return "component/itinerary-components";
    }

    // POST /itinerary/{id}/components → 把元件掛到這個行程 (用於 B2B 報價表)
    @PostMapping("/itinerary/{id}/components")
    public String attach(@PathVariable("id") int ITID,
                          @RequestParam int componentId,
                          @RequestParam(defaultValue = "1") int quantity,
                          @RequestParam(required = false) BigDecimal priceOverride) {
        itineraryComponentDAO.save(new ItineraryComponent(ITID, componentId, null, quantity, priceOverride));
        return "redirect:/itinerary/" + ITID + "/components";
    }
}
