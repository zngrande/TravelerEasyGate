package com.example.UsefulTravel.service;

import com.example.UsefulTravel.DAO.AiImportDAO;
import com.example.UsefulTravel.DAO.ItineraryDAO;
import com.example.UsefulTravel.DAO.ItineraryDayDAO;
import com.example.UsefulTravel.DAO.ItineraryItemDAO;
import com.example.UsefulTravel.DAO.ItineraryItemOptionDAO;
import com.example.UsefulTravel.DAO.PoiDAO;
import com.example.UsefulTravel.DAO.RouteSegmentDAO;
import com.example.UsefulTravel.entity.Itinerary;
import com.example.UsefulTravel.entity.ItineraryDay;
import com.example.UsefulTravel.entity.ItineraryItem;
import com.example.UsefulTravel.entity.ItineraryItemOption;
import com.example.UsefulTravel.entity.Poi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * ItineraryService - 行程建立與積木式排版的核心跨表邏輯
 */
@Service
public class ItineraryService {

    private final ItineraryDAO itineraryDAO;
    private final ItineraryDayDAO itineraryDayDAO;
    private final ItineraryItemDAO itineraryItemDAO;
    private final ItineraryItemOptionDAO itineraryItemOptionDAO;
    private final RouteSegmentDAO routeSegmentDAO;
    private final RouteService routeService;
    private final PoiDAO poiDAO;
    private final AiImportDAO aiImportDAO;
    private final GoogleMapsClient googleMapsClient;
    private final AnthropicClient anthropicClient;

    @Autowired
    public ItineraryService(ItineraryDAO itineraryDAO, ItineraryDayDAO itineraryDayDAO,
                             ItineraryItemDAO itineraryItemDAO, ItineraryItemOptionDAO itineraryItemOptionDAO,
                             RouteSegmentDAO routeSegmentDAO,
                             RouteService routeService, PoiDAO poiDAO, AiImportDAO aiImportDAO,
                             GoogleMapsClient googleMapsClient, AnthropicClient anthropicClient) {
        this.itineraryDAO = itineraryDAO;
        this.itineraryDayDAO = itineraryDayDAO;
        this.itineraryItemDAO = itineraryItemDAO;
        this.itineraryItemOptionDAO = itineraryItemOptionDAO;
        this.routeSegmentDAO = routeSegmentDAO;
        this.routeService = routeService;
        this.poiDAO = poiDAO;
        this.aiImportDAO = aiImportDAO;
        this.googleMapsClient = googleMapsClient;
        this.anthropicClient = anthropicClient;
    }

    /**
     * 建立新行程, 依 daysCount 自動產生 Day1 ~ DayN 空白骨架
     */
    public Itinerary createItinerary(int AID, int createdBy, String title, String country,
                                      int daysCount, LocalDate startDate) {
        return createItinerary(AID, createdBy, title, country, null, daysCount, startDate);
    }

    public Itinerary createItinerary(int AID, int createdBy, String title, String country, String region,
                                      int daysCount, LocalDate startDate) {
        Itinerary itinerary = new Itinerary(AID, createdBy, title, country, daysCount);
        itinerary.setRegion(region);
        itinerary.setStartDate(startDate);
        if (startDate != null) {
            itinerary.setEndDate(startDate.plusDays(daysCount - 1));
        }
        itineraryDAO.save(itinerary);

        for (int d = 1; d <= daysCount; d++) {
            LocalDate dayDate = startDate != null ? startDate.plusDays(d - 1) : null;
            itineraryDayDAO.save(new ItineraryDay(itinerary.getITID(), d, dayDate, null));
        }
        return itinerary;
    }

    public List<ItineraryDay> getDays(int ITID) {
        return itineraryDayDAO.findByItinerary(ITID);
    }

    public Itinerary getItinerary(int ITID) {
        return itineraryDAO.findById(ITID);
    }

    public void deleteItinerary(int ITID) {
        aiImportDAO.clearResultItinerary(ITID); // 先解除外鍵參照, 不然刪除會被擋
        // 同樣道理: route_segment 對 itinerary_item 的外鍵沒設 CASCADE,
        // 要先把這個行程底下每一天算過的拉車距離快取清掉, 不然整串 CASCADE 刪除會在 itinerary_item 這關被擋住
        for (ItineraryDay day : itineraryDayDAO.findByItinerary(ITID)) {
            routeSegmentDAO.deleteByDay(day.getIDID());
        }
        itineraryDAO.deleteById(ITID);
    }

    // 更新某一天的出發時間 (時間軸看板的起點, 例如「早上7點出發」)
    public void updateDayStartTime(int IDID, java.time.LocalTime startTime) {
        ItineraryDay day = itineraryDayDAO.findById(IDID);
        if (day != null) {
            day.setStartTime(startTime);
            itineraryDayDAO.save(day);
        }
    }

    // 切換這天的交通方式 (開車/走路), 切換後要重新算一次拉車時間, 不然還是舊方式的數字
    public void updateDayTransportMode(int IDID, String transportMode) {
        ItineraryDay day = itineraryDayDAO.findById(IDID);
        if (day != null) {
            day.setTransportMode("walking".equalsIgnoreCase(transportMode) ? "walking" : "driving");
            itineraryDayDAO.save(day);
            recalculateRoutes(IDID);
        }
    }

    public List<ItineraryItem> getItems(int IDID) {
        return itineraryItemDAO.findByDay(IDID);
    }

    // 給看板顯示「兩點之間拉車距離/時間」與迴頭路警示用
    public List<com.example.UsefulTravel.entity.RouteSegment> getRoutes(int IDID) {
        return routeSegmentDAO.findByDay(IDID);
    }

    // 更新這個行程匯出企劃書時要套用的模板風格 (wenqing/luxury/corporate/default)
    public void updateTemplateStyle(int ITID, String style) {
        Itinerary itinerary = itineraryDAO.findById(ITID);
        if (itinerary != null) {
            itinerary.setTemplateStyle(style);
            itineraryDAO.save(itinerary);
        }
    }

    /**
     * 把排版看板上「還沒連結 POI」的項目 (item.PID == null, 通常是手動打字加的自訂項目)
     * 寫進公司 POI 資料庫 (時間預設 NULL), 寫完後自動把這個項目連結到新建立的 POI
     *
     * @return 新建立的 Poi
     */
    public Poi addItemToPoi(int AID, int IIID) {
        ItineraryItem item = itineraryItemDAO.findById(IIID);
        if (item == null) throw new IllegalArgumentException("找不到這個項目");
        if (item.getPID() != null) throw new IllegalStateException("這個項目已經連結 POI 資料庫了");

        String category = mapItemTypeToPoiCategory(item.getItemType());
        if (category == null) {
            throw new IllegalArgumentException("「" + typeDisplayName(item.getItemType()) + "」不是景點/餐廳/住宿類型，無法加入 POI 資料庫");
        }

        // 優先用這個項目自己判斷的國家/地區 (更精確, 例如多國行程裡每個景點不同國家)
        // 項目自己沒有的話才 fallback 用行程層級的國家/地區 (取第一個 token, 避免「印度、不丹」這種合併字串整包存進去)
        String country = item.getItemCountry(), region = item.getItemRegion();
        if (country == null) {
            ItineraryDay day = itineraryDayDAO.findById(item.getIDID());
            if (day != null) {
                Itinerary parentItinerary = itineraryDAO.findById(day.getITID());
                if (parentItinerary != null) {
                    country = firstToken(parentItinerary.getCountry());
                    region = region != null ? region : firstToken(parentItinerary.getRegion());
                }
            }
        }

        Poi poi = new Poi(AID, category, item.getCustomName(), country, region, null, null, null);

        // 停留時間估算跟「補查座標」(如果項目自己還沒有座標的話) 平行呼叫, 減少等待時間
        java.util.concurrent.CompletableFuture<Integer> stayFuture =
                java.util.concurrent.CompletableFuture.supplyAsync(
                        () -> anthropicClient.estimateStayMinutes(item.getCustomName(), category, null));

        // 這個項目自己如果已經有座標 (自訂項目/已選好的選項/剛編輯過) 就直接沿用, 比重新查名稱準確
        if (item.getLatitude() != null && item.getLongitude() != null) {
            poi.setLatitude(item.getLatitude());
            poi.setLongitude(item.getLongitude());
        } else {
            String geocodeQuery = String.join(" ",
                    item.getCustomName() != null ? item.getCustomName() : "",
                    region != null ? region : "",
                    country != null ? country : "").trim();
            GoogleMapsClient.GeocodeResult geo = googleMapsClient.geocode(geocodeQuery, country);
            if (geo != null) {
                poi.setLatitude(java.math.BigDecimal.valueOf(geo.latitude));
                poi.setLongitude(java.math.BigDecimal.valueOf(geo.longitude));
            }
        }

        poi.setSuggestedStayMin(stayFuture.join());

        poiDAO.save(poi);

        item.setPID(poi.getPID());
        itineraryItemDAO.save(item);

        return poi;
    }

    // AI/手動輸入的 item_type 對應到 POI 資料庫的 category (transport/highlight 不是實體地點, 不能加入)
    private String mapItemTypeToPoiCategory(String itemType) {
        if (itemType == null) return null;
        return switch (itemType) {
            case "attraction" -> "attraction";
            case "meal" -> "restaurant";
            case "hotel" -> "hotel";
            default -> null;
        };
    }

    private String typeDisplayName(String itemType) {
        if (itemType == null) return "此項目";
        return switch (itemType) {
            case "transport" -> "交通";
            case "optional" -> "自費項目";
            case "free_time" -> "自由活動";
            default -> itemType;
        };
    }

    // 避免使用者手動輸入或 AI 判斷出「印度、不丹」這種多國合併字串時整包存進 POI 資料庫,
    // 只取第一個當作主要國家/地區
    private String firstToken(String value) {
        if (value == null) return null;
        String first = value.split("[、,/]")[0].trim();
        return first.isEmpty() ? null : first;
    }

    /**
     * 把一個 POI (或自訂項目) 加到某一天的行程尾端
     */
    public ItineraryItem addItem(int IDID, Integer PID, String itemType, String customName) {
        return addItem(IDID, PID, itemType, customName, null, null, null);
    }

    /**
     * @param stayDurationMin AI 預估的停留時間(分鐘), 沒有就傳 null (時間軸會用預設值)
     */
    public ItineraryItem addItem(int IDID, Integer PID, String itemType, String customName, Integer stayDurationMin) {
        return addItem(IDID, PID, itemType, customName, stayDurationMin, null, null);
    }

    /**
     * @param itemCountry AI 解析時針對這個項目自己判斷出的國家 (比行程層級的國家精確, 例如多國行程)
     * @param itemRegion  AI 解析時針對這個項目自己判斷出的地區/城市
     */
    public ItineraryItem addItem(int IDID, Integer PID, String itemType, String customName, Integer stayDurationMin,
                                  String itemCountry, String itemRegion) {
        List<ItineraryItem> existing = itineraryItemDAO.findByDay(IDID);
        int nextOrder = existing.size();

        ItineraryItem item = new ItineraryItem(IDID, PID, itemType, customName, nextOrder);
        item.setStayDurationMin(stayDurationMin);
        item.setItemCountry(itemCountry);
        item.setItemRegion(itemRegion);

        // 有連結 POI 的話, 直接把座標也複製到項目自己身上 (跟自訂項目走同一套地圖邏輯, 不用每次都查 Poi 表)
        if (PID != null) {
            Poi poi = poiDAO.findById(PID);
            if (poi != null && poi.getLatitude() != null) {
                item.setLatitude(poi.getLatitude());
                item.setLongitude(poi.getLongitude());
            }
        }

        itineraryItemDAO.save(item);

        recalculateRoutes(IDID);
        return item;
    }

    /**
     * 新增「自訂項目」: 不連結公司 POI 資料庫, 但會自動地理編碼取得座標, 讓地圖照樣顯示這個點
     * 支援名稱用「或」分隔多個選項 (例如飯店常見的「A飯店或B飯店或C飯店」), 每個候選都會分別地理編碼,
     * 預設選第一個, 之後可以用 selectItemOption() 切換
     */
    public ItineraryItem addCustomItem(int IDID, String itemType, String rawName, Integer stayDurationMin, String locationHint) {
        String[] candidates = rawName.split("或");
        List<String> names = new ArrayList<>();
        for (String c : candidates) {
            String trimmed = c.trim();
            if (!trimmed.isEmpty()) names.add(trimmed);
        }
        if (names.isEmpty()) names.add(rawName.trim());

        // 拿這個項目所在行程的國家/地區, 讓地理編碼查詢更準確
        String country = null, region = null;
        ItineraryDay day = itineraryDayDAO.findById(IDID);
        if (day != null) {
            Itinerary itinerary = itineraryDAO.findById(day.getITID());
            if (itinerary != null) {
                country = itinerary.getCountry();
                region = itinerary.getRegion();
            }
        }

        List<ItineraryItem> existing = itineraryItemDAO.findByDay(IDID);
        int nextOrder = existing.size();

        ItineraryItem item = new ItineraryItem(IDID, null, itemType, names.get(0), nextOrder);
        item.setStayDurationMin(stayDurationMin);
        itineraryItemDAO.save(item); // 先存起來拿 IIID

        boolean first = true;
        for (String name : names) {
            GoogleMapsClient.GeocodeResult geo = null;

            // 第一個候選點如果有填地點提示 (地址/Google地圖網址), 優先用它定位, 比純名稱查詢準確很多
            if (first && locationHint != null && !locationHint.isBlank()) {
                geo = googleMapsClient.resolveLocationHint(locationHint);
            }
            if (geo == null) {
                String query = String.join(" ", name, region != null ? region : "", country != null ? country : "").trim();
                geo = googleMapsClient.geocode(query, country);
            }

            BigDecimal lat = geo != null ? BigDecimal.valueOf(geo.latitude) : null;
            BigDecimal lng = geo != null ? BigDecimal.valueOf(geo.longitude) : null;

            itineraryItemOptionDAO.save(new ItineraryItemOption(item.getIIID(), name, lat, lng, first));

            if (first) {
                item.setLatitude(lat);
                item.setLongitude(lng);
                first = false;
            }
        }
        itineraryItemDAO.save(item);

        recalculateRoutes(IDID);
        return item;
    }

    /**
     * 切換某個項目要用哪一個候選點 (例如飯店 A或B或C, 這裡選定其中一個), 地圖會跟著更新
     */
    public void selectItemOption(int IIID, int IIOID) {
        List<ItineraryItemOption> options = itineraryItemOptionDAO.findByItem(IIID);
        ItineraryItemOption chosen = options.stream().filter(o -> o.getIIOID() == IIOID).findFirst().orElse(null);
        if (chosen == null) throw new IllegalArgumentException("找不到這個選項");

        itineraryItemOptionDAO.clearSelected(IIID);
        chosen.setSelected(true);
        itineraryItemOptionDAO.save(chosen);

        ItineraryItem item = itineraryItemDAO.findById(IIID);
        if (item != null) {
            item.setCustomName(chosen.getName());
            item.setLatitude(chosen.getLatitude());
            item.setLongitude(chosen.getLongitude());
            itineraryItemDAO.save(item);
            recalculateRoutes(item.getIDID());
        }
    }

    public List<ItineraryItemOption> getItemOptions(int IIID) {
        return itineraryItemOptionDAO.findByItem(IIID);
    }

    /**
     * 編輯排版看板上已存在的項目: 名稱、停留時間、地點提示 (填了會重新定位, 不填就保留原本座標)
     */
    public void updateItemDetails(int IIID, String customName, Integer stayDurationMin, String locationHint) {
        ItineraryItem item = itineraryItemDAO.findById(IIID);
        if (item == null) throw new IllegalArgumentException("找不到這個項目");

        item.setCustomName(customName);
        item.setStayDurationMin(stayDurationMin);

        if (locationHint != null && !locationHint.isBlank()) {
            GoogleMapsClient.GeocodeResult geo = googleMapsClient.resolveLocationHint(locationHint);
            if (geo != null) {
                item.setLatitude(BigDecimal.valueOf(geo.latitude));
                item.setLongitude(BigDecimal.valueOf(geo.longitude));
            }
        }

        itineraryItemDAO.save(item);
        recalculateRoutes(item.getIDID());
    }

    public void removeItem(int IIID, int IDID) {
        // 先清掉這天的路段快取 (route_segment 的外鍵沒設 CASCADE, 有算過拉車距離的項目直接刪會被擋)
        routeSegmentDAO.deleteByDay(IDID);
        itineraryItemDAO.deleteById(IIID);
        recalculateRoutes(IDID);
    }

    /**
     * 拖曳排序後呼叫: orderedItemIds 是前端拖完之後的新順序 (IIID 陣列)
     */
    public void reorderItems(int IDID, List<Integer> orderedItemIds) {
        for (int i = 0; i < orderedItemIds.size(); i++) {
            itineraryItemDAO.updateSortOrder(orderedItemIds.get(i), i);
        }
        recalculateRoutes(IDID);
    }

    /**
     * 排序異動後, 清掉舊的路段快取並重新請 RouteService 算一次
     * (實際的地圖/距離計算邏輯放在 RouteService, 方便未來替換 Google Maps API)
     */
    private void recalculateRoutes(int IDID) {
        routeSegmentDAO.deleteByDay(IDID);
        List<ItineraryItem> items = itineraryItemDAO.findByDay(IDID);
        ItineraryDay day = itineraryDayDAO.findById(IDID);
        String transportMode = day != null && day.getTransportMode() != null ? day.getTransportMode() : "driving";
        routeService.calculateAndSaveSegments(IDID, items, transportMode);
    }

    /**
     * 智慧景點推薦: 找出公司 POI 資料庫裡, 落在「fromItem → toItem」順路範圍內的其他景點/餐廳/休息站
     *
     * 判斷邏輯: 用三角不等式的概念——如果 (from→候選點的距離 + 候選點→to的距離) 跟 (from→to直線距離) 差不多,
     * 代表這個候選點大致落在路徑上, 才算「順路」。 迂迴超過 30% 就不推薦, 避免建議繞遠路的地方。
     */
    public List<com.example.UsefulTravel.entity.Poi> suggestPoiBetween(int AID, int IDID, int fromIIID, int toIIID) {
        ItineraryItem fromItem = itineraryItemDAO.findById(fromIIID);
        ItineraryItem toItem = itineraryItemDAO.findById(toIIID);
        if (fromItem == null || toItem == null || fromItem.getPID() == null || toItem.getPID() == null) {
            return List.of();
        }

        com.example.UsefulTravel.entity.Poi fromPoi = poiDAO.findById(fromItem.getPID());
        com.example.UsefulTravel.entity.Poi toPoi = poiDAO.findById(toItem.getPID());
        if (fromPoi == null || toPoi == null || fromPoi.getLatitude() == null || toPoi.getLatitude() == null) {
            return List.of();
        }

        double fLat = fromPoi.getLatitude().doubleValue(), fLng = fromPoi.getLongitude().doubleValue();
        double tLat = toPoi.getLatitude().doubleValue(), tLng = toPoi.getLongitude().doubleValue();
        double directKm = haversineKm(fLat, fLng, tLat, tLng);

        // 已經在這天的項目不重複推薦
        java.util.Set<Integer> alreadyInDay = itineraryItemDAO.findByDay(IDID).stream()
                .map(ItineraryItem::getPID).filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        return poiDAO.findByAgencyAndCountry(AID, fromPoi.getCountry(), null).stream()
                .filter(p -> p.getLatitude() != null)
                .filter(p -> !alreadyInDay.contains(p.getPID()))
                .filter(p -> p.getPID() != fromPoi.getPID() && p.getPID() != toPoi.getPID())
                .filter(p -> {
                    double cLat = p.getLatitude().doubleValue(), cLng = p.getLongitude().doubleValue();
                    double viaKm = haversineKm(fLat, fLng, cLat, cLng) + haversineKm(cLat, cLng, tLat, tLng);
                    // directKm 太小 (兩點幾乎同位置) 時跳過, 避免除以極小值造成誤判
                    if (directKm < 0.3) return false;
                    return viaKm <= directKm * 1.3;
                })
                .limit(5)
                .collect(java.util.stream.Collectors.toList());
    }

    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
