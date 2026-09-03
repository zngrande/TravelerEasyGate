package com.example.travelereasygate.controller;

import com.example.travelereasygate.DAO.AgencyExportTemplateDAO;
import com.example.travelereasygate.entity.AgencyExportTemplate;
import com.example.travelereasygate.service.ImageStorageService; // 直接重用圖片的儲存抽象層, 存放邏輯一模一樣
import com.example.travelereasygate.service.PermissionService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 每個旅行社(帳號)上傳/管理自己的行程輸出範本。
 * 分三種類型 (AgencyExportTemplate.templateType):
 *   CUSTOMER     → 給客戶看的企劃書範本 (.docx), 對應行程排版看板「匯出客戶版 (Word)」, 範本製作規格見 template-placeholder-spec.md
 *   AGENCY_WORD  → 給同業(B2B 夥伴)看的企劃書範本 (.docx), 對應行程排版看板「匯出同業版 (Word)」
 *   AGENCY       → 給同業(B2B 夥伴)看的報價單範本 (.xlsx), 對應報價單頁面「匯出 Excel」
 * 三種範本各自可以有自己的「預設範本」, 互不影響。
 *
 * 權限: 範本「管理」(上傳/設預設/刪除, 含這個管理畫面本身) 是全公司共用的設定, 只開放給 ADMIN,
 * 對應側邊欄「行程輸出範本」的顯示條件。但是 listApi() 這支「查詢有哪些範本可選」的唯讀 API 不受這個限制——
 * 它是行程排版看板「匯出客戶版/同業版 (Word)」彈窗載入範本下拉選單在用的, EDITOR 平常就會用到匯出功能,
 * 如果連這支都鎖 ADMIN, EDITOR 匯出時範本下拉選單就會直接讀不到、只能用系統內建版面, 反而擋到原本就有的功能。
 */
@Controller
@RequestMapping("/export-templates")
public class AgencyExportTemplateController {

    private static final String TYPE_CUSTOMER = "CUSTOMER";
    private static final String TYPE_AGENCY_WORD = "AGENCY_WORD";
    private static final String TYPE_AGENCY = "AGENCY";

    private static final String DOCX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final AgencyExportTemplateDAO templateDAO;
    private final ImageStorageService storageService;
    private final PermissionService permissionService;

    @Autowired
    public AgencyExportTemplateController(AgencyExportTemplateDAO templateDAO, ImageStorageService storageService,
                                           PermissionService permissionService) {
        this.templateDAO = templateDAO;
        this.storageService = storageService;
        this.permissionService = permissionService;
    }

    private boolean isAdmin(HttpSession session) {
        return permissionService.canManageStaff((String) session.getAttribute("role"));
    }

    private String normalizeType(String type) {
        if (TYPE_AGENCY.equalsIgnoreCase(type)) return TYPE_AGENCY;
        if (TYPE_AGENCY_WORD.equalsIgnoreCase(type)) return TYPE_AGENCY_WORD;
        return TYPE_CUSTOMER;
    }

    private boolean isExcelType(String normalizedType) {
        return TYPE_AGENCY.equals(normalizedType);
    }

    // GET /export-templates → 瀏覽器打開的範本管理畫面 (上傳/設預設/刪除), 只有 ADMIN 能進來,
    // 一次把三種類型的清單都準備好給前端分別顯示
    @GetMapping
    public String page(HttpSession session, Model model) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return "redirect:/login";
        if (!isAdmin(session)) return "redirect:/agency/dashboard";
        model.addAttribute("customerTemplates", templateDAO.findByAgencyAndType(AID, TYPE_CUSTOMER));
        model.addAttribute("agencyWordTemplates", templateDAO.findByAgencyAndType(AID, TYPE_AGENCY_WORD));
        model.addAttribute("agencyTemplates", templateDAO.findByAgencyAndType(AID, TYPE_AGENCY));
        model.addAttribute("name", session.getAttribute("name"));
        return "export-template/list";
    }

    // GET /export-templates/api → 給匯出彈窗的下拉選單用 AJAX 抓, 回傳 JSON
    // type 不傳的話預設抓 CUSTOMER (原本行程企劃書匯出彈窗只認得 Word 範本)
    // 刻意不擋 ADMIN-only (見上面 class 註解), 只要求有登入即可查詢
    @GetMapping("/api")
    @ResponseBody
    public ResponseEntity<List<AgencyExportTemplate>> listApi(@RequestParam(required = false) String type,
                                                                HttpSession session) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(templateDAO.findByAgencyAndType(AID, normalizeType(type)));
    }

    // POST /export-templates/upload → 上傳一份範本
    // type=CUSTOMER (預設) / AGENCY_WORD 要求 .docx；type=AGENCY 要求 .xlsx
    @PostMapping("/upload")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                                        @RequestParam String name,
                                                        @RequestParam(defaultValue = TYPE_CUSTOMER) String type,
                                                        @RequestParam(defaultValue = "false") boolean setDefault,
                                                        HttpSession session) {
        Integer AID = (Integer) session.getAttribute("AID");
        Integer UID = (Integer) session.getAttribute("UID");
        if (AID == null) return ResponseEntity.status(401).build();
        if (!isAdmin(session)) return ResponseEntity.status(403).body(Map.of("error", "只有管理者能上傳範本"));

        String normalizedType = normalizeType(type);
        boolean excel = isExcelType(normalizedType);
        String expectedContentType = excel ? XLSX_CONTENT_TYPE : DOCX_CONTENT_TYPE;
        String expectedExt = excel ? ".xlsx" : ".docx";

        boolean contentTypeOk = expectedContentType.equals(file.getContentType());
        boolean extOk = file.getOriginalFilename() != null
                && file.getOriginalFilename().toLowerCase().endsWith(expectedExt);

        if (file.isEmpty() || !(contentTypeOk || extOk)) {
            return ResponseEntity.badRequest().body(Map.of("error", "請上傳 " + expectedExt + " 檔"));
        }

        try {
            String storageKey = storageService.store(file.getBytes(), file.getOriginalFilename());

            AgencyExportTemplate template = new AgencyExportTemplate(AID, name, normalizedType, storageKey, UID);
            templateDAO.save(template);

            if (setDefault) {
                templateDAO.clearDefault(AID, normalizedType);
                template.setDefault(true);
                templateDAO.save(template);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("AETID", template.getAETID());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage() != null ? e.getMessage() : e.toString()));
        }
    }

    // POST /export-templates/{AETID}/set-default → 設定這家旅行社匯出時預設用哪份範本
    // (同類型底下互斥, 三種版型的預設各自獨立, 不會互相影響)
    @PostMapping("/{AETID}/set-default")
    @ResponseBody
    public ResponseEntity<Void> setDefault(@PathVariable int AETID, HttpSession session) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return ResponseEntity.status(401).build();
        if (!isAdmin(session)) return ResponseEntity.status(403).build();

        AgencyExportTemplate template = templateDAO.findById(AETID);
        if (template == null || template.getAID() != AID) return ResponseEntity.status(404).build();

        templateDAO.clearDefault(AID, normalizeType(template.getTemplateType()));
        template.setDefault(true);
        templateDAO.save(template);
        return ResponseEntity.ok().build();
    }

    // DELETE /export-templates/{AETID}
    @DeleteMapping("/{AETID}")
    @ResponseBody
    public ResponseEntity<Void> delete(@PathVariable int AETID, HttpSession session) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return ResponseEntity.status(401).build();
        if (!isAdmin(session)) return ResponseEntity.status(403).build();

        AgencyExportTemplate template = templateDAO.findById(AETID);
        if (template == null || template.getAID() != AID) return ResponseEntity.status(404).build();

        storageService.delete(template.getFilePath());
        templateDAO.delete(AETID);
        return ResponseEntity.ok().build();
    }
}
