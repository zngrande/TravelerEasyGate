package com.example.travelereasygate.controller;

import com.example.travelereasygate.DAO.AgencyExportTemplateDAO;
import com.example.travelereasygate.DAO.ItineraryDAO;
import com.example.travelereasygate.entity.AgencyExportTemplate;
import com.example.travelereasygate.entity.Itinerary;
import com.example.travelereasygate.entity.Quotation;
import com.example.travelereasygate.service.ExcelTemplateMergeService;
import com.example.travelereasygate.service.ExportService;
import com.example.travelereasygate.service.ImageStorageService;
import com.example.travelereasygate.service.QuotationExportService;
import com.example.travelereasygate.service.QuotationService;
import com.example.travelereasygate.service.TemplateMergeService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(ExportController.class);

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
        byte[] customTemplateBytes = null;
        if (template != null) {
            try {
                customTemplateBytes = storageService.load(template.getFilePath());
            } catch (Exception e) {
                // 使用者反映「行程匯出 WORD 都會報錯」——追查後發現根因: 自訂範本檔案是存在容器本機硬碟
                // (見 LocalImageStorageService 說明), Railway 每次重新部署都會換一個全新的容器、本機硬碟
                // 內容會整個歸零, 但資料庫裡這筆 AgencyExportTemplate 紀錄的檔案路徑還在, 一部署完就變成
                // 「資料庫說有這個範本, 但硬碟上這個檔案已經不存在」, storageService.load() 丟出
                // NoSuchFileException, 而這整支 export() 方法沒有任何防護, 直接讓整個 request 被
                // GlobalExceptionHandler 攔截、顯示系統發生錯誤。修正: 找不到自訂範本檔案時不要整個匯出
                // 失敗, 改為退回系統內建版型 (跟這個旅行社完全沒設定自訂範本時的行為一致), 讓使用者至少能
                // 正常匯出企劃書 (只是版面會變回內建樣式), 同時留一筆 log 提醒之後需要重新上傳自訂範本。
                LOGGER.warn("自訂匯出範本檔案遺失 (可能是容器重新部署清空本機硬碟), 已退回系統內建版型 (templateId={}, filePath={}): {}",
                        template.getAETID(), template.getFilePath(), e.toString());
                template = null;
            }
        }

        if (template != null) {
            Itinerary itinerary = itineraryDAO.findById(ITID);
            if (itinerary == null) return ResponseEntity.notFound().build();

            TemplateMergeService.TemplateData data = templateMergeService.buildTemplateData(itinerary, includeImages, includeRoutes, includeMap);
            fileBytes = templateMergeService.merge(customTemplateBytes, data);
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
        byte[] customTemplateBytes = null;
        if (agencyTemplate != null) {
            try {
                customTemplateBytes = storageService.load(agencyTemplate.getFilePath());
            } catch (Exception e) {
                // 同上 (export() 方法裡的說明): 自訂範本檔案存在容器本機硬碟, 重新部署後會遺失, 資料庫紀錄
                // 卻還在——找不到檔案就退回系統內建版型, 不要讓報價單 Excel 匯出整個失敗。
                LOGGER.warn("自訂報價單範本檔案遺失 (可能是容器重新部署清空本機硬碟), 已退回系統內建版型 (templateId={}, filePath={}): {}",
                        agencyTemplate.getAETID(), agencyTemplate.getFilePath(), e.toString());
                agencyTemplate = null;
            }
        }

        if (agencyTemplate != null) {
            Itinerary itinerary = itineraryDAO.findById(quotation.getITID());
            ExcelTemplateMergeService.ExcelTemplateData data =
                    excelTemplateMergeService.buildTemplateData(quotation, itinerary);
            fileBytes = excelTemplateMergeService.merge(customTemplateBytes, data);
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