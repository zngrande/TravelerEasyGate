package com.example.UsefulTravel.service;

import com.example.UsefulTravel.DAO.ImageAssetDAO;
import com.example.UsefulTravel.DAO.PoiDAO;
import com.example.UsefulTravel.entity.ImageAsset;
import com.example.UsefulTravel.entity.Poi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

/**
 * ImageAssetService - 圖片資源庫: 上傳、AI 自動標籤、自動比對公司 POI 資料庫
 */
@Service
public class ImageAssetService {

    private static final String TAG_SYSTEM_PROMPT = """
        你是旅遊業圖庫管理助手。使用者會給你一張照片 (可能是旅遊景點、餐廳、飯店、活動現場等)。
        請分析這張照片並「只」輸出一個 JSON 物件, 不要有任何其他文字、不要用 markdown code fence 包起來。

        JSON 格式:
        {
          "description": "用一兩句話描述這張照片的內容",
          "tags": ["標籤1", "標籤2", "標籤3"],
          "likely_place_name": "如果照片內容像是某個知名地標/景點, 猜測最可能的名稱; 看不出來或是普通生活照就給 null"
        }

        規則:
        - tags 給 3~8 個關鍵字, 包含: 地點類型 (例如寺廟/海灘/夜市/飯店大廳)、視覺特徵 (例如日落/夜景/建築/美食)、氛圍 (例如浪漫/熱鬧/寧靜)
        - likely_place_name 只有在有明顯地標特徵時才猜測 (例如清水寺、艾菲爾鐵塔的外觀特徵很明顯), 一般街景/普通餐點照片就給 null
        - 絕對不要輸出 JSON 以外的任何文字
        """;

    private final ImageStorageService imageStorageService;
    private final ImageAssetDAO imageAssetDAO;
    private final PoiDAO poiDAO;
    private final AnthropicClient anthropicClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public ImageAssetService(ImageStorageService imageStorageService, ImageAssetDAO imageAssetDAO,
                              PoiDAO poiDAO, AnthropicClient anthropicClient, ObjectMapper objectMapper) {
        this.imageStorageService = imageStorageService;
        this.imageAssetDAO = imageAssetDAO;
        this.poiDAO = poiDAO;
        this.anthropicClient = anthropicClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 上傳一張圖片並立即做 AI 標籤/自動比對 (同步做完, MVP 先這樣, 之後量大可以改非同步佇列處理)
     */
    public ImageAsset upload(int AID, Integer UID, MultipartFile file) throws IOException {
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("只支援圖片檔案 (jpg/png/webp 等)");
        }

        byte[] bytes = file.getBytes();
        String storageKey = imageStorageService.store(bytes, file.getOriginalFilename());

        ImageAsset image = new ImageAsset(AID, storageKey, file.getOriginalFilename(), contentType, UID);
        imageAssetDAO.save(image); // 先存一筆, 拿到 IAID, 就算 AI 標籤失敗圖片還是有上傳成功

        try {
            autoTag(image, bytes, contentType, AID);
        } catch (Exception e) {
            image.setTagStatus("failed");
            imageAssetDAO.save(image);
        }

        return image;
    }

    private void autoTag(ImageAsset image, byte[] bytes, String contentType, int AID) throws Exception {
        String response = anthropicClient.analyzeImage(bytes, contentType, TAG_SYSTEM_PROMPT,
                "請分析這張照片並輸出標籤跟描述");
        JsonNode root = objectMapper.readTree(stripCodeFence(response));

        image.setAiDescription(root.path("description").asText(null));

        StringBuilder tagsCsv = new StringBuilder();
        for (JsonNode tagNode : root.path("tags")) {
            if (!tagsCsv.isEmpty()) tagsCsv.append(", ");
            tagsCsv.append(tagNode.asText());
        }
        image.setTags(tagsCsv.toString());

        // 用 AI 猜測的地標名稱去比對公司 POI 資料庫, 比對到就自動綁定
        String likelyName = root.path("likely_place_name").asText(null);
        if (likelyName != null && !likelyName.isBlank() && !"null".equalsIgnoreCase(likelyName)) {
            List<Poi> matches = poiDAO.searchByKeyword(AID, likelyName, null);
            if (!matches.isEmpty()) {
                image.setMatchedPid(matches.get(0).getPID());
            }
        }

        image.setTagStatus("tagged");
        imageAssetDAO.save(image);
    }

    private String stripCodeFence(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```[a-zA-Z]*\\s*", "");
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
        }
        return trimmed.trim();
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
