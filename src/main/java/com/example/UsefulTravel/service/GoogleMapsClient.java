package com.example.UsefulTravel.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * GoogleMapsClient - 封裝呼叫 Google Maps 相關 API
 *
 * 需要在 application.properties 設定:
 *   google.maps.api.key=你的 API Key
 *
 * 申請網址: https://console.cloud.google.com/google/maps-apis
 * 需要啟用: Distance Matrix API, Maps Static API, Maps JavaScript API
 *
 * 如果沒設定 key, 系統會自動退回用經緯度直線距離估算 (不會整個掛掉)
 */
@Component
public class GoogleMapsClient {

    @Value("${google.maps.api.key:}")
    private String apiKey;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public GoogleMapsClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String getApiKey() {
        return apiKey;
    }

    public static class GeocodeResult {
        public final double latitude;
        public final double longitude;
        public GeocodeResult(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    /**
     * 把地名/地址轉成經緯度 (AI 匯入新增 POI 時自動呼叫, 不然地圖畫不出來)
     * 沒設定 key、地址空白、或查不到結果都回傳 null, 呼叫端要自己處理 (通常就是留空經緯度)
     */
    public GeocodeResult geocode(String address) {
        if (!isConfigured() || address == null || address.isBlank()) return null;

        try {
            String url = "https://maps.googleapis.com/maps/api/geocode/json"
                    + "?address=" + URLEncoder.encode(address, StandardCharsets.UTF_8)
                    + "&language=zh-TW&key=" + apiKey;

            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return null;

            JsonNode root = objectMapper.readTree(response.body());
            if (!"OK".equals(root.path("status").asText())) return null;

            JsonNode location = root.path("results").path(0).path("geometry").path("location");
            if (location.isMissingNode()) return null;

            return new GeocodeResult(location.path("lat").asDouble(), location.path("lng").asDouble());
        } catch (Exception e) {
            return null; // 地理編碼失敗不影響主流程, 讓 POI 先留空經緯度就好
        }
    }

    /**
     * 解析使用者填的「地點提示」: 可以是 Google 地圖網址 (含短網址) 或一般地址文字, 盡量準確定位
     * 判斷順序: 1) 網址裡直接有經緯度就抓出來 2) 短網址先展開成完整網址再抓一次
     *          3) 都抓不到座標就當地址文字直接送去 Geocoding API 查
     */
    public GeocodeResult resolveLocationHint(String hint) {
        if (hint == null || hint.isBlank()) return null;
        String trimmed = hint.trim();

        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            GeocodeResult fromUrl = extractCoordsFromUrl(trimmed);
            if (fromUrl != null) return fromUrl;

            String expanded = expandShortUrl(trimmed);
            if (expanded != null) {
                fromUrl = extractCoordsFromUrl(expanded);
                if (fromUrl != null) return fromUrl;
                // 展開後的網址通常會帶地點名稱, 拿去做一般地理編碼當最後手段
                return geocode(expanded);
            }
            return null;
        }

        // 不是網址, 當作地址/地點名稱直接查
        return geocode(trimmed);
    }

    // 從網址字串直接用常見的 Google Maps 座標格式抓經緯度, 抓不到回傳 null
    private GeocodeResult extractCoordsFromUrl(String url) {
        // 格式1: .../@25.0330123,121.5654321,17z
        java.util.regex.Matcher m1 = java.util.regex.Pattern
                .compile("@(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)").matcher(url);
        if (m1.find()) {
            return new GeocodeResult(Double.parseDouble(m1.group(1)), Double.parseDouble(m1.group(2)));
        }
        // 格式2: !3d25.0330123!4d121.5654321 (地點詳細頁常見格式)
        java.util.regex.Matcher m2 = java.util.regex.Pattern
                .compile("!3d(-?\\d+\\.\\d+)!4d(-?\\d+\\.\\d+)").matcher(url);
        if (m2.find()) {
            return new GeocodeResult(Double.parseDouble(m2.group(1)), Double.parseDouble(m2.group(2)));
        }
        // 格式3: ?q=25.0330123,121.5654321 或 &ll=25.0330123,121.5654321
        java.util.regex.Matcher m3 = java.util.regex.Pattern
                .compile("[?&](?:q|ll|query)=(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)").matcher(url);
        if (m3.find()) {
            return new GeocodeResult(Double.parseDouble(m3.group(1)), Double.parseDouble(m3.group(2)));
        }
        return null;
    }

    // 展開短網址 (maps.app.goo.gl / goo.gl) 拿到真正的完整網址
    private String expandShortUrl(String shortUrl) {
        try {
            HttpClient noRedirectClient = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(8))
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(shortUrl))
                    .timeout(Duration.ofSeconds(8)).GET().build();
            HttpResponse<Void> response = noRedirectClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.uri().toString();
        } catch (Exception e) {
            return null;
        }
    }

    public static class DistanceResult {
        public final double distanceKm;
        public final int durationMin;
        public DistanceResult(double distanceKm, int durationMin) {
            this.distanceKm = distanceKm;
            this.durationMin = durationMin;
        }
    }

    /**
     * 呼叫 Distance Matrix API 取得兩點間實際開車距離/時間 (含路況估算)
     * 沒設定 key 或呼叫失敗會回傳 null, 呼叫端要自己 fallback
     */
    public DistanceResult getDrivingDistance(double fromLat, double fromLng, double toLat, double toLng) {
        if (!isConfigured()) return null;

        try {
            String origins = fromLat + "," + fromLng;
            String destinations = toLat + "," + toLng;
            String url = "https://maps.googleapis.com/maps/api/distancematrix/json"
                    + "?origins=" + URLEncoder.encode(origins, StandardCharsets.UTF_8)
                    + "&destinations=" + URLEncoder.encode(destinations, StandardCharsets.UTF_8)
                    + "&mode=driving&language=zh-TW&key=" + apiKey;

            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return null;

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode element = root.path("rows").path(0).path("elements").path(0);
            if (!"OK".equals(element.path("status").asText())) return null;

            double meters = element.path("distance").path("value").asDouble();
            double seconds = element.path("duration").path("value").asDouble();

            return new DistanceResult(meters / 1000.0, (int) Math.round(seconds / 60.0));
        } catch (Exception e) {
            return null; // 呼叫失敗就讓 caller fallback 成直線距離估算
        }
    }

    /**
     * 產生 Static Maps API 的圖片網址 (含所有點位標記 + 連線), 用於看板顯示跟匯出企劃書內嵌圖片
     */
    public String buildStaticMapUrl(List<double[]> points, int width, int height) {
        if (!isConfigured() || points.isEmpty()) return null;

        StringBuilder markers = new StringBuilder();
        StringBuilder path = new StringBuilder("path=color:0x2563ebcc|weight:4");
        for (int i = 0; i < points.size(); i++) {
            double[] p = points.get(i);
            markers.append("&markers=label:").append((char) ('A' + Math.min(i, 25)))
                    .append("|").append(p[0]).append(",").append(p[1]);
            path.append("|").append(p[0]).append(",").append(p[1]);
        }

        return "https://maps.googleapis.com/maps/api/staticmap?size=" + width + "x" + height
                + "&maptype=roadmap" + markers + "&" + path + "&key=" + apiKey;
    }

    /**
     * 下載 Static Map 圖片的位元組內容 (匯出 Word 企劃書內嵌用)
     */
    public byte[] fetchStaticMapImage(String staticMapUrl) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(staticMapUrl))
                .timeout(Duration.ofSeconds(15)).GET().build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("下載地圖圖片失敗 (HTTP " + response.statusCode() + ")");
        }
        return response.body();
    }
}
