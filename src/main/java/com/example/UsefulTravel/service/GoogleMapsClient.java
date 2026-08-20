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
        public final String formattedAddress; // Google 回傳的完整地址文字, 查不到時為 null
        public GeocodeResult(double latitude, double longitude) {
            this(latitude, longitude, null);
        }
        public GeocodeResult(double latitude, double longitude, String formattedAddress) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.formattedAddress = formattedAddress;
        }
    }

    /**
     * 把地名/地址轉成經緯度 (AI 匯入新增 POI 時自動呼叫, 不然地圖畫不出來)
     * 沒設定 key、地址空白、或查不到結果都回傳 null, 呼叫端要自己處理 (通常就是留空經緯度)
     */
    public GeocodeResult geocode(String address) {
        return geocode(address, null);
    }

    /**
     * @param countryName 用中文/英文國家名限定搜尋範圍 (例如「日本」「台灣」「不丹」), 大幅提升準確度。
     *                     沒有對應到已知代碼就不加限制, 退回全球搜尋 (跟舊版行為一樣)
     */
    public GeocodeResult geocode(String address, String countryName) {
        if (!isConfigured() || address == null || address.isBlank()) return null;

        try {
            String url = "https://maps.googleapis.com/maps/api/geocode/json"
                    + "?address=" + URLEncoder.encode(address, StandardCharsets.UTF_8)
                    + "&language=zh-TW";

            String countryCode = COUNTRY_NAME_TO_CODE.get(normalizeCountryName(countryName));
            if (countryCode != null) {
                url += "&components=" + URLEncoder.encode("country:" + countryCode, StandardCharsets.UTF_8);
            }
            url += "&key=" + apiKey;

            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return null;

            JsonNode root = objectMapper.readTree(response.body());
            if (!"OK".equals(root.path("status").asText())) return null;

            JsonNode result = root.path("results").path(0);
            JsonNode location = result.path("geometry").path("location");
            if (location.isMissingNode()) return null;

            String formattedAddress = result.path("formatted_address").asText(null);
            return new GeocodeResult(location.path("lat").asDouble(), location.path("lng").asDouble(), formattedAddress);
        } catch (Exception e) {
            return null; // 地理編碼失敗不影響主流程, 讓 POI 先留空經緯度就好
        }
    }

    /**
     * 只需要地址文字時用這個 (例如「交通」項目的起始/目的地地址沒填, 依名稱自動查詢帶入):
     * 優先用 Places API (對店名/景點名準確率較高), 查不到再退回一般地址查詢。
     * 沒設定 API Key、名稱空白、或都查不到結果會回傳 null, 呼叫端要自己保留原本的空白值。
     */
    public String resolveAddressForName(String name, String countryName) {
        if (!isConfigured() || name == null || name.isBlank()) return null;
        GeocodeResult result = findPlace(name, countryName);
        if (result == null || result.formattedAddress == null) {
            result = geocode(name, countryName);
        }
        return result != null ? result.formattedAddress : null;
    }

    // 常見旅遊目的地國家名 → ISO 3166-1 alpha-2 代碼, 用於限定 Geocoding API 搜尋範圍
    // 這份清單不用完整涵蓋全世界, 涵蓋旅行社常用的目的地就好; 沒對應到的國家就不限制範圍
    private static final java.util.Map<String, String> COUNTRY_NAME_TO_CODE = java.util.Map.ofEntries(
            java.util.Map.entry("台灣", "TW"), java.util.Map.entry("臺灣", "TW"),
            java.util.Map.entry("日本", "JP"), java.util.Map.entry("韓國", "KR"), java.util.Map.entry("南韓", "KR"),
            java.util.Map.entry("中國", "CN"), java.util.Map.entry("香港", "HK"), java.util.Map.entry("澳門", "MO"),
            java.util.Map.entry("泰國", "TH"), java.util.Map.entry("越南", "VN"), java.util.Map.entry("寮國", "LA"),
            java.util.Map.entry("柬埔寨", "KH"), java.util.Map.entry("緬甸", "MM"), java.util.Map.entry("菲律賓", "PH"),
            java.util.Map.entry("馬來西亞", "MY"), java.util.Map.entry("新加坡", "SG"), java.util.Map.entry("印尼", "ID"),
            java.util.Map.entry("印度", "IN"), java.util.Map.entry("不丹", "BT"), java.util.Map.entry("尼泊爾", "NP"),
            java.util.Map.entry("斯里蘭卡", "LK"), java.util.Map.entry("巴基斯坦", "PK"), java.util.Map.entry("孟加拉", "BD"),
            java.util.Map.entry("土耳其", "TR"), java.util.Map.entry("杜拜", "AE"), java.util.Map.entry("阿聯", "AE"),
            java.util.Map.entry("以色列", "IL"), java.util.Map.entry("約旦", "JO"), java.util.Map.entry("埃及", "EG"),
            java.util.Map.entry("摩洛哥", "MA"), java.util.Map.entry("南非", "ZA"), java.util.Map.entry("肯亞", "KE"),
            java.util.Map.entry("英國", "GB"), java.util.Map.entry("法國", "FR"), java.util.Map.entry("德國", "DE"),
            java.util.Map.entry("義大利", "IT"), java.util.Map.entry("西班牙", "ES"), java.util.Map.entry("葡萄牙", "PT"),
            java.util.Map.entry("荷蘭", "NL"), java.util.Map.entry("比利時", "BE"), java.util.Map.entry("瑞士", "CH"),
            java.util.Map.entry("奧地利", "AT"), java.util.Map.entry("希臘", "GR"), java.util.Map.entry("捷克", "CZ"),
            java.util.Map.entry("波蘭", "PL"), java.util.Map.entry("匈牙利", "HU"), java.util.Map.entry("克羅埃西亞", "HR"),
            java.util.Map.entry("冰島", "IS"), java.util.Map.entry("挪威", "NO"), java.util.Map.entry("瑞典", "SE"),
            java.util.Map.entry("丹麥", "DK"), java.util.Map.entry("芬蘭", "FI"), java.util.Map.entry("俄羅斯", "RU"),
            java.util.Map.entry("美國", "US"), java.util.Map.entry("加拿大", "CA"), java.util.Map.entry("墨西哥", "MX"),
            java.util.Map.entry("巴西", "BR"), java.util.Map.entry("阿根廷", "AR"), java.util.Map.entry("秘魯", "PE"),
            java.util.Map.entry("智利", "CL"), java.util.Map.entry("古巴", "CU"),
            java.util.Map.entry("澳洲", "AU"), java.util.Map.entry("紐西蘭", "NZ")
    );

    private String normalizeCountryName(String countryName) {
        if (countryName == null) return "";
        // AI 判斷出的國家有時會帶「、」分隔多國 (例如「印度、不丹」), 只取第一個當主要限定範圍
        String first = countryName.split("[、,/]")[0].trim();
        return first;
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
        return getDistance(fromLat, fromLng, toLat, toLng, "driving");
    }

    /**
     * @param mode "driving" 或 "walking" (跟 Google Distance Matrix API 的 mode 參數一致)
     */
    public DistanceResult getDistance(double fromLat, double fromLng, double toLat, double toLng, String mode) {
        if (!isConfigured()) return null;
        String safeMode = "walking".equalsIgnoreCase(mode) ? "walking" : "driving";

        try {
            String origins = fromLat + "," + fromLng;
            String destinations = toLat + "," + toLng;
            String url = "https://maps.googleapis.com/maps/api/distancematrix/json"
                    + "?origins=" + URLEncoder.encode(origins, StandardCharsets.UTF_8)
                    + "&destinations=" + URLEncoder.encode(destinations, StandardCharsets.UTF_8)
                    + "&mode=" + safeMode + "&language=zh-TW&key=" + apiKey;

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
        return buildStaticMapUrl(points, null, width, height);
    }

    /**
     * @param modes 每一段 (points[i] → points[i+1]) 的交通方式 ("walking"/"driving"), 可以傳 null 或長度不足就全部當開車;
     *              有設定的話會呼叫 Directions API 把「真實沿道路走的路線」畫進靜態地圖 (不再是直線),
     *              跟看板上互動地圖的路線一致。任何一段查不到路線就 fallback 成那一段的直線。
     */
    public String buildStaticMapUrl(List<double[]> points, List<String> modes, int width, int height) {
        return buildStaticMapUrl(points, modes, null, width, height);
    }

    private static final String HOTEL_MARKER_COLOR = "0d9488"; // 住宿固定用這個顏色, 跟其他點區分 (不變)

    /**
     * @param itemTypes 每個點對應的項目類型 (attraction/meal/hotel/transport/...), 可傳 null 代表不特別上色
     *                  (退回全部同一種預設顏色)。一般類型統一用原本的紅色標記; 住宿維持不變, 固定用青色、
     *                  不編字母代號 (前端互動地圖上住宿是用床的 emoji 表示, 但 Google Static Maps API 的
     *                  markers 參數只支援單一英數字當標籤, 沒辦法放 emoji, 所以靜態地圖改成「不放字母、用固定顏色」
     *                  讓住宿仍然一眼可以跟其他點區分開來)。
     */
    public String buildStaticMapUrl(List<double[]> points, List<String> modes, List<String> itemTypes, int width, int height) {
        if (!isConfigured() || points.isEmpty()) return null;

        StringBuilder url = new StringBuilder("https://maps.googleapis.com/maps/api/staticmap?size=" + width + "x" + height
                + "&maptype=roadmap");

        // 每個點的標記, 每個 markers 參數的值要單獨做 URL 編碼 (裡面的 | 字元不編碼會是不合法網址)
        for (int i = 0; i < points.size(); i++) {
            double[] p = points.get(i);
            String itemType = (itemTypes != null && itemTypes.size() > i) ? itemTypes.get(i) : null;
            boolean isHotel = "hotel".equals(itemType);
            String color = isHotel ? ("0x" + HOTEL_MARKER_COLOR) : "red"; // 一般類型統一用原本的紅色標記

            String markerValue = "color:" + color
                    + (isHotel ? "" : "|label:" + (char) ('A' + Math.min(i, 25))) // 住宿不編字母代號
                    + "|" + p[0] + "," + p[1];
            url.append("&markers=").append(URLEncoder.encode(markerValue, StandardCharsets.UTF_8));
        }

        // 每一段路線: 有辦法查到真實路線 (Directions API) 就用真實路線, 查不到才 fallback 成這一段的直線
        for (int i = 0; i < points.size() - 1; i++) {
            String mode = (modes != null && modes.size() > i) ? modes.get(i) : "driving";
            String encodedPolyline = fetchRoutePolyline(points.get(i), points.get(i + 1), mode);

            String pathValue = encodedPolyline != null
                    ? "color:0x2563ebcc|weight:4|enc:" + encodedPolyline
                    : "color:0x2563ebcc|weight:4|" + points.get(i)[0] + "," + points.get(i)[1]
                    + "|" + points.get(i + 1)[0] + "," + points.get(i + 1)[1];
            url.append("&path=").append(URLEncoder.encode(pathValue, StandardCharsets.UTF_8));
        }

        url.append("&key=").append(apiKey);
        return url.toString();
    }

    // 呼叫 Directions API 拿兩點之間「真實沿道路走」的路線, 回傳 Google 的編碼折線字串 (encoded polyline), 查不到回傳 null
    private String fetchRoutePolyline(double[] from, double[] to, String mode) {
        try {
            String safeMode = "walking".equalsIgnoreCase(mode) ? "walking" : "driving";
            String url = "https://maps.googleapis.com/maps/api/directions/json"
                    + "?origin=" + from[0] + "," + from[1]
                    + "&destination=" + to[0] + "," + to[1]
                    + "&mode=" + safeMode + "&key=" + apiKey;

            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return null;

            JsonNode root = objectMapper.readTree(response.body());
            if (!"OK".equals(root.path("status").asText())) return null;

            return root.path("routes").path(0).path("overview_polyline").path("points").asText(null);
        } catch (Exception e) {
            return null; // 查不到就讓呼叫端 fallback 成直線, 不影響整張圖產出
        }
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

    /**
     * 用 Places API 的 Find Place From Text 查詢景點/餐廳名稱, 準確率比 Geocoding API 高很多
     * (Geocoding API 是設計給「地址」用的, 對店名/景點名容易抓錯)
     */
    public GeocodeResult findPlace(String query, String countryName) {
        if (!isConfigured() || query == null || query.isBlank()) return null;
        try {
            String url = "https://maps.googleapis.com/maps/api/place/findplacefromtext/json"
                    + "?input=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                    + "&inputtype=textquery&fields=geometry,name,formatted_address"
                    + "&language=zh-TW&key=" + apiKey;

            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return null;

            JsonNode root = objectMapper.readTree(response.body());
            if (!"OK".equals(root.path("status").asText())) return null;

            JsonNode candidate = root.path("candidates").path(0);
            JsonNode location = candidate.path("geometry").path("location");
            if (location.isMissingNode()) return null;

            String formattedAddress = candidate.path("formatted_address").asText(null);
            return new GeocodeResult(location.path("lat").asDouble(), location.path("lng").asDouble(), formattedAddress);
        } catch (Exception e) {
            return null; // 找不到就讓呼叫端 fallback 到 geocode()
        }
    }
}