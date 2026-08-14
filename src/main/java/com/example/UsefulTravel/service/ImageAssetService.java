package com.example.UsefulTravel.service;

import com.example.UsefulTravel.DAO.ImageAssetDAO;
import com.example.UsefulTravel.entity.ImageAsset;
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

    @Autowired
    public ImageAssetService(ImageStorageService imageStorageService, ImageAssetDAO imageAssetDAO) {
        this.imageStorageService = imageStorageService;
        this.imageAssetDAO = imageAssetDAO;
    }

    /**
     * 上傳一張圖片, 可以直接指定要綁定的 POI (poiId 傳 null 就是先不綁, 之後在列表頁再選)
     */
    public ImageAsset upload(int AID, Integer UID, MultipartFile file, Integer poiId) throws IOException {
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("只支援圖片檔案 (jpg/png/webp 等)");
        }

        byte[] bytes = file.getBytes();
        String storageKey = imageStorageService.store(bytes, file.getOriginalFilename());

        ImageAsset image = new ImageAsset(AID, storageKey, file.getOriginalFilename(), contentType, UID);
        image.setMatchedPid(poiId);
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

    public List<ImageAsset> listForPoi(int PID) {
        return imageAssetDAO.findByPoi(PID);
    }

    public void linkToPoi(int IAID, Integer PID) {
        ImageAsset image = imageAssetDAO.findById(IAID);
        if (image == null) throw new IllegalArgumentException("找不到這張圖片");
        image.setMatchedPid(PID);
        imageAssetDAO.save(image);
    }

    public void delete(int IAID) {
        ImageAsset image = imageAssetDAO.findById(IAID);
        if (image == null) return;
        imageStorageService.delete(image.getFilePath());
        imageAssetDAO.deleteById(IAID);
    }
}
