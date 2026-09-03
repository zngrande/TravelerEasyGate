package com.example.travelereasygate.service;

import java.io.IOException;

/**
 * ImageStorageService - 圖片實際存放的抽象層
 *
 * 目前唯一的實作是 LocalImageStorageService (存本機硬碟), 只適合單機開發/測試。
 *
 * 之後要部署到雲端時, 因為雲端容器通常是無狀態的 (重新部署/自動縮放會換機器,
 * 各機器硬碟不共享), 本機硬碟存圖片會出問題 (圖片時有時無、重新部署直接消失)。
 * 屆時只要新增一個實作這個介面的 S3ImageStorageService (或 GCS 版本),
 * 把 @Service 註解換過去, ImageAssetService 完全不用改, 呼叫端也不用改。
 */
public interface ImageStorageService {

    /**
     * 儲存圖片, 回傳一個之後可以用來讀取/顯示這張圖的識別碼 (本機實作是相對路徑,
     * S3 實作的話會是 object key)
     */
    String store(byte[] imageBytes, String originalFilename) throws IOException;

    /**
     * 依儲存時回傳的識別碼讀取圖片位元組內容
     */
    byte[] load(String storageKey) throws IOException;

    /**
     * 刪除圖片
     */
    void delete(String storageKey);
}
