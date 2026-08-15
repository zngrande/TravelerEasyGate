package com.example.UsefulTravel.service;

import com.example.UsefulTravel.DAO.ExportHistoryDAO;
import com.example.UsefulTravel.DAO.ImageAssetDAO;
import com.example.UsefulTravel.DAO.ItineraryComponentDAO;
import com.example.UsefulTravel.DAO.ItineraryDAO;
import com.example.UsefulTravel.DAO.PoiDAO;
import com.example.UsefulTravel.DAO.TravelComponentDAO;
import com.example.UsefulTravel.entity.*;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * ExportService - 「智慧美編與多格式一鍵輸出」目前先做 Word (.docx)
 *
 * - format = "b2b": 同業版, 含每日行程 + 報價明細表 (成本/售價)
 * - format = "b2c": 客戶版, 只有美化過的行程內容, 完全不顯示任何價格/成本資訊
 *
 * TODO 下一步: PDF 匯出 (需要另外接 HTML→PDF 或 iText, 目前先跳過避免跟 PDFBox 版本衝突)
 */
@Service
public class ExportService {

    /**
     * 匯出時要包含哪些內容區塊, 對應前端的勾選視窗
     */
    public static class ExportOptions {
        public boolean includeItinerary = true; // 每日行程內容 (景點/餐廳/住宿/交通/亮點)
        public boolean includeRoutes = true;    // 拉車距離/時間/迴頭路警示
        public boolean includeMap = true;       // 每日景點連線地圖圖片
        public boolean includeImages = false;   // 景點對應的圖片資源庫照片

        public static ExportOptions all() {
            ExportOptions o = new ExportOptions();
            o.includeImages = true;
            return o;
        }
    }

    private final ItineraryDAO itineraryDAO;
    private final ItineraryService itineraryService;
    private final ItineraryComponentDAO itineraryComponentDAO;
    private final TravelComponentDAO travelComponentDAO;
    private final ExportHistoryDAO exportHistoryDAO;
    private final PoiDAO poiDAO;
    private final GoogleMapsClient googleMapsClient;
    private final ImageAssetDAO imageAssetDAO;
    private final ImageStorageService imageStorageService;

    @Autowired
    public ExportService(ItineraryDAO itineraryDAO, ItineraryService itineraryService,
                         ItineraryComponentDAO itineraryComponentDAO, TravelComponentDAO travelComponentDAO,
                         ExportHistoryDAO exportHistoryDAO, PoiDAO poiDAO, GoogleMapsClient googleMapsClient,
                         ImageAssetDAO imageAssetDAO, ImageStorageService imageStorageService) {
        this.itineraryDAO = itineraryDAO;
        this.itineraryService = itineraryService;
        this.itineraryComponentDAO = itineraryComponentDAO;
        this.travelComponentDAO = travelComponentDAO;
        this.exportHistoryDAO = exportHistoryDAO;
        this.poiDAO = poiDAO;
        this.googleMapsClient = googleMapsClient;
        this.imageAssetDAO = imageAssetDAO;
        this.imageStorageService = imageStorageService;
    }

    /**
     * 產生企劃書 Word 檔的位元組內容, 並記錄一筆 export_history
     */
    public byte[] generateWordDocument(int ITID, String format, Integer generatedByUID, ExportOptions options) throws Exception {
        Itinerary itinerary = itineraryDAO.findById(ITID);
        if (itinerary == null) throw new IllegalArgumentException("找不到這筆行程");

        boolean isB2B = "b2b".equalsIgnoreCase(format);
        StylePalette palette = resolvePalette(itinerary.getTemplateStyle());

        try (XWPFDocument doc = new XWPFDocument()) {
            addTitlePage(doc, itinerary, isB2B, palette);
            if (options.includeItinerary) {
                addDaysContent(doc, itinerary.getITID(), palette, options);
            }
            if (isB2B) {
                addQuoteTable(doc, itinerary.getITID());
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            byte[] bytes = out.toByteArray();

            ExportHistory history = new ExportHistory(
                    ITID, isB2B ? "docx-b2b" : "docx-b2c",
                    null /* 本地下載, 沒有存到雲端儲存空間 */,
                    generatedByUID);
            exportHistoryDAO.save(history);

            return bytes;
        }
    }

    // ---------------- 模板樣式配色 ----------------

    /**
     * 內建版面固定用一種配色/語氣。
     *
     * 原本這裡是依 itinerary.getTemplateStyle() (AI 解析時猜的 wenqing/luxury/corporate/default)
     * 切換四種配色 — 現在旅行社可以自己上傳範本 (TemplateMergeService) 決定風格,
     * 內建版面只是「沒上傳範本時」的陽春 fallback, 不需要再讓 AI 猜風格。
     *
     * AiParseService 那邊目前還是會判斷/寫入 template_style, 先留著沒清掉 (不影響任何輸出結果,
     * 之後如果想順便把那段 AI 判斷邏輯也拿掉, 再另外處理, 這裡不動它)。
     */
    private record StylePalette(String titleColor, String dayHeadingColor, String typeTagColor,
                                String subtitleTone, String b2cTagline) {}

    private StylePalette resolvePalette(String style) {
        return new StylePalette("1E3A8A", "2563EB", "4338CA", "64748B", "為您精心規劃的專屬旅程");
    }

    // ---------------- 內部組版邏輯 ----------------

    private void addTitlePage(XWPFDocument doc, Itinerary itinerary, boolean isB2B, StylePalette palette) {
        XWPFParagraph title = doc.createParagraph();
        title.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun titleRun = title.createRun();
        titleRun.setText(itinerary.getTitle());
        titleRun.setBold(true);
        titleRun.setFontSize(26);
        titleRun.setColor(palette.titleColor());

        XWPFParagraph subtitle = doc.createParagraph();
        subtitle.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun subtitleRun = subtitle.createRun();
        String dateRange = (itinerary.getStartDate() != null)
                ? itinerary.getStartDate() + " ~ " + itinerary.getEndDate()
                : "";
        subtitleRun.setText(itinerary.getCountry() + " · " + itinerary.getDaysCount() + " 天"
                + (dateRange.isBlank() ? "" : " · " + dateRange));
        subtitleRun.setFontSize(13);
        subtitleRun.setColor(palette.subtitleTone());

        XWPFParagraph tag = doc.createParagraph();
        tag.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun tagRun = tag.createRun();
        tagRun.setText(isB2B ? "【同業專用 — 內含報價資訊，請勿外流客戶】" : palette.b2cTagline());
        tagRun.setItalic(true);
        tagRun.setFontSize(11);
        tagRun.setColor(isB2B ? "B91C1C" : "16A34A");

        doc.createParagraph(); // 空行
    }

    private void addDaysContent(XWPFDocument doc, int ITID, StylePalette palette, ExportOptions options) {
        List<ItineraryDay> days = itineraryService.getDays(ITID);

        for (ItineraryDay day : days) {
            XWPFParagraph dayHeading = doc.createParagraph();
            XWPFRun dayRun = dayHeading.createRun();
            dayRun.setText("Day " + day.getDayNumber() + (day.getTheme() != null ? "　" + day.getTheme() : ""));
            dayRun.setBold(true);
            dayRun.setFontSize(16);
            dayRun.setColor(palette.dayHeadingColor());

            List<ItineraryItem> items = itineraryService.getItems(day.getIDID());
            List<RouteSegment> routes = itineraryService.getRoutes(day.getIDID());

            if (items.isEmpty()) {
                XWPFParagraph empty = doc.createParagraph();
                empty.createRun().setText("（尚未安排行程內容）");
            }

            for (ItineraryItem item : items) {
                XWPFParagraph p = doc.createParagraph();
                p.setIndentationLeft(300);

                XWPFRun typeRun = p.createRun();
                typeRun.setText("【" + typeLabel(item.getItemType()) + "】");
                typeRun.setBold(true);
                typeRun.setColor(palette.typeTagColor());

                XWPFRun nameRun = p.createRun();
                nameRun.setText(" " + item.getCustomName());
                nameRun.setFontSize(12);

                if (item.getNote() != null && !item.getNote().isBlank()) {
                    XWPFRun noteRun = p.createRun();
                    noteRun.setText("　" + item.getNote());
                    noteRun.setColor("64748B");
                    noteRun.setFontSize(10);
                }

                // 圖片: 這個項目連結的 POI 如果有綁定圖片資源庫的照片, 插入第一張
                if (options.includeImages && item.getPID() != null) {
                    insertItemImage(doc, item.getPID());
                }

                // 拉車距離: 依勾選決定要不要顯示
                if (options.includeRoutes) {
                    routes.stream()
                            .filter(r -> r.getFromItemId() == item.getIIID())
                            .findFirst()
                            .ifPresent(route -> {
                                XWPFParagraph routeP = doc.createParagraph();
                                routeP.setIndentationLeft(500);
                                XWPFRun routeRun = routeP.createRun();
                                String prefix = route.isBacktrack() ? "⚠ 疑似迴頭路 · " : "🚗 ";
                                routeRun.setText(prefix + "約 " + route.getDistanceKm() + " 公里，車程約 "
                                        + route.getDurationMin() + " 分鐘");
                                routeRun.setFontSize(9);
                                routeRun.setItalic(true);
                                routeRun.setColor("94A3B8");
                            });
                }
            }

            if (options.includeMap) {
                insertDayMapImage(doc, day.getIDID(), items, routes);
            }
            doc.createParagraph(); // 每天結束空一行
        }
    }

    /**
     * 把這一天的景點連線地圖 (Google Static Maps) 插入企劃書, 沒設定 API key 或沒有座標資料就跳過
     */
    /**
     * 插入這個項目連結的 POI 綁定的第一張圖片資源庫照片 (沒有綁定圖片就跳過)
     */
    private void insertItemImage(XWPFDocument doc, int PID) {
        List<com.example.UsefulTravel.entity.ImageAsset> images = imageAssetDAO.findByPoi(PID);
        if (images.isEmpty()) return;

        try {
            com.example.UsefulTravel.entity.ImageAsset image = images.get(0);
            byte[] imageBytes = imageStorageService.load(image.getFilePath());

            int pictureType = (image.getContentType() != null && image.getContentType().contains("png"))
                    ? XWPFDocument.PICTURE_TYPE_PNG : XWPFDocument.PICTURE_TYPE_JPEG;

            XWPFParagraph imgP = doc.createParagraph();
            imgP.setIndentationLeft(500);
            XWPFRun imgRun = imgP.createRun();
            imgRun.addPicture(new ByteArrayInputStream(imageBytes), pictureType,
                    image.getOriginalFilename() != null ? image.getOriginalFilename() : "photo",
                    Units.toEMU(200), Units.toEMU(130));
        } catch (Exception e) {
            // 圖片讀取失敗不影響整份企劃書產出, 靜默跳過
        }
    }

    private void insertDayMapImage(XWPFDocument doc, int IDID, List<ItineraryItem> items, List<RouteSegment> routes) {
        if (!googleMapsClient.isConfigured()) return;

        List<double[]> coords = new ArrayList<>();
        List<String> modes = new ArrayList<>();
        for (ItineraryItem item : items) {
            double[] coord = null;
            if (item.getLatitude() != null && item.getLongitude() != null) {
                coord = new double[]{item.getLatitude().doubleValue(), item.getLongitude().doubleValue()};
            } else if (item.getPID() != null) {
                Poi poi = poiDAO.findById(item.getPID());
                if (poi != null && poi.getLatitude() != null) {
                    coord = new double[]{poi.getLatitude().doubleValue(), poi.getLongitude().doubleValue()};
                }
            }
            if (coord == null) continue;

            coords.add(coord);
            String mode = routes.stream()
                    .filter(r -> r.getFromItemId() == item.getIIID())
                    .findFirst()
                    .map(RouteSegment::getTransportMode)
                    .orElse("driving");
            modes.add(mode);
        }
        if (coords.isEmpty()) return;

        try {
            String url = googleMapsClient.buildStaticMapUrl(coords, modes, 640, 400);
            byte[] imageBytes = googleMapsClient.fetchStaticMapImage(url);

            XWPFParagraph mapP = doc.createParagraph();
            mapP.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun mapRun = mapP.createRun();
            mapRun.addPicture(new ByteArrayInputStream(imageBytes), XWPFDocument.PICTURE_TYPE_PNG,
                    "map.png", Units.toEMU(400), Units.toEMU(250));
        } catch (Exception e) {
            // 不再靜默跳過: 直接在文件裡印出失敗原因, 不然完全看不出來是哪裡出問題
            // (最常見原因: Google Cloud 還沒啟用 Maps Static API, 這是跟 Places/Distance Matrix 分開的權限)
            XWPFParagraph errP = doc.createParagraph();
            XWPFRun errRun = errP.createRun();
            errRun.setText("⚠ 地圖圖片載入失敗：" + (e.getMessage() != null ? e.getMessage() : e.toString())
                    + "（常見原因：Google Cloud 專案還沒啟用 Maps Static API）");
            errRun.setColor("DC2626");
            errRun.setFontSize(9);
            errRun.setItalic(true);
        }
    }

    private String typeLabel(String itemType) {
        if (itemType == null) return "項目";
        return switch (itemType) {
            case "attraction" -> "景點";
            case "meal" -> "餐廳";
            case "hotel" -> "住宿";
            case "transport" -> "交通";
            case "optional" -> "自費";
            case "free_time" -> "自由活動";
            case "highlight" -> "亮點";
            default -> itemType;
        };
    }

    private void addQuoteTable(XWPFDocument doc, int ITID) {
        XWPFParagraph heading = doc.createParagraph();
        XWPFRun headingRun = heading.createRun();
        headingRun.setText("報價明細");
        headingRun.setBold(true);
        headingRun.setFontSize(16);
        headingRun.setColor("B91C1C");

        List<ItineraryComponent> components = itineraryComponentDAO.findByItinerary(ITID);

        if (components.isEmpty()) {
            XWPFParagraph empty = doc.createParagraph();
            empty.createRun().setText("（尚未加入任何報價元件，請到行程管理頁面新增航班/餐食/住宿/自費等元件）");
            return;
        }

        XWPFTable table = doc.createTable(components.size() + 1, 4);
        setCell(table, 0, 0, "項目", true);
        setCell(table, 0, 1, "類型", true);
        setCell(table, 0, 2, "數量", true);
        setCell(table, 0, 3, "單價", true);

        BigDecimal total = BigDecimal.ZERO;
        int rowIndex = 1;
        for (ItineraryComponent ic : components) {
            TravelComponent tc = travelComponentDAO.findById(ic.getCPID());
            BigDecimal unitPrice = ic.getPriceOverride() != null ? ic.getPriceOverride()
                    : (tc != null && tc.getDefaultPrice() != null ? tc.getDefaultPrice() : BigDecimal.ZERO);

            setCell(table, rowIndex, 0, tc != null ? tc.getName() : "（元件已刪除）", false);
            setCell(table, rowIndex, 1, tc != null ? tc.getType() : "", false);
            setCell(table, rowIndex, 2, String.valueOf(ic.getQuantity()), false);
            setCell(table, rowIndex, 3, unitPrice.toPlainString(), false);

            total = total.add(unitPrice.multiply(BigDecimal.valueOf(ic.getQuantity())));
            rowIndex++;
        }

        XWPFParagraph totalP = doc.createParagraph();
        totalP.setAlignment(ParagraphAlignment.RIGHT);
        XWPFRun totalRun = totalP.createRun();
        totalRun.setText("總計：" + total.toPlainString() + " 元");
        totalRun.setBold(true);
        totalRun.setFontSize(13);
    }

    private void setCell(XWPFTable table, int row, int col, String text, boolean header) {
        XWPFTableCell cell = table.getRow(row).getCell(col);
        cell.removeParagraph(0);
        XWPFParagraph p = cell.addParagraph();
        XWPFRun run = p.createRun();
        run.setText(text);
        run.setBold(header);
        if (header) run.setColor("FFFFFF");
        if (header) cell.setColor("2563EB");
    }
}