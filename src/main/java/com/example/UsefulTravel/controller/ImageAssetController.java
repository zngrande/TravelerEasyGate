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
    @PostMapping("/upload")
    public String upload(@RequestParam("files") List<MultipartFile> files,
                          @RequestParam(required = false) Integer poiId,
                          HttpSession session, Model model) {
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
            model.addAttribute("uploadErrors", errors);
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
        if (session.getAttribute("AID") == null) return "redirect:/login";
        imageAssetService.linkToPoi(IAID, poiId);
        return "redirect:/images";
    }

    // POST /images/{id}/delete → 刪除圖片
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable("id") int IAID, HttpSession session) {
        if (session.getAttribute("AID") == null) return "redirect:/login";
        imageAssetService.delete(IAID);
        return "redirect:/images";
    }
}
