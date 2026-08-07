package com.example.UsefulTravel.controller;

import com.example.UsefulTravel.DAO.ItineraryDAO;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AgencyController {

    private final ItineraryDAO itineraryDAO;

    @Autowired
    public AgencyController(ItineraryDAO itineraryDAO) {
        this.itineraryDAO = itineraryDAO;
    }

    @GetMapping("/agency/dashboard")
    public String dashboard(HttpSession session, Model model) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return "redirect:/login";

        model.addAttribute("name", session.getAttribute("name"));
        model.addAttribute("itineraries", itineraryDAO.findByAgency(AID));
        return "agency/dashboard";
    }
}
