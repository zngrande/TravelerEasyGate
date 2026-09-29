package com.example.travelereasygate.service;

import java.io.IOException;

/**
 * ImageStorageService - 圖片實際存放的抽象層
 *
 * 有兩個實作:
 * - LocalImageStorageService (存本機硬碟): 只適合單機開發/測試, 部署到雲端容器化/
 *   無狀態環境 (例如 Railway, 重新部署會換一個全新容器, 各容器硬碟不共享) 會出問題
 *   (圖片時有時無、重新部署直接消失, 但資料庫的綁定紀錄還在, 造成「已綁定景點」
 *   卻圖片壞掉的症狀)。這是預設值, 沒有另外設定 app.storage.provider 就是用這個。
 * - S3ImageStorageService (存 S3 相容物件儲存服務, 例如 AWS S3 / Cloudflare R2 /
 *   Backblaze B2 / MinIO 等): 把 app.storage.provider 設成 s3, 並在
 *   application.properties (或環境變數) 設定 app.storage.s3.* 底下的連線資訊即可啟用,
 *   不需要改 ImageAssetService 或任何呼叫端的程式碼——這正是這個介面當初設計成抽象層
 *   的目的。
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
