package com.example.UsefulTravel.service;

import com.example.UsefulTravel.DAO.ItineraryDAO;
import com.example.UsefulTravel.DAO.ItineraryDayDAO;
import com.example.UsefulTravel.DAO.ItineraryItemDAO;
import com.example.UsefulTravel.DAO.PoiDAO;
import com.example.UsefulTravel.DAO.RouteSegmentDAO;
import com.example.UsefulTravel.entity.Itinerary;
import com.example.UsefulTravel.entity.ItineraryDay;
import com.example.UsefulTravel.entity.ItineraryItem;
import com.example.UsefulTravel.entity.Poi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * ItineraryService - 行程建立與積木式排版的核心跨表邏輯
 */
@Service
public class ItineraryService {

    private final ItineraryDAO itineraryDAO;
    private final ItineraryDayDAO itineraryDayDAO;
    private final ItineraryItemDAO itineraryItemDAO;
    private final RouteSegmentDAO routeSegmentDAO;
    private final RouteService routeService;
    private final PoiDAO poiDAO;

    @Autowired
    public ItineraryService(ItineraryDAO itineraryDAO, ItineraryDayDAO itineraryDayDAO,
                             ItineraryItemDAO itineraryItemDAO, RouteSegmentDAO routeSegmentDAO,
                             RouteService routeService, PoiDAO poiDAO) {
        this.itineraryDAO = itineraryDAO;
        this.itineraryDayDAO = itineraryDayDAO;
        this.itineraryItemDAO = itineraryItemDAO;
        this.routeSegmentDAO = routeSegmentDAO;
        this.routeService = routeService;
        this.poiDAO = poiDAO;
    }

    /**
     * 建立新行程, 依 daysCount 自動產生 Day1 ~ DayN 空白骨架
     */
    public Itinerary createItinerary(int AID, int createdBy, String title, String country,
                                      int daysCount, LocalDate startDate) {
        Itinerary itinerary = new Itinerary(AID, createdBy, title, country, daysCount);
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

    // 更新某一天的出發時間 (時間軸看板的起點, 例如「早上7點出發」)
    public void updateDayStartTime(int IDID, java.time.LocalTime startTime) {
        ItineraryDay day = itineraryDayDAO.findById(IDID);
        if (day != null) {
            day.setStartTime(startTime);
            itineraryDayDAO.save(day);
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

        Poi poi = new Poi(AID, category, item.getCustomName(), null, null, null, null, null);
        poi.setSuggestedStayMin(null); // 時間預設 NULL, 之後由線控自己補
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

    /**
     * 把一個 POI (或自訂項目) 加到某一天的行程尾端
     */
    public ItineraryItem addItem(int IDID, Integer PID, String itemType, String customName) {
        return addItem(IDID, PID, itemType, customName, null);
    }

    /**
     * @param stayDurationMin AI 預估的停留時間(分鐘), 沒有就傳 null (時間軸會用預設值)
     */
    public ItineraryItem addItem(int IDID, Integer PID, String itemType, String customName, Integer stayDurationMin) {
        List<ItineraryItem> existing = itineraryItemDAO.findByDay(IDID);
        int nextOrder = existing.size();

        ItineraryItem item = new ItineraryItem(IDID, PID, itemType, customName, nextOrder);
        item.setStayDurationMin(stayDurationMin);
        itineraryItemDAO.save(item);

        recalculateRoutes(IDID);
        return item;
    }

    public void removeItem(int IIID, int IDID) {
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
        routeService.calculateAndSaveSegments(IDID, items);
    }
}
