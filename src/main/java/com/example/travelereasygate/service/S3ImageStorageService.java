package com.example.travelereasygate.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;

/**
 * S3ImageStorageService - 把圖片存在 S3 相容的物件儲存服務。
 *
 * 取代 LocalImageStorageService (存容器本機硬碟): 部署到 Railway 這種無狀態容器環境,
 * 每次重新部署都是全新容器, 本機硬碟內容會整個歸零——先前上傳的圖片檔案就這樣消失了,
 * 但資料庫裡的 image_asset 綁定紀錄還在, 才會看到「已綁定景點」卻圖片變成壞掉的方框
 * 這種症狀 (在原本上傳的那台電腦上看起來正常, 是因為瀏覽器當時把圖片快取下來了,
 * 換一台沒快取過的電腦馬上就看得出破圖)。改用物件儲存服務後, 圖片檔案本身不再依賴
 * 容器硬碟, 不管重新部署幾次、有沒有多個容器實例, 都不會再遺失。
 *
 * 「S3 相容」不是只能接 AWS 官方 S3——Cloudflare R2、Backblaze B2、MinIO、
 * DigitalOcean Spaces 等只要支援 S3 API 的服務都可以透過 app.storage.s3.endpoint
 * 指到對應的服務端點來使用, 不用綁定特定廠商。
 *
 * 啟用方式: 把 app.storage.provider 設成 s3 (預設是 local, 不影響既有部署),
 * 並在 application.properties (建議用環境變數帶入, 不要把金鑰直接寫死 commit
 * 進版本控制) 設定以下屬性:
 *   app.storage.s3.bucket        - bucket 名稱 (必填)
 *   app.storage.s3.region        - 區域, AWS 官方 S3 需要真實區域代碼 (例如 ap-northeast-1),
 *                                   Cloudflare R2 等可以隨意填一個值 (例如 auto), 由 endpoint 決定實際連線位置
 *   app.storage.s3.endpoint      - 自訂端點 (R2/MinIO/B2 等需要, AWS 官方 S3 留空即可)
 *   app.storage.s3.access-key    - access key (必填)
 *   app.storage.s3.secret-key    - secret key (必填)
 *   app.storage.s3.path-style-access - 是否用 path-style 存取 (MinIO 等自架服務通常需要開啟, 預設 false)
 *
 * ImageAssetService 跟其他呼叫端完全不用改——這正是 ImageStorageService 介面當初
 * 設計成抽象層的目的, 資料庫存的還是同一個 storageKey (物件儲存的 object key 取代
 * 本機的相對路徑), 上層邏輯完全無感。
 */
@Service
@ConditionalOnProperty(prefix = "app.storage", name = "provider", havingValue = "s3")
public class S3ImageStorageService implements ImageStorageService {

    @Value("${app.storage.s3.bucket}")
    private String bucket;

    @Value("${app.storage.s3.region:auto}")
    private String region;

    @Value("${app.storage.s3.endpoint:}")
    private String endpoint;

    @Value("${app.storage.s3.access-key}")
    private String accessKey;

    @Value("${app.storage.s3.secret-key}")
    private String secretKey;

    @Value("${app.storage.s3.path-style-access:false}")
    private boolean pathStyleAccess;

    // 物件 key 統一加這個前綴, 方便之後在同一個 bucket 裡跟其他用途的物件區分, 不影響任何查詢邏輯
    // (資料庫存的就是含前綴的完整 key, 讀寫都用同一把 key, 純粹是儲存空間裡的資料夾分類)
    private static final String KEY_PREFIX = "images/";

    private S3Client s3Client;

    @PostConstruct
    private void init() {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
        S3ClientBuilder builder = S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .region(Region.of(region))
                .forcePathStyle(pathStyleAccess);
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        this.s3Client = builder.build();
    }

    @Override
    public String store(byte[] imageBytes, String originalFilename) throws IOException {
        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf('.'));
        }
        String storageKey = KEY_PREFIX + UUID.randomUUID() + ext;

        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(storageKey)
                            .build(),
                    RequestBody.fromBytes(imageBytes));
        } catch (S3Exception e) {
            throw new IOException("上傳圖片到物件儲存失敗: " + e.getMessage(), e);
        }

        return storageKey; // 資料庫存這個 object key 就好, 換服務/搬 bucket 都不受影響
    }

    @Override
    public byte[] load(String storageKey) throws IOException {
        try {
            return s3Client.getObject(
                            GetObjectRequest.builder()
                                    .bucket(bucket)
                                    .key(storageKey)
                                    .build())
                    .readAllBytes();
        } catch (S3Exception e) {
            throw new IOException("讀取圖片失敗: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            s3Client.deleteObject(
                    DeleteObjectRequest.builder()
                            .bucket(bucket)
                            .key(storageKey)
                            .build());
        } catch (S3Exception e) {
            // 刪除失敗不影響主流程 (資料庫紀錄還是會刪掉), 頂多留一個孤兒物件在儲存空間裡
        }
    }
}
