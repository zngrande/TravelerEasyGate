package com.example.UsefulTravel.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * LocalImageStorageService - 把圖片存在伺服器本機的資料夾
 *
 * 只適合單機開發/測試環境使用! 部署到雲端 (容器化/自動縮放/無狀態部署) 時圖片會遺失,
 * 屆時要換成 S3ImageStorageService 之類的物件儲存實作 (實作 ImageStorageService 介面即可)。
 */
@Service
public class LocalImageStorageService implements ImageStorageService {

    @Value("${app.upload.image-dir:uploads/images}")
    private String imageDir;

    @Override
    public String store(byte[] imageBytes, String originalFilename) throws IOException {
        Path dir = Paths.get(imageDir);
        Files.createDirectories(dir);

        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf('.'));
        }
        String storageKey = UUID.randomUUID() + ext;

        Path target = dir.resolve(storageKey);
        Files.copy(new java.io.ByteArrayInputStream(imageBytes), target, StandardCopyOption.REPLACE_EXISTING);

        return storageKey; // 資料庫存這個相對檔名就好, 不用存完整路徑 (換機器/資料夾位置改了也不怕)
    }

    @Override
    public byte[] load(String storageKey) throws IOException {
        Path target = Paths.get(imageDir).resolve(storageKey);
        return Files.readAllBytes(target);
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(Paths.get(imageDir).resolve(storageKey));
        } catch (IOException e) {
            // 刪除失敗不影響主流程 (資料庫紀錄還是會刪掉), 頂多留一個孤兒檔案在硬碟上
        }
    }
}
