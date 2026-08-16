package com.example.UsefulTravel.controller;

import com.example.UsefulTravel.DAO.ImageAssetDAO;
import com.example.UsefulTravel.DAO.ItineraryDAO;
import com.example.UsefulTravel.entity.ImageAsset;
import com.example.UsefulTravel.entity.Itinerary;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Controller
public class AgencyController {

    private final ItineraryDAO itineraryDAO;
    private final ImageAssetDAO imageAssetDAO;

    @Autowired
    public AgencyController(ItineraryDAO itineraryDAO, ImageAssetDAO imageAssetDAO) {
        this.itineraryDAO = itineraryDAO;
        this.imageAssetDAO = imageAssetDAO;
    }

    @GetMapping("/agency/dashboard")
    public String dashboard(HttpSession session, Model model) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return "redirect:/login";

        List<Itinerary> itineraries = itineraryDAO.findByAgency(AID);
        model.addAttribute("name", session.getAttribute("name"));
        model.addAttribute("itineraries", itineraries);

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
}
