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
}
