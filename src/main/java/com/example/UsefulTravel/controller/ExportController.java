package com.example.UsefulTravel.controller;

import com.example.UsefulTravel.service.ExportService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;

@Controller
public class ExportController {

    private final ExportService exportService;

    @Autowired
    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    // GET /itinerary/{id}/export?format=b2b|b2c → 產生並直接下載 Word 企劃書
    @GetMapping("/itinerary/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable("id") int ITID,
                                          @RequestParam(defaultValue = "b2c") String format,
                                          @RequestParam(defaultValue = "true") boolean includeItinerary,
                                          @RequestParam(defaultValue = "true") boolean includeRoutes,
                                          @RequestParam(defaultValue = "true") boolean includeMap,
                                          @RequestParam(defaultValue = "false") boolean includeImages,
                                          HttpSession session) throws Exception {
        Integer UID = (Integer) session.getAttribute("UID");
        if (UID == null) {
            return ResponseEntity.status(401).build();
        }

        ExportService.ExportOptions options = new ExportService.ExportOptions();
        options.includeItinerary = includeItinerary;
        options.includeRoutes = includeRoutes;
        options.includeMap = includeMap;
        options.includeImages = includeImages;

        byte[] fileBytes = exportService.generateWordDocument(ITID, format, UID, options);

        String filename = "itinerary_" + ITID + "_" + format + ".docx";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(
                ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build());

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(fileBytes);
    }
}
