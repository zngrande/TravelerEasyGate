package com.example.travelereasygate.controller;

import com.example.travelereasygate.entity.AiImport;
import com.example.travelereasygate.entity.AiParsedDay;
import com.example.travelereasygate.entity.Itinerary;
import com.example.travelereasygate.service.AiParseService;
import com.example.travelereasygate.service.DocumentExtractionService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/ai-import")
public class AiImportController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AiImportController.class);

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
                        HttpSession session) {
        Integer AID = (Integer) session.getAttribute("AID");
        Integer UID = (Integer) session.getAttribute("UID");
        if (AID == null || UID == null) return "redirect:/login";

        AiImport result = aiParseService.parseText(AID, UID, rawText, "text", templateStyle);
        return "redirect:/ai-import/" + result.getIPID() + "/review";
    }

    // POST /ai-import/upload → 上傳 PDF/Word, 抽出文字後跟貼上文字走同一套 AI 解析流程
    @PostMapping("/upload")
    public String uploadAndParse(@RequestParam("file") MultipartFile file,
                                 @RequestParam(defaultValue = "default") String templateStyle,
                                 HttpSession session, Model model) {
        Integer AID = (Integer) session.getAttribute("AID");
        Integer UID = (Integer) session.getAttribute("UID");
        if (AID == null || UID == null) return "redirect:/login";

        if (file.isEmpty()) {
            model.addAttribute("uploadError", "請先選擇一個 PDF 或 Word 檔案");
            return "ai-import/new";
        }

        try {
            DocumentExtractionService.ExtractResult extracted = documentExtractionService.extract(file);
            AiImport result = aiParseService.parseText(AID, UID, extracted.text, extracted.sourceType, templateStyle);
            return "redirect:/ai-import/" + result.getIPID() + "/review";
        } catch (Exception e) {
            model.addAttribute("uploadError",
                    "檔案處理失敗：" + (e.getMessage() != null ? e.getMessage() : e.toString()));
            return "ai-import/new";
        }
    }

    // POST /ai-import/item/{apiid}/edit → 編輯 AI 解析出來、還沒確認的項目
    @PostMapping("/item/{apiid}/edit")
    public String editItem(@PathVariable("apiid") int APIID,
                           @RequestParam String name,
                           @RequestParam String itemType,
                           @RequestParam(required = false) String timeSlot,
                           @RequestParam(required = false) String note,
                           @RequestParam(required = false) Integer stayMinutes,
                           @RequestParam(required = false) String fromLocation,
                           @RequestParam(required = false) String toLocation,
                           @RequestParam(required = false) String transportMethod,
                           @RequestParam(required = false) String transportNumber,
                           @RequestParam(required = false) String departureTime,
                           @RequestParam(required = false) String arrivalTime,
                           @RequestParam(required = false) String description,
                           HttpSession session) {
        if (session.getAttribute("AID") == null) return "redirect:/login";

        int IPID = aiParseService.getIpidByItem(APIID);
        aiParseService.updateParsedItem(APIID, name, itemType, timeSlot, note, stayMinutes,
                fromLocation, toLocation, transportMethod, transportNumber, departureTime, arrivalTime, description);
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

    // POST /ai-import/item/{apiid}/cancel-match → 取消這個項目跟公司 POI 資料庫的自動比對
    // (使用者反映自動比對有時候會比對到不正確的資料, 想要能個別解除)
    @PostMapping("/item/{apiid}/cancel-match")
    public String cancelMatch(@PathVariable("apiid") int APIID, HttpSession session) {
        if (session.getAttribute("AID") == null) return "redirect:/login";

        int IPID = aiParseService.getIpidByItem(APIID);
        aiParseService.cancelMatch(APIID);
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
                          @RequestParam(required = false) String startDate,
                          HttpSession session,
                          RedirectAttributes redirectAttributes) {
        if (session.getAttribute("AID") == null) return "redirect:/login";

        java.time.LocalDate parsedDate = (startDate != null && !startDate.isBlank())
                ? java.time.LocalDate.parse(startDate) : null;
        // 使用者反映「AI 解析行程草稿, 會出現轉成正式就系統發生錯誤」——原本這裡完全沒有防護, confirmImport()
        // 內部任何一步丟例外都會讓整個 request 被 GlobalExceptionHandler 攔截、直接顯示「系統發生錯誤」
        // 500 頁面, 而且這筆 AiImport 的狀態卡在 parsed、使用者沒辦法重試。改成: 失敗時顯示友善提示訊息、
        // 導回 review 頁面讓使用者可以再按一次「確認」(confirmImport() 失敗時本身已經會把剛建立的殘缺
        // 行程刪掉, 見 AiParseService.confirmImport() 說明), 而不是整頁死掉。
        try {
            Itinerary itinerary = aiParseService.confirmImport(IPID, title, country, region, parsedDate);
            return "redirect:/itinerary/" + itinerary.getITID() + "/board";
        } catch (Exception e) {
            LOGGER.warn("AI 解析結果確認轉成正式行程失敗 (IPID={}): {}", IPID, e.toString(), e);
            redirectAttributes.addFlashAttribute("confirmError",
                    e.getMessage() != null ? e.getMessage() : "轉成正式行程時發生錯誤，請稍後再試一次。");
            return "redirect:/ai-import/" + IPID + "/review";
        }
    }
}