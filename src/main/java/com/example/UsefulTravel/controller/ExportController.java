package com.example.UsefulTravel.controller;

import com.example.UsefulTravel.DAO.AgencyExportTemplateDAO;
import com.example.UsefulTravel.DAO.ItineraryDAO;
import com.example.UsefulTravel.entity.AgencyExportTemplate;
import com.example.UsefulTravel.entity.Itinerary;
import com.example.UsefulTravel.service.ExportService;
import com.example.UsefulTravel.service.ImageStorageService;
import com.example.UsefulTravel.service.TemplateMergeService;
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
    private final TemplateMergeService templateMergeService;
    private final AgencyExportTemplateDAO templateDAO;
    private final ItineraryDAO itineraryDAO;
    private final ImageStorageService storageService;

    @Autowired
    public ExportController(ExportService exportService, TemplateMergeService templateMergeService,
                             AgencyExportTemplateDAO templateDAO, ItineraryDAO itineraryDAO,
                             ImageStorageService storageService) {
        this.exportService = exportService;
        this.templateMergeService = templateMergeService;
        this.templateDAO = templateDAO;
        this.itineraryDAO = itineraryDAO;
        this.storageService = storageService;
    }

    // GET /itinerary/{id}/export?format=b2b|b2c → 產生並直接下載 Word 企劃書
    // 多加的 templateId 參數: 不傳的話走原本內建的四種模板風格;
    // 傳了就改用該旅行社自己上傳的 .docx 範本做合併 (templateId = 0 代表用該旅行社設的預設範本)
    @GetMapping("/itinerary/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable("id") int ITID,
                                          @RequestParam(defaultValue = "b2c") String format,
                                          @RequestParam(defaultValue = "true") boolean includeItinerary,
                                          @RequestParam(defaultValue = "true") boolean includeRoutes,
                                          @RequestParam(defaultValue = "true") boolean includeMap,
                                          @RequestParam(defaultValue = "false") boolean includeImages,
                                          @RequestParam(required = false) Integer templateId,
                                          HttpSession session) throws Exception {
        Integer UID = (Integer) session.getAttribute("UID");
        Integer AID = (Integer) session.getAttribute("AID");
        if (UID == null) {
            return ResponseEntity.status(401).build();
        }

        byte[] fileBytes;

        AgencyExportTemplate template = resolveTemplate(templateId, AID);
        if (template != null) {
            Itinerary itinerary = itineraryDAO.findById(ITID);
            if (itinerary == null) return ResponseEntity.notFound().build();

            byte[] templateBytes = storageService.load(template.getFilePath());
            TemplateMergeService.TemplateData data = templateMergeService.buildTemplateData(itinerary, includeImages);
            fileBytes = templateMergeService.merge(templateBytes, data);
        } else {
            ExportService.ExportOptions options = new ExportService.ExportOptions();
            options.includeItinerary = includeItinerary;
            options.includeRoutes = includeRoutes;
            options.includeMap = includeMap;
            options.includeImages = includeImages;
            fileBytes = exportService.generateWordDocument(ITID, format, UID, options);
        }

        String filename = "itinerary_" + ITID + "_" + format + ".docx";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(
                ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build());

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(fileBytes);
    }

    // templateId 沒傳 → null (用內建版面); templateId = 0 → 用該旅行社的預設範本; 其他 → 用指定的那份範本
    private AgencyExportTemplate resolveTemplate(Integer templateId, Integer AID) {
        if (templateId == null || AID == null) return null;
        if (templateId == 0) return templateDAO.findDefaultByAgency(AID);
        AgencyExportTemplate t = templateDAO.findById(templateId);
        return (t != null && t.getAID() == AID) ? t : null;
    }
}
