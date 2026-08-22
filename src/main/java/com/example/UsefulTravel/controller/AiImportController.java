package com.example.UsefulTravel.controller;

import com.example.UsefulTravel.entity.AiImport;
import com.example.UsefulTravel.entity.AiParsedDay;
import com.example.UsefulTravel.entity.Itinerary;
import com.example.UsefulTravel.service.AiParseService;
import com.example.UsefulTravel.service.DocumentExtractionService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/ai-import")
public class AiImportController {

    private final AiParseService aiParseService;
    private final DocumentExtractionService documentExtractionService;

    @Autowired
    public AiImportController(AiParseService aiParseService, DocumentExtractionService documentExtractionService) {
        this.aiParseService = aiParseService;
        this.documentExtractionService = documentExtractionService;
    }

    // 檔案超過 application.properties 設定的大小上限時, 顯示友善訊息而不是讓連線直接斷掉
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleFileTooLarge(Model model) {
        model.addAttribute("uploadError", "檔案太大了，目前上限是 50MB，請壓縮檔案或分批處理後再上傳。");
        return "ai-import/new";
    }

    // GET /ai-import/new → 貼上文字的表單
    @GetMapping("/new")
    public String newForm(HttpSession session) {
        if (session.getAttribute("AID") == null) return "redirect:/login";
        return "ai-import/new";
    }

    // POST /ai-import/new → 呼叫 Claude 解析, 完成後導去 review 頁
    @PostMapping("/new")
    public String parse(@RequestParam String rawText,
                        @RequestParam(defaultValue = "default") String templateStyle,
                        @RequestParam(required = false) List<String> depAirport,
                        @RequestParam(required = false) List<String> depTime,
                        @RequestParam(required = false) List<String> arrAirport,
                        @RequestParam(required = false) List<String> arrTime,
                        @RequestParam(required = false) String tripDescription,
                        HttpSession session) {
        Integer AID = (Integer) session.getAttribute("AID");
        Integer UID = (Integer) session.getAttribute("UID");
        if (AID == null || UID == null) return "redirect:/login";

        String extraContext = buildExtraContext(depAirport, depTime, arrAirport, arrTime, tripDescription);
        AiImport result = aiParseService.parseText(AID, UID, rawText, "text", templateStyle, extraContext);
        return "redirect:/ai-import/" + result.getIPID() + "/review";
    }

    // POST /ai-import/upload → 上傳 PDF/Word, 抽出文字後跟貼上文字走同一套 AI 解析流程
    @PostMapping("/upload")
    public String uploadAndParse(@RequestParam("file") MultipartFile file,
                                 @RequestParam(defaultValue = "default") String templateStyle,
                                 @RequestParam(required = false) List<String> depAirport,
                                 @RequestParam(required = false) List<String> depTime,
                                 @RequestParam(required = false) List<String> arrAirport,
                                 @RequestParam(required = false) List<String> arrTime,
                                 @RequestParam(required = false) String tripDescription,
                                 HttpSession session, Model model) {
        Integer AID = (Integer) session.getAttribute("AID");
        Integer UID = (Integer) session.getAttribute("UID");
        if (AID == null || UID == null) return "redirect:/login";

        if (file.isEmpty()) {
            model.addAttribute("uploadError", "請先選擇一個 PDF 或 Word 檔案");
            return "ai-import/new";
        }

        String extraContext = buildExtraContext(depAirport, depTime, arrAirport, arrTime, tripDescription);
        try {
            DocumentExtractionService.ExtractResult extracted = documentExtractionService.extract(file);
            AiImport result = aiParseService.parseText(AID, UID, extracted.text, extracted.sourceType, templateStyle, extraContext);
            return "redirect:/ai-import/" + result.getIPID() + "/review";
        } catch (Exception e) {
            model.addAttribute("uploadError",
                    "檔案處理失敗：" + (e.getMessage() != null ? e.getMessage() : e.toString()));
            return "ai-import/new";
        }
    }

    // 把「出發/抵達機場+時間」(可能有多段, 例如轉機) 跟「行程說明」組成一段格式化文字,
    // 存進 ai_import.extra_context 並當額外 context 送給 AI 解析。四個 List 長度可能不一致
    // (使用者可能只填了某幾段的某幾個欄位), 用最長的當迴圈長度, 缺的欄位就跳過。
    // 整段都沒填 (使用者沒有用到這個選填功能) 就回傳 null。
    private String buildExtraContext(List<String> depAirport, List<String> depTime,
                                     List<String> arrAirport, List<String> arrTime,
                                     String tripDescription) {
        int legCount = Math.max(
                Math.max(size(depAirport), size(depTime)),
                Math.max(size(arrAirport), size(arrTime)));

        StringBuilder sb = new StringBuilder();
        int legNo = 0;
        for (int i = 0; i < legCount; i++) {
            String dep = get(depAirport, i);
            String depT = get(depTime, i);
            String arr = get(arrAirport, i);
            String arrT = get(arrTime, i);
            if (isBlank(dep) && isBlank(depT) && isBlank(arr) && isBlank(arrT)) continue;

            legNo++;
            sb.append("航段").append(legNo).append("：");
            if (!isBlank(dep)) sb.append("出發機場=").append(dep.trim()).append(" ");
            if (!isBlank(depT)) sb.append("出發時間=").append(depT.trim()).append(" ");
            if (!isBlank(arr)) sb.append("抵達機場=").append(arr.trim()).append(" ");
            if (!isBlank(arrT)) sb.append("抵達時間=").append(arrT.trim()).append(" ");
            sb.append("\n");
        }

        if (!isBlank(tripDescription)) {
            sb.append("行程說明：").append(tripDescription.trim()).append("\n");
        }

        String result = sb.toString().trim();
        return result.isEmpty() ? null : result;
    }

    private int size(List<String> list) { return list == null ? 0 : list.size(); }

    private String get(List<String> list, int i) {
        return (list != null && i < list.size()) ? list.get(i) : null;
    }

    private boolean isBlank(String s) { return s == null || s.isBlank(); }

    // POST /ai-import/item/{apiid}/edit → 編輯 AI 解析出來、還沒確認的項目
    @PostMapping("/item/{apiid}/edit")
    public String editItem(@PathVariable("apiid") int APIID,
                           @RequestParam String name,
                           @RequestParam String itemType,
                           @RequestParam(required = false) String timeSlot,
                           @RequestParam(required = false) String note,
                           @RequestParam(required = false) Integer stayMinutes,
                           HttpSession session) {
        if (session.getAttribute("AID") == null) return "redirect:/login";

        int IPID = aiParseService.getIpidByItem(APIID);
        aiParseService.updateParsedItem(APIID, name, itemType, timeSlot, note, stayMinutes);
        return "redirect:/ai-import/" + IPID + "/review";
    }

    // POST /ai-import/item/{apiid}/add-to-poi → 把 AI 解析出的單一項目寫進公司 POI 資料庫
    @PostMapping("/item/{apiid}/add-to-poi")
    public String addToPoi(@PathVariable("apiid") int APIID, HttpSession session, Model model) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return "redirect:/login";

        int IPID = aiParseService.getIpidByItem(APIID);
        try {
            aiParseService.addItemToPoi(AID, APIID);
        } catch (Exception e) {
            // 加入失敗 (例如類型不支援、已經比對過) 就靜默導回, review 頁面上狀態不會變
        }
        return "redirect:/ai-import/" + IPID + "/review";
    }

    // GET /ai-import/{id}/review → 顯示 AI 拆解出來的景點卡片讓線控確認
    @GetMapping("/{id}/review")
    public String review(@PathVariable("id") int IPID, HttpSession session, Model model) {
        if (session.getAttribute("AID") == null) return "redirect:/login";

        AiImport aiImport = aiParseService.findById(IPID);
        List<AiParsedDay> days = aiParseService.getDays(IPID);

        // 把每天的卡片一起準備好給頁面用 (day -> items)
        Map<AiParsedDay, Object> dayItemsMap = new LinkedHashMap<>();
        for (AiParsedDay day : days) {
            dayItemsMap.put(day, aiParseService.getItems(day.getAPDID()));
        }

        model.addAttribute("aiImport", aiImport);
        model.addAttribute("days", days);
        model.addAttribute("dayItemsMap", dayItemsMap);
        return "ai-import/review";
    }

    // POST /ai-import/{id}/confirm → 確認無誤, 轉成正式行程並導去排版看板
    @PostMapping("/{id}/confirm")
    public String confirm(@PathVariable("id") int IPID,
                          @RequestParam String title,
                          @RequestParam String country,
                          @RequestParam(required = false) String region,
                          HttpSession session) {
        if (session.getAttribute("AID") == null) return "redirect:/login";

        Itinerary itinerary = aiParseService.confirmImport(IPID, title, country, region);
        return "redirect:/itinerary/" + itinerary.getITID() + "/board";
    }
}