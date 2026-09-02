package com.example.UsefulTravel.controller;

import com.example.UsefulTravel.DAO.AgencyExportTemplateDAO;
import com.example.UsefulTravel.DAO.ItineraryDAO;
import com.example.UsefulTravel.entity.AgencyExportTemplate;
import com.example.UsefulTravel.entity.Itinerary;
import com.example.UsefulTravel.entity.Quotation;
import com.example.UsefulTravel.service.ExcelTemplateMergeService;
import com.example.UsefulTravel.service.ExportService;
import com.example.UsefulTravel.service.ImageStorageService;
import com.example.UsefulTravel.service.QuotationExportService;
import com.example.UsefulTravel.service.QuotationService;
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
    private final ExcelTemplateMergeService excelTemplateMergeService;
    private final AgencyExportTemplateDAO templateDAO;
    private final ItineraryDAO itineraryDAO;
    private final ImageStorageService storageService;
    private final QuotationExportService quotationExportService;
    private final QuotationService quotationService;

    @Autowired
    public ExportController(ExportService exportService, TemplateMergeService templateMergeService,
                            ExcelTemplateMergeService excelTemplateMergeService,
                            AgencyExportTemplateDAO templateDAO, ItineraryDAO itineraryDAO,
                            ImageStorageService storageService, QuotationExportService quotationExportService,
                            QuotationService quotationService) {
        this.exportService = exportService;
        this.templateMergeService = templateMergeService;
        this.excelTemplateMergeService = excelTemplateMergeService;
        this.templateDAO = templateDAO;
        this.itineraryDAO = itineraryDAO;
        this.storageService = storageService;
        this.quotationExportService = quotationExportService;
        this.quotationService = quotationService;
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

        AgencyExportTemplate template = resolveTemplate(templateId, AID, format);
        if (template != null) {
            Itinerary itinerary = itineraryDAO.findById(ITID);
            if (itinerary == null) return ResponseEntity.notFound().build();

            byte[] templateBytes = storageService.load(template.getFilePath());
            TemplateMergeService.TemplateData data = templateMergeService.buildTemplateData(itinerary, includeImages, includeRoutes, includeMap);
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

    // templateId 沒傳 → null (用內建版面); templateId = 0 → 用該旅行社「這個 format 對應類型」的預設範本; 其他 → 用指定的那份範本
    // format=b2c → 找 CUSTOMER 類型範本 (給客戶看的企劃書); format=b2b → 找 AGENCY_WORD 類型範本 (給同業看的企劃書)
    // 同業 Excel 報價單是完全不同的東西, 走 resolveAgencyTemplate / 另一支 API
    private AgencyExportTemplate resolveTemplate(Integer templateId, Integer AID, String format) {
        if (templateId == null || AID == null) return null;
        String type = "b2b".equals(format) ? "AGENCY_WORD" : "CUSTOMER";
        if (templateId == 0) return templateDAO.findDefaultByAgencyAndType(AID, type);
        AgencyExportTemplate t = templateDAO.findById(templateId);
        return (t != null && t.getAID() == AID && type.equals(t.getTemplateType())) ? t : null;
    }

    // templateId 沒傳 → null (用系統內建固定版型); templateId = 0 → 用該旅行社的預設「同業版型」範本; 其他 → 指定的那份
    private AgencyExportTemplate resolveAgencyTemplate(Integer templateId, Integer AID) {
        if (templateId == null || AID == null) return null;
        if (templateId == 0) return templateDAO.findDefaultByAgencyAndType(AID, "AGENCY");
        AgencyExportTemplate t = templateDAO.findById(templateId);
        return (t != null && t.getAID() == AID && "AGENCY".equals(t.getTemplateType())) ? t : null;
    }

    // GET /quotation/{qid}/export/excel → 把這份報價單匯出成 Excel
    // templateId 沒傳/找不到範本 → 走系統內建固定版型 (原本的行為); 傳了且有對應的「同業版型」自訂範本 →
    // 讀取那份 .xlsx 範本做合併 (ExcelTemplateMergeService), 版面完全照旅行社自己設計的長相輸出
    @GetMapping("/quotation/{qid}/export/excel")
    public ResponseEntity<byte[]> exportQuotationExcel(@PathVariable("qid") int QID,
                                                        @RequestParam(required = false) Integer templateId,
                                                        HttpSession session) throws Exception {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return ResponseEntity.status(401).build();

        Quotation quotation = quotationService.findById(QID);
        if (quotation == null || quotation.getAID() != AID) return ResponseEntity.notFound().build();

        // 草稿階段金額還沒凍結, 隨時可能再變, 不開放匯出以免業務把還會變動的數字寄給客戶
        if ("draft".equals(quotation.getStatus())) return ResponseEntity.status(409).body(null);

        byte[] fileBytes;
        AgencyExportTemplate agencyTemplate = resolveAgencyTemplate(templateId, AID);
        if (agencyTemplate != null) {
            Itinerary itinerary = itineraryDAO.findById(quotation.getITID());
            byte[] templateBytes = storageService.load(agencyTemplate.getFilePath());
            ExcelTemplateMergeService.ExcelTemplateData data =
                    excelTemplateMergeService.buildTemplateData(quotation, itinerary);
            fileBytes = excelTemplateMergeService.merge(templateBytes, data);
        } else {
            fileBytes = quotationExportService.generateExcel(QID);
        }

        String filename = "quotation_" + QID + "_v" + quotation.getVersion() + ".xlsx";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(
                ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build());

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(fileBytes);
    }
}