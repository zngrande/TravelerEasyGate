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

/**
 * 「方式一」範本合併引擎：讀取旅行社上傳的 .docx 範本，把佔位符換成真實行程資料，
 * 並把 {{day_start}}...{{day_end}} 之間的區塊依天數複製多份。
 *
 * 範本製作規格（要給旅行社的說明文件另附，見 template-placeholder-spec.md）：
 *   單次替換: {{title}} {{country}} {{days_count}} {{date_range}}
 *   圖片區塊: 一個段落, 內容只放 {{images_block}}
 *   逐日區塊: {{day_start}} ... {{day_end}} 各自獨立一個段落當標記,
 *             中間可以放任意段落/表格, 內容裡可以用 {{day.number}} {{day.title}}
 *             {{day.content}} {{day.meals}} {{day.hotel}}
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

    @Autowired
    public TemplateMergeService(ImageAssetDAO imageAssetDAO, ImageStorageService imageStorageService,
                                 ItineraryService itineraryService, PoiDAO poiDAO) {
        this.imageAssetDAO = imageAssetDAO;
        this.imageStorageService = imageStorageService;
        this.itineraryService = itineraryService;
        this.poiDAO = poiDAO;
    }

    // ---------------- 資料結構 ----------------

    public record DayData(int number, String title, String content, String meals, String hotel) {
        Map<String, String> toMap() {
            Map<String, String> m = new HashMap<>();
            m.put("day.number", String.valueOf(number));
            m.put("day.title", title == null ? "" : title);
            m.put("day.content", content == null ? "" : content);
            m.put("day.meals", meals == null ? "" : meals);
            m.put("day.hotel", hotel == null ? "" : hotel);
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
    public TemplateData buildTemplateData(Itinerary itinerary, boolean includeImages) {
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

            StringBuilder content = new StringBuilder();
            StringBuilder meals = new StringBuilder();
            String hotel = "";

            for (ItineraryItem item : items) {
                String name = item.getCustomName() == null ? "" : item.getCustomName();
                // 景點資料庫裡的介紹說明, 有綁定 POI 才會有 (自訂項目沒有對應的資料庫紀錄, 就沒有介紹文字可帶)
                Poi poi = item.getPID() != null ? poiDAO.findById(item.getPID()) : null;

                switch (item.getItemType() == null ? "" : item.getItemType()) {
                    case "meal" -> {
                        if (!meals.isEmpty()) meals.append("\n\n"); // 項目之間空一行
                        meals.append(name);
                    }
                    case "hotel" -> hotel = name;
                    default -> {
                        if (!content.isEmpty()) content.append("\n\n"); // 項目之間空一行, 讀起來不會擠成一團
                        content.append("【").append(typeLabel(item.getItemType())).append("】").append(name);
                        if (item.getNote() != null && !item.getNote().isBlank()) {
                            content.append("　").append(item.getNote());
                        }
                        // 帶入景點資料庫的介紹說明 (另起一行接在標題底下)
                        if (poi != null && poi.getDescription() != null && !poi.getDescription().isBlank()) {
                            content.append("\n").append(poi.getDescription());
                        }
                    }
                }

                // 收集這個項目綁定的圖片, 之後統一插在標題和行程之間, 圖說用項目/景點名稱
                // 沒勾「圖片」選項就整段跳過, 不然就算勾選只勾行程, 圖片還是會被輸出
                if (includeImages && item.getPID() != null) {
                    List<ImageAsset> assets = imageAssetDAO.findByPoi(item.getPID());
                    if (!assets.isEmpty()) {
                        ImageAsset asset = assets.get(0);
                        try {
                            byte[] bytes = imageStorageService.load(asset.getFilePath());
                            int pictureType = (asset.getContentType() != null && asset.getContentType().contains("png"))
                                    ? XWPFDocument.PICTURE_TYPE_PNG : XWPFDocument.PICTURE_TYPE_JPEG;
                            images.add(new ImageData(bytes, pictureType,
                                    asset.getOriginalFilename() != null ? asset.getOriginalFilename() : "photo",
                                    name));
                        } catch (Exception ignored) {
                            // 單張圖片讀取失敗不擋整份文件
                        }
                    }
                }
            }

            days.add(new DayData(day.getDayNumber(), day.getTheme(), content.toString(), meals.toString(), hotel));
        }

        return new TemplateData(simple, days, images);
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

    private void replaceInParagraph(XWPFParagraph paragraph, Map<String, String> values) {
        String text = paragraph.getText();
        if (text == null || text.isEmpty()) return;

        boolean changed = false;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String token = "{{" + entry.getKey() + "}}";
            if (text.contains(token)) {
                text = text.replace(token, entry.getValue());
                changed = true;
            }
        }
        if (!changed) return;

        // Word 會把同一句話拆成很多個 <w:r>, 這裡簡化成: 全部清掉、只留第一個 run 放新文字
        // (代價是這段文字的內部格式會統一成第一個 run 的格式, 對佔位符這種短標記通常沒差)
        int runCount = paragraph.getRuns().size();
        for (int i = runCount - 1; i >= 1; i--) {
            paragraph.removeRun(i);
        }
        if (runCount > 0) {
            paragraph.getRuns().get(0).setText(text, 0);
        } else {
            paragraph.createRun().setText(text);
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
            for (IBodyElement el : blockTemplate) {
                if (el instanceof XWPFParagraph templateP) {
                    XmlCursor cursor = endMarker.getCTP().newCursor();
                    XWPFParagraph newP = doc.insertNewParagraph(cursor);
                    cursor.dispose();
                    newP.getCTP().set(templateP.getCTP().copy());
                    replaceInParagraph(new XWPFParagraph(newP.getCTP(), doc), dayValues);
                } else if (el instanceof XWPFTable templateTbl) {
                    XmlCursor cursor = endMarker.getCTP().newCursor();
                    XWPFTable newTbl = doc.insertNewTbl(cursor);
                    cursor.dispose();
                    newTbl.getCTTbl().set(templateTbl.getCTTbl().copy());
                    replaceInTable(new XWPFTable(newTbl.getCTTbl(), doc), dayValues);
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
}
