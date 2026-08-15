package com.example.UsefulTravel.controller;

import com.example.UsefulTravel.DAO.AgencyExportTemplateDAO;
import com.example.UsefulTravel.entity.AgencyExportTemplate;
import com.example.UsefulTravel.service.ImageStorageService; // 直接重用圖片的儲存抽象層, 存放邏輯一模一樣
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
 * 每個旅行社(帳號)上傳/管理自己的行程輸出範本 (.docx)
 * 範本製作規格見 template-placeholder-spec.md
 */
@Controller
@RequestMapping("/export-templates")
public class AgencyExportTemplateController {

    private final AgencyExportTemplateDAO templateDAO;
    private final ImageStorageService storageService;

    @Autowired
    public AgencyExportTemplateController(AgencyExportTemplateDAO templateDAO, ImageStorageService storageService) {
        this.templateDAO = templateDAO;
        this.storageService = storageService;
    }

    // GET /export-templates → 瀏覽器打開的範本管理畫面 (上傳/設預設/刪除)
    @GetMapping
    public String page(HttpSession session, Model model) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return "redirect:/login";
        model.addAttribute("templates", templateDAO.findByAgency(AID));
        model.addAttribute("name", session.getAttribute("name"));
        return "export-template/list";
    }

    // GET /export-templates/api → 給匯出彈窗的下拉選單用 AJAX 抓, 回傳 JSON
    @GetMapping("/api")
    @ResponseBody
    public ResponseEntity<List<AgencyExportTemplate>> listApi(HttpSession session) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(templateDAO.findByAgency(AID));
    }

    // POST /export-templates/upload → 上傳一份 .docx 範本
    @PostMapping("/upload")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                                        @RequestParam String name,
                                                        @RequestParam(defaultValue = "false") boolean setDefault,
                                                        HttpSession session) {
        Integer AID = (Integer) session.getAttribute("AID");
        Integer UID = (Integer) session.getAttribute("UID");
        if (AID == null) return ResponseEntity.status(401).build();

        if (file.isEmpty() || !"application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                .equals(file.getContentType())) {
            return ResponseEntity.badRequest().body(Map.of("error", "請上傳 .docx 檔"));
        }

        try {
            String storageKey = storageService.store(file.getBytes(), file.getOriginalFilename());

            AgencyExportTemplate template = new AgencyExportTemplate(AID, name, storageKey, UID);
            templateDAO.save(template);

            if (setDefault) {
                templateDAO.clearDefault(AID);
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
    @PostMapping("/{AETID}/set-default")
    @ResponseBody
    public ResponseEntity<Void> setDefault(@PathVariable int AETID, HttpSession session) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return ResponseEntity.status(401).build();

        AgencyExportTemplate template = templateDAO.findById(AETID);
        if (template == null || template.getAID() != AID) return ResponseEntity.status(404).build();

        templateDAO.clearDefault(AID);
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

        AgencyExportTemplate template = templateDAO.findById(AETID);
        if (template == null || template.getAID() != AID) return ResponseEntity.status(404).build();

        storageService.delete(template.getFilePath());
        templateDAO.delete(AETID);
        return ResponseEntity.ok().build();
    }
}
