package com.example.UsefulTravel.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * AnthropicClient - 呼叫 Anthropic Claude API 的最小封裝
 *
 * 需要在 application.properties 設定:
 *   anthropic.api.key=你的 API Key (sk-ant-...)
 *   anthropic.api.model=claude-sonnet-5   (可選, 有預設值)
 *
 * API Key 請去 https://console.anthropic.com 申請, 不要 commit 進 git,
 * 建議用環境變數 ANTHROPIC_API_KEY 帶入 (application.properties 已經預設吃環境變數)
 */
@Component
public class AnthropicClient {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String API_VERSION = "2023-06-01";

    @Value("${anthropic.api.key:}")
    private String apiKey;

    @Value("${anthropic.api.model:claude-sonnet-5}")
    private String model;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public AnthropicClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * 呼叫 Claude, 回傳純文字內容 (通常是我們要求它輸出的 JSON 字串)
     *
     * @param systemPrompt 系統提示詞 (定義任務規則/輸出格式)
     * @param userContent  使用者輸入內容 (行程文字)
     * @param maxTokens    回應上限 token 數
     */
    /**
     * 針對單一地點快速估算「遊客一般會停留幾分鐘」, 用在新增 POI 時自動帶入建議停留時間
     * (Google Maps 本身沒有開放這個資料的公開 API, 用 AI 常識估算最接近的替代方案)
     * 回傳 null 代表估算失敗 (API 沒設定/呼叫失敗/回應無法解析成數字), 呼叫端要自己處理
     */
    public Integer estimateStayMinutes(String placeName, String category, String address) {
        try {
            String system = """
                你是旅遊行程規劃助手。使用者會給你一個地點的名稱、類型、地址(可能沒有)。
                請估算一般遊客在這個地點會停留幾分鐘，只能輸出一個整數(分鐘數，15的倍數，例如 60、90、120)，
                不要輸出任何其他文字、單位、說明或標點符號。
                """;
            String userContent = "名稱: " + placeName
                    + "\n類型: " + (category != null ? category : "未知")
                    + "\n地址: " + (address != null && !address.isBlank() ? address : "未提供");

            String response = complete(system, userContent, 20);
            String digits = response.replaceAll("[^0-9]", "");
            if (digits.isBlank()) return null;
            return Integer.parseInt(digits);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 幫景點/餐廳/飯店自動生成一段簡短的介紹說明, 用在:
     *   1) AI 解析行程時, 把項目寫進 POI 資料庫的當下自動產生介紹
     *   2) 行程編輯畫面手動把自訂項目加入 POI 資料庫時自動產生介紹
     *
     * @param name     地點名稱 (必填)
     * @param category attraction / restaurant / hotel 等分類, 可為 null
     * @param country  國家, 可為 null
     * @param region   地區/城市, 可為 null
     * @param hint     原始行程文字裡跟這個地點有關的補充說明 (例如 AI 解析時的 note), 可為 null,
     *                 用來讓生成的介紹更貼近這份行程實際提到的重點, 不是必要資訊
     * @return 生成的介紹文字 (繁體中文, 約 80~150 字); 生成失敗 (API 沒設定/呼叫失敗) 回傳 null, 呼叫端要自己處理 fallback
     */
    public String generateDescription(String name, String category, String country, String region, String hint) {
        try {
            String system = """
                你是旅遊行程規劃助手, 負責幫旅行社的景點/餐廳/飯店資料庫生成簡短的介紹說明。
                使用者會給你一個地點的名稱、類型、國家/地區, 有時還會附上這個地點在某份行程文件裡原本的補充備註。
                請用繁體中文寫一段大約 80~150 字的介紹, 內容可以包含: 這個地方的特色/賣點、適合什麼樣的遊客、
                如果是景點可以提一下歷史或亮點, 如果是餐廳可以提一下料理特色, 如果是飯店可以提一下服務/位置優勢。
                只能輸出介紹文字本身, 不要加標題、不要加任何前言或說明文字、不要用引號包起來。
                如果給的資訊不足以判斷細節, 就用這個地點的類型/地區合理推測, 寫一段通用但不失真的介紹, 不要留白。
                標點符號一律使用繁體中文全形標點(，。、「」！？), 不要使用半形標點(, . ! ?)。
                """;
            StringBuilder userContent = new StringBuilder();
            userContent.append("名稱: ").append(name);
            userContent.append("\n類型: ").append(category != null ? category : "未知");
            String location = String.join(" ", nonBlank(region), nonBlank(country)).trim();
            userContent.append("\n地區: ").append(location.isEmpty() ? "未知" : location);
            if (hint != null && !hint.isBlank()) {
                userContent.append("\n行程文件裡的原始備註 (僅供參考, 不要照抄): ").append(hint);
            }

            String response = complete(system, userContent.toString(), 400);
            String trimmed = response == null ? "" : response.trim();
            if (trimmed.isEmpty()) return null;
            // 保險機制: 即使 AI 沒有完全照 prompt 指示, 也把常見的半形標點轉成全形,
            // 避免混用半形、全形逗號句號等問題。刻意跳過數字前後的逗號/句號/冒號,
            // 避免誤轉小數點(3.5)、千分位(1,000)、時間(9:00)等本來就該用半形的情況。
            return toFullWidthPunctuation(trimmed);
        } catch (Exception e) {
            return null;
        }
    }

    private String nonBlank(String s) {
        return s == null ? "" : s;
    }

    /**
     * 把常見的半形中文標點轉成全形, 用在 AI 生成的中文介紹文字上。
     * 逗號/句號/冒號會避開前後緊接數字的情況(例如小數點、千分位、時間), 保留半形；
     * 其餘標點(驚嘆號、問號、分號、括號)一律轉全形。
     */
    private String toFullWidthPunctuation(String text) {
        if (text == null || text.isBlank()) return text;
        String result = text;
        result = result.replaceAll("(?<!\\d),(?!\\d)", "，");
        result = result.replaceAll("(?<!\\d)\\.(?!\\d)", "。");
        result = result.replaceAll("(?<!\\d):(?!\\d)", "：");
        result = result.replace("!", "！");
        result = result.replace("?", "？");
        result = result.replace(";", "；");
        result = result.replace("(", "（").replace(")", "）");
        return result;
    }

    /**
     * 分析圖片內容, 用於圖片資源庫的自動標籤/描述
     *
     * @param imageBytes 圖片位元組內容
     * @param mediaType  image/jpeg, image/png, image/webp 等
     * @param systemPrompt 系統提示詞 (定義要 AI 輸出什麼格式)
     * @param userPrompt   額外的文字指示 (例如「請描述這張圖片並列出標籤」)
     */
    public String analyzeImage(byte[] imageBytes, String mediaType, String systemPrompt, String userPrompt) throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "尚未設定 anthropic.api.key，請在 application.properties 或環境變數 ANTHROPIC_API_KEY 設定你的 Claude API Key");
        }

        String base64Image = java.util.Base64.getEncoder().encodeToString(imageBytes);

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", 1024);
        body.put("system", systemPrompt);

        ArrayNode messages = body.putArray("messages");
        ObjectNode userMsg = objectMapper.createObjectNode();
        userMsg.put("role", "user");

        ArrayNode content = userMsg.putArray("content");

        ObjectNode imageBlock = objectMapper.createObjectNode();
        imageBlock.put("type", "image");
        ObjectNode source = objectMapper.createObjectNode();
        source.put("type", "base64");
        source.put("media_type", mediaType);
        source.put("data", base64Image);
        imageBlock.set("source", source);
        content.add(imageBlock);

        ObjectNode textBlock = objectMapper.createObjectNode();
        textBlock.put("type", "text");
        textBlock.put("text", userPrompt);
        content.add(textBlock);

        messages.add(userMsg);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("x-api-key", apiKey)
                .header("anthropic-version", API_VERSION)
                .header("content-type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Claude API 呼叫失敗 (HTTP " + response.statusCode() + "): " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode contentArray = root.path("content");

        StringBuilder text = new StringBuilder();
        if (contentArray.isArray()) {
            for (JsonNode block : contentArray) {
                if ("text".equals(block.path("type").asText())) {
                    text.append(block.path("text").asText());
                }
            }
        }
        return text.toString();
    }

    public String complete(String systemPrompt, String userContent, int maxTokens) throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "尚未設定 anthropic.api.key，請在 application.properties 或環境變數 ANTHROPIC_API_KEY 設定你的 Claude API Key");
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        body.put("system", systemPrompt);

        ArrayNode messages = body.putArray("messages");
        ObjectNode userMsg = objectMapper.createObjectNode();
        userMsg.put("role", "user");
        userMsg.put("content", userContent);
        messages.add(userMsg);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("x-api-key", apiKey)
                .header("anthropic-version", API_VERSION)
                .header("content-type", "application/json")
                .timeout(Duration.ofSeconds(180))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Claude API 呼叫失敗 (HTTP " + response.statusCode() + "): " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode contentArray = root.path("content");

        StringBuilder text = new StringBuilder();
        if (contentArray.isArray()) {
            for (JsonNode block : contentArray) {
                if ("text".equals(block.path("type").asText())) {
                    text.append(block.path("text").asText());
                }
            }
        }
        return text.toString();
    }
}