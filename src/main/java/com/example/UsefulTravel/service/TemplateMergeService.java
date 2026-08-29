package com.example.UsefulTravel.service;

import com.example.UsefulTravel.DAO.ImageAssetDAO;
import com.example.UsefulTravel.DAO.PoiDAO;
import com.example.UsefulTravel.entity.*;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.xmlbeans.XmlCursor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 「方式一」範本合併引擎：讀取旅行社上傳的 .docx 範本，把佔位符換成真實行程資料，
 * 並把 {{day_start}}...{{day_end}} 之間的區塊依天數複製多份。
 *
 * 範本製作規格（要給旅行社的說明文件另附，見 template-placeholder-spec.md）：
 *   單次替換: {{title}} {{country}} {{days_count}} {{date_range}}
 *   圖片區塊: 一個段落, 內容只放 {{images_block}}
 *   逐日區塊: {{day_start}} ... {{day_end}} 各自獨立一個段落當標記,
 *             中間可以放任意段落/表格, 內容裡可以用 {{day.number}} {{day.title}}
 *             {{day.content}} {{day.meals}} {{day.hotel}} {{day.map}}
 *   逐項目區塊 (放在逐日區塊裡的表格儲存格裡): {{item_start}} ... {{item_end}},
 *             裡面可以用 {{item.name}} {{item.note}} {{item.description}} {{item.route}}
 *
 * 注意: 這個實作直接操作 POI 的底層 CTP/CTTbl 物件來複製區塊, 屬於 Word XML 結構操作,
 * 對排版複雜（跨頁表格、巢狀表格、章節分隔）的範本可能需要再測試調整。
 * 建議每次改動後用 soffice --convert-to pdf 實際看輸出結果。
 */
@Service
public class TemplateMergeService {

    private final ImageAssetDAO imageAssetDAO;
    private final ImageStorageService imageStorageService;
    private final ItineraryService itineraryService;
    private final PoiDAO poiDAO;
    private final GoogleMapsClient googleMapsClient;

    @Autowired
    public TemplateMergeService(ImageAssetDAO imageAssetDAO, ImageStorageService imageStorageService,
                                ItineraryService itineraryService, PoiDAO poiDAO, GoogleMapsClient googleMapsClient) {
        this.imageAssetDAO = imageAssetDAO;
        this.imageStorageService = imageStorageService;
        this.itineraryService = itineraryService;
        this.poiDAO = poiDAO;
        this.googleMapsClient = googleMapsClient;
    }

    // ---------------- 資料結構 ----------------

    public record DayData(int number, String title, String content, String meals, String hotel,
                          List<ItemData> items, ImageData mapImage) {
        Map<String, String> toMap() {
            Map<String, String> m = new HashMap<>();
            m.put("day.number", toChineseNumeral(number));
            m.put("day.title", title == null ? "" : title);
            m.put("day.content", content == null ? "" : content);
            m.put("day.meals", meals == null ? "" : meals);
            m.put("day.hotel", hotel == null ? "" : hotel);
            return m;
        }
    }

    // 一天裡的單一個項目 (景點/亮點等, 不含餐食/住宿), 給範本裡巢狀的 {{item_start}}...{{item_end}} 用
    public record ItemData(String name, String note, String description, String route) {
        Map<String, String> toMap() {
            Map<String, String> m = new HashMap<>();
            m.put("item.name", name == null ? "" : name);
            m.put("item.note", note == null ? "" : note);
            m.put("item.description", description == null ? "" : description);
            m.put("item.route", route == null ? "" : route);
            return m;
        }
    }

    public record ImageData(byte[] bytes, int pictureType, String filename, String caption) {}

    public record TemplateData(Map<String, String> simpleValues, List<DayData> days, List<ImageData> images) {}

    // ---------------- 對外進入點 ----------------

    /**
     * 把範本 + 行程資料合併成最終 .docx 位元組內容
     */
    public byte[] merge(byte[] templateBytes, TemplateData data) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(templateBytes))) {
            replaceAllPlaceholders(doc, data.simpleValues());
            expandDayBlock(doc, data.days());
            insertImagesBlock(doc, data.images());

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        }
    }

    /**
     * 從資料庫組出合併用的 TemplateData（複用 ItineraryService 既有的查詢邏輯）
     * ExportController 呼叫這個, 再丟給 merge()
     *
     * includeImages: 對應匯出勾選視窗的「圖片」選項, false 的話 images 一律回傳空清單,
     * insertImagesBlock 就會直接把 {{images_block}} 段落整段拿掉 (不留空白)
     */
    public TemplateData buildTemplateData(Itinerary itinerary, boolean includeImages, boolean includeRoutes, boolean includeMap) {
        Map<String, String> simple = new HashMap<>();
        simple.put("title", itinerary.getTitle() == null ? "" : itinerary.getTitle());
        simple.put("country", itinerary.getCountry() == null ? "" : itinerary.getCountry());
        simple.put("days_count", String.valueOf(itinerary.getDaysCount()));
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy/MM/dd");
        String dateRange = (itinerary.getStartDate() != null)
                ? itinerary.getStartDate().format(fmt) + " ~ " + itinerary.getEndDate().format(fmt)
                : "";
        simple.put("date_range", dateRange);

        List<DayData> days = new ArrayList<>();
        List<ImageData> images = new ArrayList<>();

        for (ItineraryDay day : itineraryService.getDays(itinerary.getITID())) {
            List<ItineraryItem> items = itineraryService.getItems(day.getIDID());
            List<RouteSegment> routes = includeRoutes ? itineraryService.getRoutes(day.getIDID()) : List.of();

            StringBuilder content = new StringBuilder();
            StringBuilder meals = new StringBuilder();
            String hotel = "";
            List<ItemData> itemList = new ArrayList<>();

            for (ItineraryItem item : items) {
                String name = item.getCustomName() == null ? "" : item.getCustomName();
                // 景點資料庫裡的介紹說明, 有綁定 POI 才會有 (自訂項目沒有對應的資料庫紀錄, 就沒有介紹文字可帶)
                Poi poi = item.getPID() != null ? poiDAO.findById(item.getPID()) : null;
                String description = (poi != null) ? poi.getDescription() : null;

                // 拉車距離/時間 (對應這個項目「離開後前往下一站」的路程), 沒勾「路程」選項就不算
                String routeText = null;
                if (includeRoutes) {
                    routeText = routes.stream()
                            .filter(r -> r.getFromItemId() == item.getIIID())
                            .findFirst()
                            .map(r -> (r.isBacktrack() ? "⚠ 疑似迴頭路 · " : "🚗 ") + "約 " + r.getDistanceKm()
                                    + " 公里，車程約 " + r.getDurationMin() + " 分鐘")
                            .orElse(null);
                }

                switch (item.getItemType() == null ? "" : item.getItemType()) {
                    case "meal" -> {
                        if (!meals.isEmpty()) meals.append("\n"); // 項目之間空一行
                        meals.append(name);
                    }
                    case "hotel" -> hotel = name;
                    default -> {
                        if (!content.isEmpty()) content.append("\n"); // 項目之間空一行, 讀起來不會擠成一團
                        content.append("【").append(typeLabel(item.getItemType())).append("】").append(name);
                        if (item.getNote() != null && !item.getNote().isBlank()) {
                            content.append("　").append(item.getNote());
                        }
                        // 帶入景點資料庫的介紹說明 (另起一行接在標題底下)
                        if (description != null && !description.isBlank()) {
                            content.append("\n").append(description);
                        }
                        if (routeText != null) {
                            content.append("\n").append(routeText);
                        }
                        // 同一個項目也存一份給範本裡的 {{item_start}}...{{item_end}} 逐項目區塊用
                        // (跟上面的 content 聚合字串是兩套獨立寫法, 範本擇一使用即可)
                        itemList.add(new ItemData(name, item.getNote(), description, routeText));
                    }
                }

                // 收集這個項目綁定的圖片, 之後統一插在標題和行程之間, 圖說用項目/景點名稱
                // 沒勾「圖片」選項就整段跳過, 不然就算勾選只勾行程, 圖片還是會被輸出
                if (includeImages && item.getPID() != null) {
                    // 只取這份行程所屬旅行社自己上傳的照片, 共用景點底下別間旅行社的照片不能混進來
                    List<ImageAsset> assets = imageAssetDAO.findByPoi(item.getPID(), itinerary.getAID());
                    if (!assets.isEmpty()) {
                        // 使用者要求圖片預設全部輸出 (看板上縮圖預設都有綠框), 使用者可以點掉某幾張排除
                        // (itinerary_item.excludedImageIds)——沒被排除的全部收進 images, 每張各自成一筆
                        // ImageData, 集合是空的 (沒有互動過) 就等於全部輸出。
                        java.util.Set<Integer> excludedImageIds = item.getExcludedImageIdSet();
                        for (ImageAsset asset : assets) {
                            if (excludedImageIds.contains(asset.getIAID())) continue;
                            try {
                                byte[] bytes = imageStorageService.load(asset.getFilePath());
                                int pictureType = (asset.getContentType() != null && asset.getContentType().contains("png"))
                                        ? XWPFDocument.PICTURE_TYPE_PNG : XWPFDocument.PICTURE_TYPE_JPEG;
                                images.add(new ImageData(bytes, pictureType,
                                        asset.getOriginalFilename() != null ? asset.getOriginalFilename() : "photo",
                                        name));
                            } catch (Exception ignored) {
                                // 單張圖片讀取失敗不擋整份文件, 跳過這張繼續下一張
                            }
                        }
                    }
                }
            }

            ImageData mapImage = includeMap ? buildDayMapImage(items, routes) : null;

            // day.title 改成把當天所有景點項目的名稱串起來, 不用原本常常沒填的 theme 欄位
            String dayTitle = itemList.stream()
                    .map(ItemData::name)
                    .filter(n -> n != null && !n.isBlank())
                    .collect(java.util.stream.Collectors.joining("、"));

            days.add(new DayData(day.getDayNumber(), dayTitle, content.toString(), meals.toString(), hotel,
                    itemList, mapImage));
        }

        return new TemplateData(simple, days, images);
    }

    /**
     * 這一天的景點連線地圖 (Google Static Maps), 沒設定 API key、沒有座標資料、或抓取失敗都回傳 null
     * (呼叫端看到 null 就把 {{day.map}} 那個段落整段拿掉, 不留錯誤訊息或空白)
     */
    private ImageData buildDayMapImage(List<ItineraryItem> items, List<RouteSegment> routes) {
        if (!googleMapsClient.isConfigured()) return null;

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
        if (coords.isEmpty()) return null;

        try {
            String url = googleMapsClient.buildStaticMapUrl(coords, modes, 640, 400);
            byte[] imageBytes = googleMapsClient.fetchStaticMapImage(url);
            return new ImageData(imageBytes, XWPFDocument.PICTURE_TYPE_PNG, "map.png", null);
        } catch (Exception e) {
            return null; // 抓失敗就跳過這天的地圖, 不擋整份文件產出
        }
    }

    // 把 1, 2, 3... 轉成 一, 二, 三... 給 {{day.number}} 用 (支援到 99 天, 行程夠用了)
    private static String toChineseNumeral(int n) {
        String[] digits = {"", "一", "二", "三", "四", "五", "六", "七", "八", "九"};
        if (n <= 0) return String.valueOf(n); // 理論上不會發生, 保底
        if (n < 10) return digits[n];
        if (n == 10) return "十";
        if (n < 20) return "十" + digits[n % 10];
        int tens = n / 10, ones = n % 10;
        return digits[tens] + "十" + (ones > 0 ? digits[ones] : "");
    }

    private String typeLabel(String itemType) {
        if (itemType == null) return "項目";
        return switch (itemType) {
            case "attraction" -> "景點";
            case "transport" -> "交通";
            case "optional" -> "自費";
            case "free_time" -> "自由活動";
            case "highlight" -> "亮點";
            default -> itemType;
        };
    }

    // ---------------- 單次替換 (標題等只出現一次的欄位) ----------------

    private void replaceAllPlaceholders(IBody body, Map<String, String> values) {
        for (XWPFParagraph p : body.getParagraphs()) {
            replaceInParagraph(p, values);
        }
        for (XWPFTable table : body.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    replaceAllPlaceholders(cell, values); // XWPFTableCell 也是 IBody, 遞迴處理巢狀表格
                }
            }
        }
    }

    // XWPFTable 本身沒有實作 IBody (只有 XWPFDocument 跟 XWPFTableCell 有),
    // 所以表格要另外走這個方法, 逐格丟進 replaceAllPlaceholders(IBody, ...)
    private void replaceInTable(XWPFTable table, Map<String, String> values) {
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                replaceAllPlaceholders(cell, values);
            }
        }
    }

    // 只匹配 {{xxx}} 這種標記本身的樣式 (英數字/底線/句點), 用來偵測有沒有標記被拆在好幾個 run 裡
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\{\\{[a-zA-Z0-9_.]+\\}\\}");

    private void replaceInParagraph(XWPFParagraph paragraph, Map<String, String> values) {
        // Word 常常會把同一個 {{...}} 標記拆成好幾個 <w:r> (打字/自動校正/反覆修改造成的),
        // 肉眼看起來是完整一行字, 但底層 XML 早就四分五裂。如果只逐一檢查每個 run 自己的文字,
        // 被拆開的標記會完全比對不到、抓不到、不會被替換。所以要先把「剛好被拆散、拼起來才是
        // 完整標記」的那幾個 run 合併回一個 (用第一個 run 的格式), 其他 run 完全不去動,
        // 這樣才不會把同一行裡「另一個顏色不同的標記」的格式也一起蓋掉。
        mergeSplitTokens(paragraph);

        // 改成逐一檢查「每個 run 自己的文字」分別替換, 不要把整段合併成一個 run —
        // 不然像「「{{item.name}}」　{{item.note}}」這種同一行放兩個不同顏色標記的情況,
        // 兩個 run 的文字會被合併進第一個 run, 顏色/粗體等格式也會被第一個 run 蓋掉。
        // 逐 run 處理可以讓每段文字保留它原本的格式。
        for (XWPFRun run : paragraph.getRuns()) {
            String text = run.getText(0);
            if (text == null || text.isEmpty()) continue;

            boolean changed = false;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                String token = "{{" + entry.getKey() + "}}";
                if (text.contains(token)) {
                    text = text.replace(token, entry.getValue());
                    changed = true;
                }
            }
            if (!changed) continue;

            // 值裡面如果有 \n (例如 day.content / day.meals 是好幾個項目串起來的),
            // 不能直接把 \n 字元塞進 <w:t> — Word 不會把它當換行, 只會整段擠在一起。
            // 要換行必須插入真正的 <w:br/>, 所以這裡逐行 setText + addBreak()。
            String[] lines = text.split("\n", -1);
            run.setText(lines[0], 0);
            for (int i = 1; i < lines.length; i++) {
                run.addBreak();
                run.setText(lines[i]);
            }
        }
    }

    // 掃這個段落的完整文字 (跨所有 run 串起來), 找出橫跨多個 run 的 {{xxx}} 標記, 把牽涉到的那幾個
    // run 合併成一個 (用第一個 run 的格式), 沒被標記橫跨到的 run 完全不動。可能一次合併會讓其他
    // 標記的 run 位置跟著變, 所以合併一個之後重新掃一次, 直到沒有橫跨多個 run 的標記為止。
    private void mergeSplitTokens(XWPFParagraph paragraph) {
        while (true) {
            List<XWPFRun> runs = paragraph.getRuns();
            StringBuilder full = new StringBuilder();
            List<Integer> charToRun = new ArrayList<>();
            for (int i = 0; i < runs.size(); i++) {
                String t = runs.get(i).getText(0);
                if (t == null) t = "";
                for (int c = 0; c < t.length(); c++) charToRun.add(i);
                full.append(t);
            }
            if (full.isEmpty()) return;

            Matcher m = TOKEN_PATTERN.matcher(full.toString());
            int[] spanToFix = null;
            while (m.find()) {
                int startRun = charToRun.get(m.start());
                int endRun = charToRun.get(m.end() - 1);
                if (endRun > startRun) {
                    spanToFix = new int[]{startRun, endRun};
                    break;
                }
            }
            if (spanToFix == null) return; // 沒有被拆開的標記了, 結束

            int startRun = spanToFix[0], endRun = spanToFix[1];
            StringBuilder merged = new StringBuilder();
            for (int r = startRun; r <= endRun; r++) {
                String t = runs.get(r).getText(0);
                if (t != null) merged.append(t);
            }
            runs.get(startRun).setText(merged.toString(), 0);
            for (int r = endRun; r > startRun; r--) {
                paragraph.removeRun(r);
            }
            // 迴圈重來: run 結構變了, 重新掃描確認還有沒有其他被拆開的標記
        }
    }

    // ---------------- 逐日區塊複製 ----------------

    private void expandDayBlock(XWPFDocument doc, List<DayData> days) {
        XWPFParagraph startMarker = findParagraphByExactText(doc, "{{day_start}}");
        XWPFParagraph endMarker = findParagraphByExactText(doc, "{{day_end}}");
        if (startMarker == null || endMarker == null) {
            return; // 範本沒放逐日標記, 不處理 (可能是整份都是單次替換的簡單範本)
        }

        int startPos = doc.getPosOfParagraph(startMarker);
        int endPos = doc.getPosOfParagraph(endMarker);
        if (startPos < 0 || endPos < 0 || endPos <= startPos) return;

        List<IBodyElement> blockTemplate = new ArrayList<>(doc.getBodyElements().subList(startPos + 1, endPos));
        if (blockTemplate.isEmpty()) return;

        // 把每一天的內容, 依範本區塊的內容順序複製插入到 end marker 之前
        for (DayData day : days) {
            Map<String, String> dayValues = day.toMap();
            List<IBodyElement> newDayElements = new ArrayList<>();
            for (IBodyElement el : blockTemplate) {
                if (el instanceof XWPFParagraph templateP) {
                    XmlCursor cursor = endMarker.getCTP().newCursor();
                    XWPFParagraph newP = doc.insertNewParagraph(cursor);
                    cursor.dispose();
                    newP.getCTP().set(templateP.getCTP().copy());
                    // newP 的內部 runs 清單是複製「之前」的舊快取, 一定要重新包一個 wrapper
                    // 才會正確反映剛複製進去的內容 (XWPFParagraph 的 runs 是建構時就讀好存住, 不會動態重讀)
                    newDayElements.add(new XWPFParagraph(newP.getCTP(), doc));
                } else if (el instanceof XWPFTable templateTbl) {
                    XmlCursor cursor = endMarker.getCTP().newCursor();
                    XWPFTable newTbl = doc.insertNewTbl(cursor);
                    cursor.dispose();
                    newTbl.getCTTbl().set(templateTbl.getCTTbl().copy());
                    newDayElements.add(new XWPFTable(newTbl.getCTTbl(), doc));
                }
            }

            // 先展開巢狀的逐項目區塊 ({{item_start}}...{{item_end}}, 通常放在表格儲存格裡)。
            // 展開完之後這個表格的內部快取 (rows/cells 清單) 可能跟實際 XML 對不上了 (新增/刪除段落
            // 是直接操作底層 XML, 不會自動同步 wrapper 物件的快取), 所以要重新包一個乾淨的 wrapper,
            // 不然後面搜尋 {{day.map}} 會踩到已經被移除的舊段落物件, 丟 XmlValueDisconnectedException。
            List<IBodyElement> refreshedDayElements = new ArrayList<>();
            for (IBodyElement el : newDayElements) {
                if (el instanceof XWPFTable t) {
                    expandItemBlockInTable(t, day.items());
                    refreshedDayElements.add(new XWPFTable(t.getCTTbl(), doc));
                } else {
                    refreshedDayElements.add(el);
                }
            }

            // 每天的地圖圖片 ({{day.map}}) 也要在下面的一般單次替換「之前」處理,
            // 不然 {{day.number}} 之類的替換會把整個儲存格文字打散, day.map 就找不到了
            insertDayMapMarker(refreshedDayElements, day.mapImage());

            // insertDayMapMarker 一樣會直接動底層 XML (插入地圖段落、用 removeXml 移除 {{day.map}} 標記),
            // 所以表格 wrapper 的快取又過期了一次, 要再刷新一次才能安全做最後的一般替換,
            // 不然會跟剛剛同一種 XmlValueDisconnectedException 一樣的問題再發生一次。
            List<IBodyElement> finalDayElements = new ArrayList<>();
            for (IBodyElement el : refreshedDayElements) {
                if (el instanceof XWPFTable t) {
                    finalDayElements.add(new XWPFTable(t.getCTTbl(), doc));
                } else {
                    finalDayElements.add(el);
                }
            }

            // 最後才做這一天份的一般單次替換 ({{day.number}} / {{day.title}} / {{day.content}} 等)
            for (IBodyElement el : finalDayElements) {
                if (el instanceof XWPFParagraph p) {
                    replaceInParagraph(p, dayValues);
                } else if (el instanceof XWPFTable t) {
                    replaceInTable(t, dayValues);
                }
            }
        }

        // 清掉原本的範本區塊本體跟頭尾兩個標記段落 (由後往前刪, 避免位置跑掉)
        removeElement(doc, endMarker);
        for (int i = blockTemplate.size() - 1; i >= 0; i--) {
            removeElement(doc, blockTemplate.get(i));
        }
        removeElement(doc, startMarker);
    }

    // ---------------- 圖片區塊 (標題和行程之間, 每張圖片附名稱) ----------------

    private void insertImagesBlock(XWPFDocument doc, List<ImageData> images) {
        XWPFParagraph marker = findParagraphByExactText(doc, "{{images_block}}");
        if (marker == null || images.isEmpty()) {
            if (marker != null) removeElement(doc, marker); // 沒有圖片就把佔位段落清掉, 不留空白標記
            return;
        }

        for (ImageData img : images) {
            XmlCursor picCursor = marker.getCTP().newCursor();
            XWPFParagraph picP = doc.insertNewParagraph(picCursor);
            picCursor.dispose();
            picP.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun picRun = picP.createRun();
            try {
                picRun.addPicture(new ByteArrayInputStream(img.bytes()), img.pictureType(),
                        img.filename(), Units.toEMU(320), Units.toEMU(210));
            } catch (Exception e) {
                picRun.setText("[圖片載入失敗：" + img.caption() + "]");
            }

            XmlCursor capCursor = marker.getCTP().newCursor();
            XWPFParagraph capP = doc.insertNewParagraph(capCursor);
            capCursor.dispose();
            capP.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun capRun = capP.createRun();
            capRun.setText(img.caption());
            capRun.setFontSize(9);
            capRun.setColor("64748B");
        }

        removeElement(doc, marker);
    }

    // ---------------- 小工具 ----------------

    private XWPFParagraph findParagraphByExactText(XWPFDocument doc, String text) {
        for (XWPFParagraph p : doc.getParagraphs()) {
            if (text.equals(p.getText() == null ? null : p.getText().trim())) {
                return p;
            }
        }
        return null;
    }

    private void removeElement(XWPFDocument doc, IBodyElement el) {
        if (el instanceof XWPFParagraph p) {
            int pos = doc.getPosOfParagraph(p);
            if (pos >= 0) doc.removeBodyElement(pos);
        } else if (el instanceof XWPFTable t) {
            int pos = doc.getPosOfTable(t);
            if (pos >= 0) doc.removeBodyElement(pos);
        }
    }

    // ---------------- 逐日區塊「裡面」的巢狀內容: 逐項目區塊 + 每日地圖 ----------------
    // 這兩個都可能長在表格儲存格裡 (XWPFTableCell), 不是文件最上層, 所以不能沿用
    // doc.getPosOfParagraph()/doc.removeBodyElement() (那兩個是 XWPFDocument 專屬的方法)。
    // 改用 IBody 通用寫法: IBodyElement.getBody() 可以拿到它所在的 body (不管是 doc 還是儲存格),
    // 移除則直接對底層 XML 節點做 XmlCursor.removeXml(), 這個不管在哪一層 body 裡都能用。

    /**
     * 在指定的元素清單裡 (可能包含表格), 遞迴找出文字完全等於 text 的段落。
     * 只會往「表格儲存格」裡面找, 不會找表格以外更深的結構。
     */
    private XWPFParagraph findParagraphRecursive(List<IBodyElement> elements, String text) {
        for (IBodyElement el : elements) {
            if (el instanceof XWPFParagraph p) {
                if (text.equals(p.getText() == null ? null : p.getText().trim())) return p;
            } else if (el instanceof XWPFTable t) {
                for (XWPFTableRow row : t.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        XWPFParagraph found = findParagraphRecursive(cell.getBodyElements(), text);
                        if (found != null) return found;
                    }
                }
            }
        }
        return null;
    }

    // 通用版移除, 直接砍底層 XML 節點, 不管這個元素是在文件最上層還是表格儲存格裡都能用
    private void removeElementAnywhere(IBodyElement el) {
        if (el instanceof XWPFParagraph p) {
            IBody parent = p.getBody();
            if (parent != null && parent.getBodyElements().size() <= 1) {
                // 這是它所在 body (通常是表格儲存格) 目前「唯一」的內容, 直接刪掉會讓儲存格
                // 變成零段落 — Word 規格要求每個儲存格至少要有一個段落, 違反就會被判定成文件毀損,
                // 開啟時跳出「找到無法讀取的內容」。改成清空文字、留一個空段落, 安全得多。
                clearParagraphText(p);
                return;
            }
            XmlCursor c = p.getCTP().newCursor();
            c.removeXml();
            c.dispose();
        } else if (el instanceof XWPFTable t) {
            XmlCursor c = t.getCTTbl().newCursor();
            c.removeXml();
            c.dispose();
        }
    }

    private void clearParagraphText(XWPFParagraph p) {
        int runCount = p.getRuns().size();
        for (int i = runCount - 1; i >= 0; i--) {
            p.removeRun(i);
        }
    }

    // 找出範本裡指定文字的段落 (只找這個 body 底下「直接」的段落, 不含它自己表格裡更深的內容)
    private XWPFParagraph findParagraphByExactText(IBody body, String text) {
        for (XWPFParagraph p : body.getParagraphs()) {
            if (text.equals(p.getText() == null ? null : p.getText().trim())) return p;
        }
        return null;
    }

    /**
     * 通用的「範本區塊依清單重複」邏輯 — expandDayBlock 複製每一天是這個概念的手動版本 (那段是已經測過在用的,
     * 沒有改成呼叫這個, 降低風險), 這裡是給巢狀在儲存格裡的逐項目區塊 ({{item_start}}...{{item_end}}) 用的通用版,
     * 因為 body 可能是 doc 也可能是 XWPFTableCell, 兩者都實作 IBody, 用同一套邏輯就能共用。
     */
    private void expandRepeatingBlock(IBody body, String startTag, String endTag, List<Map<String, String>> rows) {
        XWPFParagraph startMarker = findParagraphByExactText(body, startTag);
        XWPFParagraph endMarker = findParagraphByExactText(body, endTag);
        if (startMarker == null || endMarker == null) return;

        List<IBodyElement> all = body.getBodyElements();
        int startIdx = all.indexOf(startMarker);
        int endIdx = all.indexOf(endMarker);
        if (startIdx < 0 || endIdx < 0 || endIdx <= startIdx) return;

        List<IBodyElement> blockTemplate = new ArrayList<>(all.subList(startIdx + 1, endIdx));

        if (!blockTemplate.isEmpty()) {
            for (Map<String, String> rowValues : rows) {
                for (IBodyElement el : blockTemplate) {
                    if (el instanceof XWPFParagraph templateP) {
                        XmlCursor cursor = endMarker.getCTP().newCursor();
                        XWPFParagraph newP = body.insertNewParagraph(cursor);
                        cursor.dispose();
                        newP.getCTP().set(templateP.getCTP().copy());
                        replaceInParagraph(new XWPFParagraph(newP.getCTP(), body), rowValues);
                    } else if (el instanceof XWPFTable templateTbl) {
                        XmlCursor cursor = endMarker.getCTP().newCursor();
                        XWPFTable newTbl = body.insertNewTbl(cursor);
                        cursor.dispose();
                        newTbl.getCTTbl().set(templateTbl.getCTTbl().copy());
                        replaceInTable(new XWPFTable(newTbl.getCTTbl(), body), rowValues);
                    }
                }
            }
        }

        removeElementAnywhere(endMarker);
        for (int i = blockTemplate.size() - 1; i >= 0; i--) {
            removeElementAnywhere(blockTemplate.get(i));
        }
        removeElementAnywhere(startMarker);
    }

    // 在這一天的表格裡找 {{item_start}}...{{item_end}}, 有放才展開 (沒放就完全不影響, 保留原本 day.content 的用法)
    private void expandItemBlockInTable(XWPFTable table, List<ItemData> items) {
        List<Map<String, String>> rows = new ArrayList<>();
        for (ItemData item : items) rows.add(item.toMap());

        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                expandRepeatingBlock(cell, "{{item_start}}", "{{item_end}}", rows);
            }
        }
    }

    // 在這一天的內容裡找 {{day.map}} 這個段落, 換成地圖圖片 (沒設定地圖或抓取失敗就整段拿掉, 不留錯誤訊息)
    private void insertDayMapMarker(List<IBodyElement> dayElements, ImageData mapImage) {
        XWPFParagraph marker = findParagraphRecursive(dayElements, "{{day.map}}");
        if (marker == null) return;

        if (mapImage == null) {
            removeElementAnywhere(marker);
            return;
        }

        IBody body = marker.getBody();
        XmlCursor cursor = marker.getCTP().newCursor();
        XWPFParagraph picP = body.insertNewParagraph(cursor);
        cursor.dispose();
        picP.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = picP.createRun();
        try {
            run.addPicture(new ByteArrayInputStream(mapImage.bytes()), mapImage.pictureType(),
                    mapImage.filename(), Units.toEMU(320), Units.toEMU(200));
        } catch (Exception e) {
            run.setText("[地圖載入失敗]");
        }
        removeElementAnywhere(marker);
    }
}