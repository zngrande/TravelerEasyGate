package com.example.travelereasygate.service;

import com.example.travelereasygate.DAO.ImageAssetDAO;
import com.example.travelereasygate.entity.ImageAsset;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * ImageAssetService - 圖片資源庫: 單純上傳 + 手動綁定公司 POI 資料庫 (不跑 AI 辨識)
 */
@Service
public class ImageAssetService {

    private final ImageStorageService imageStorageService;
    private final ImageAssetDAO imageAssetDAO;
    private final PoiService poiService;

    @Autowired
    public ImageAssetService(ImageStorageService imageStorageService, ImageAssetDAO imageAssetDAO, PoiService poiService) {
        this.imageStorageService = imageStorageService;
        this.imageAssetDAO = imageAssetDAO;
        this.poiService = poiService;
    }

    /**
     * 上傳一張圖片, 可以直接指定要綁定的 POI (poiId 傳 null 就是先不綁, 之後在列表頁再選)
     *
     * 使用者反映「從行程看板新增的圖片不會顯示綁定的景點」, 追查到底: 行程看板傳進來的 poiId
     * 其實是行程項目自己記錄的 PID, 可能是這間旅行社 override 共用庫景點「之前」就綁定好、
     * 之後一直沒有更新過的舊值——見 PoiService.resolveCurrentPid() 的說明。這裡先轉換一次
     * 再存進 matched_pid, 這樣不管是從行程看板、圖片資源庫、或是手動改綁, 最後存進資料庫的
     * 都會是這間旅行社「目前實際看得到」的那個 PID, 不會再有兩個頁面對同一批圖片各自顯示
     * 不同綁定狀態的矛盾。
     */
    public ImageAsset upload(int AID, Integer UID, MultipartFile file, Integer poiId) throws IOException {
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("只支援圖片檔案 (jpg/png/webp 等)");
        }

        byte[] bytes = file.getBytes();
        String storageKey = imageStorageService.store(bytes, file.getOriginalFilename());

        ImageAsset image = new ImageAsset(AID, storageKey, file.getOriginalFilename(), contentType, UID);
        image.setMatchedPid(poiId != null ? poiService.resolveCurrentPid(AID, poiId) : null);
        image.setTagStatus("uploaded"); // 單純上傳, 沒有跑 AI 標籤
        imageAssetDAO.save(image);

        return image;
    }

    public byte[] loadImageBytes(ImageAsset image) throws IOException {
        return imageStorageService.load(image.getFilePath());
    }

    public ImageAsset findById(int IAID) {
        return imageAssetDAO.findById(IAID);
    }

    public List<ImageAsset> listForAgency(int AID) {
        return imageAssetDAO.findByAgency(AID);
    }

    public List<ImageAsset> listUnlinked(int AID) {
        return imageAssetDAO.findUnlinked(AID);
    }

    // 圖片是「各自綁定」: 每張圖片只屬於上傳它的旅行社 (即使綁的是共用庫的 PID),
    // 不需要也不應該去改動/複製 POI 本身 — POI 複本 (override) 只在真的修改到 POI 欄位時才需要。
    //
    // 這裡查詢前一樣先用 resolveCurrentPid() 轉換一次 PID (見 upload() 的說明): 呼叫端 (例如
    // 行程看板) 傳進來的 PID 可能是這間旅行社 override 共用庫景點之前的舊值, 轉換後才能查到
    // 這間旅行社目前實際綁定在複本 PID 上的圖片, 不會因為 PID 對不起來而查出空結果。
    public List<ImageAsset> listForPoi(int PID, int AID) {
        return imageAssetDAO.findByPoi(poiService.resolveCurrentPid(AID, PID), AID);
    }

    public void linkToPoi(int AID, int IAID, Integer PID) {
        ImageAsset image = imageAssetDAO.findById(IAID);
        if (image == null) throw new IllegalArgumentException("找不到這張圖片");
        if (image.getAID() != AID) return; // 不是自己上傳的圖片, 靜默忽略 (避免竄改網址操作別間旅行社的照片)
        image.setMatchedPid(PID != null ? poiService.resolveCurrentPid(AID, PID) : null);
        imageAssetDAO.save(image);
    }

    public void delete(int AID, int IAID) {
        ImageAsset image = imageAssetDAO.findById(IAID);
        if (image == null) return;
        if (image.getAID() != AID) return; // 同上, 不是自己的圖片不能刪
        imageStorageService.delete(image.getFilePath());
        imageAssetDAO.deleteById(IAID);
    }
}
