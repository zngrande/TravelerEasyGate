package com.example.UsefulTravel.controller;

import com.example.UsefulTravel.entity.ImageAsset;
import com.example.UsefulTravel.service.ImageAssetService;
import com.example.UsefulTravel.service.PoiService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/images")
public class ImageAssetController {

    private final ImageAssetService imageAssetService;
    private final PoiService poiService;

    @Autowired
    public ImageAssetController(ImageAssetService imageAssetService, PoiService poiService) {
        this.imageAssetService = imageAssetService;
        this.poiService = poiService;
    }

    // GET /images/by-poi/{PID} → 這個景點目前綁定的圖片清單 (給行程項目編輯表單顯示縮圖用)
    // 只回傳自己旅行社上傳的圖片 (即使是共用庫景點, 別間旅行社上傳的照片不會混進來)
    @GetMapping("/by-poi/{PID}")
    @ResponseBody
    public ResponseEntity<List<ImageAsset>> listByPoi(@PathVariable int PID, HttpSession session) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(imageAssetService.listForPoi(PID, AID));
    }

    // GET /images → 圖片資源庫列表 (可篩選只看未綁定 POI 的)
    @GetMapping
    public String list(@RequestParam(required = false) Boolean unlinkedOnly, HttpSession session, Model model) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return "redirect:/login";

        List<ImageAsset> images = (unlinkedOnly != null && unlinkedOnly)
                ? imageAssetService.listUnlinked(AID)
                : imageAssetService.listForAgency(AID);

        model.addAttribute("images", images);
        model.addAttribute("unlinkedOnly", unlinkedOnly != null && unlinkedOnly);
        model.addAttribute("allPois", poiService.listForAgency(AID));
        return "images/list";
    }

    // POST /images/upload → 上傳圖片 (支援多檔), 選了 poiId 就直接綁定, 不跑 AI
    // POST /images/upload-ajax → 給看板 AJAX 呼叫用的上傳端點, 回傳 JSON 結果 (不像 /upload 那樣做 redirect,
    // redirect 會讓錯誤訊息在轉址過程中消失, 前端完全看不到到底成功了沒)
    @PostMapping("/upload-ajax")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> uploadAjax(@RequestParam("files") List<MultipartFile> files,
                                                          @RequestParam(required = false) Integer poiId,
                                                          HttpSession session) {
        Integer AID = (Integer) session.getAttribute("AID");
        Integer UID = (Integer) session.getAttribute("UID");
        if (AID == null) return ResponseEntity.status(401).build();

        int uploaded = 0;
        List<String> errors = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;
            try {
                imageAssetService.upload(AID, UID, file, poiId);
                uploaded++;
            } catch (Exception e) {
                errors.add(file.getOriginalFilename() + "：" + (e.getMessage() != null ? e.getMessage() : e.toString()));
            }
        }

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("uploaded", uploaded);
        result.put("errors", errors);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/upload")
    public String upload(@RequestParam("files") List<MultipartFile> files,
                         @RequestParam(required = false) Integer poiId,
                         HttpSession session, org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        Integer AID = (Integer) session.getAttribute("AID");
        Integer UID = (Integer) session.getAttribute("UID");
        if (AID == null) return "redirect:/login";

        List<String> errors = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;
            try {
                imageAssetService.upload(AID, UID, file, poiId);
            } catch (Exception e) {
                errors.add(file.getOriginalFilename() + "：" + (e.getMessage() != null ? e.getMessage() : e.toString()));
            }
        }

        if (!errors.isEmpty()) {
            // 原本這裡用 model.addAttribute, 但緊接著做 redirect, Model 屬性在轉址後就消失了,
            // 錯誤訊息完全不會顯示出來。要在 redirect 後還看得到, 必須用 RedirectAttributes 的 flash attribute。
            redirectAttributes.addFlashAttribute("uploadErrors", errors);
        }
        return "redirect:/images";
    }

    // GET /images/{id}/file → 輸出圖片實際內容 (給 <img> 標籤用)
    @GetMapping("/{id}/file")
    @ResponseBody
    public ResponseEntity<byte[]> file(@PathVariable("id") int IAID) throws Exception {
        ImageAsset image = imageAssetService.findById(IAID);
        if (image == null) return ResponseEntity.notFound().build();

        byte[] bytes = imageAssetService.loadImageBytes(image);
        MediaType mediaType = image.getContentType() != null
                ? MediaType.parseMediaType(image.getContentType())
                : MediaType.IMAGE_JPEG;

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CACHE_CONTROL, "max-age=86400")
                .body(bytes);
    }

    // POST /images/{id}/link-poi → 手動綁定/改綁到某個 POI (傳空值代表解除綁定)
    @PostMapping("/{id}/link-poi")
    public String linkPoi(@PathVariable("id") int IAID, @RequestParam(required = false) Integer poiId,
                          HttpSession session) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return "redirect:/login";
        imageAssetService.linkToPoi(AID, IAID, poiId);
        return "redirect:/images";
    }

    // POST /images/{id}/delete → 刪除圖片
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable("id") int IAID, HttpSession session) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return "redirect:/login";
        imageAssetService.delete(AID, IAID);
        return "redirect:/images";
    }
}