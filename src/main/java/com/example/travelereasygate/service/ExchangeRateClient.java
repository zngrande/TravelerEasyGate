package com.example.travelereasygate.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ExchangeRateClient - 封裝呼叫免費匯率 API (open.er-api.com), 給每日自動更新匯率用。
 *
 * 選用這個 API 的原因:
 *   - 完全免費、不需要申請 API Key, 沒有額外設定成本
 *   - 支援 161 種貨幣, 包含 TWD (公司內部帳本以 TWD 為準, 常見的 Frankfurter 等歐系免費 API
 *     只支援歐央行公告的 30 幾種貨幣, 沒有 TWD, 沒辦法用)
 *   - 每天更新一次, 符合這裡「每天更新」的需求 (不需要即時匯率的話沒必要用到會員制/更頻繁更新的付費方案)
 *
 * API 文件: https://www.exchangerate-api.com/docs/free
 * 免費版有基本的流量限制與需要標示出處, 用量大或需要更高更新頻率時可以改申請該站的付費方案,
 * 到時候只要把下面 API_URL_TEMPLATE 換成 https://v6.exchangerate-api.com/v6/{key}/latest/{base} 即可,
 * 回傳格式 (conversion_rates) 跟免費版的 rates 略有不同, 要一併調整解析欄位名稱。
 */
@Component
public class ExchangeRateClient {

    private static final String API_URL_TEMPLATE = "https://open.er-api.com/v6/latest/%s";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public ExchangeRateClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    /**
     * 取得「以 TWD 為基準」的匯率表: 回傳的 map 是 code -> rateToTwd (1 單位該幣別等於多少 TWD),
     * 直接對應到 Currency.rateToTwd 的定義, 呼叫端不用再自己換算。
     *
     * @return code -> rateToTwd 的對照表 (一定包含 "TWD" -> 1); 呼叫失敗回傳 null 讓呼叫端決定要不要整批放棄這次更新
     */
    public Map<String, BigDecimal> fetchRatesToTwd() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(String.format(API_URL_TEMPLATE, "TWD")))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return null;

            JsonNode root = objectMapper.readTree(response.body());
            if (!"success".equalsIgnoreCase(root.path("result").asText(""))) return null;

            JsonNode rates = root.path("rates"); // 這裡的 rates 是「1 TWD = 多少該幣別」, 跟我們要的方向相反
            if (rates.isMissingNode() || !rates.isObject()) return null;

            Map<String, BigDecimal> rateToTwd = new LinkedHashMap<>();
            for (Map.Entry<String, JsonNode> entry : rates.properties()) {
                double perTwd = entry.getValue().asDouble(0);
                if (perTwd > 0) {
                    // 反過來算: 1 該幣別 = 1/perTwd TWD, 取 6 位小數避免小額幣別 (例如日圓、越南盾) 精度不夠
                    BigDecimal twdPerUnit = BigDecimal.ONE.divide(BigDecimal.valueOf(perTwd), 6, RoundingMode.HALF_UP);
                    rateToTwd.put(entry.getKey(), twdPerUnit);
                }
            }
            rateToTwd.put("TWD", BigDecimal.ONE);
            return rateToTwd;
        } catch (Exception e) {
            return null; // 網路問題/API 暫時掛掉, 讓呼叫端保留原本的匯率不動, 等下次排程再試
        }
    }
}
