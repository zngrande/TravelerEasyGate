package com.example.travelereasygate.controller;

import com.example.travelereasygate.entity.ImageAsset;
import com.example.travelereasygate.entity.Poi;
import com.example.travelereasygate.service.ImageAssetService;
import com.example.travelereasygate.service.PermissionService;
import com.example.travelereasygate.service.PoiService;
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
    private final PermissionService permissionService;

    @Autowired
    public ImageAssetController(ImageAssetService imageAssetService, PoiService poiService, PermissionService permissionService) {
        this.imageAssetService = imageAssetService;
        this.poiService = poiService;
        this.permissionService = permissionService;
    }

    // 圖片資料庫的管理動作 (列表/上傳/綁定/刪除) 對應側邊欄「資料庫管理」的顯示條件, 只有 ADMIN / EDITOR 能用。
    // 純讀取/顯示用的端點 (listByPoi, file) 沒有用這個擋, 因為那些是給任何看得到行程/報價單內容的頁面
    // 顯示縮圖用的, 限制太嚴格反而會讓其他角色連原本看得到的圖都看不到。
    private boolean canEditItinerary(HttpSession session) {
        return permissionService.canEditItinerary((String) session.getAttribute("role"));
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
        if (!canEditItinerary(session)) return "redirect:/agency/dashboard";

        List<ImageAsset> images = (unlinkedOnly != null && unlinkedOnly)
                ? imageAssetService.listUnlinked(AID)
                : imageAssetService.listForAgency(AID);

        model.addAttribute("images", images);
        model.addAttribute("unlinkedOnly", unlinkedOnly != null && unlinkedOnly);
        model.addAttribute("allPois", poiService.listForAgency(AID));

        // 使用者反映「從行程看板新增的圖片不會顯示綁定的景點」——追查後發現根因: 這個頁面原本用
        // th:each 在 allPois (poiService.listForAgency(AID), 即 findByAgencyOrShared) 裡逐一比對
        // img.matchedPid 來找出景點名稱顯示; 但 allPois 只包含「目前對這間旅行社可見」的景點——如果
        // 這間旅行社後來在「景點管理」頁編輯過某筆共用庫景點 (PoiService.overrideSharedPoi()), 系統會
        // 幫這間旅行社複製一份專屬版本, 同時把原始共用庫那筆從 findByAgencyOrShared() 的結果裡隱藏起來
        // (避免同一筆資料同時看到共用庫原版跟自己改過的版本), 但既有指到「原始共用庫 PID」的行程項目
        // /圖片綁定 (image_asset.matched_pid) 不會被自動改指到新複本——從行程看板直接對這種項目上傳圖片
        // 時, 綁定的還是這個「現在已經被隱藏」的原始 PID, matchedPid 有值、綁定其實是成功的, 但這個頁面
        // 在 allPois 裡怎麼找都找不到這筆景點, th:each 比對不到任何東西, 顯示欄位就整個空白 (看起來像
        // 「沒有顯示綁定的景點」)。
        // 修正: 額外準備一份不受 AID 可見性限制的「matchedPid → Poi」對照表, 針對這個頁面實際會用到的
        // 每一個 matchedPid, 直接用原始的 PoiService.findById() 查 (不套用 SHARED_OR_OWN_CLAUSE 這層
        // 可見性篩選), 這樣就算景點已經被隱藏也還是查得到資料本身, 畫面上能正確顯示已綁定的景點名稱。
        java.util.Map<Integer, Poi> matchedPoiMap = new java.util.HashMap<>();
        for (ImageAsset img : images) {
            Integer pid = img.getMatchedPid();
            if (pid != null && !matchedPoiMap.containsKey(pid)) {
                Poi poi = poiService.findById(pid);
                if (poi != null) matchedPoiMap.put(pid, poi);
            }
        }
        model.addAttribute("matchedPoiMap", matchedPoiMap);
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
        if (!canEditItinerary(session)) return ResponseEntity.status(403).build();

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
        if (!canEditItinerary(session)) return "redirect:/agency/dashboard";

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
        if (!canEditItinerary(session)) return "redirect:/agency/dashboard";
        imageAssetService.linkToPoi(AID, IAID, poiId);
        return "redirect:/images";
    }

    // POST /images/{id}/delete → 刪除圖片
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable("id") int IAID, HttpSession session) {
        Integer AID = (Integer) session.getAttribute("AID");
        if (AID == null) return "redirect:/login";
        if (!canEditItinerary(session)) return "redirect:/agency/dashboard";
        imageAssetService.delete(AID, IAID);
        return "redirect:/images";
    }
}