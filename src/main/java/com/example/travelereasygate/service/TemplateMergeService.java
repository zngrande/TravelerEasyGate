package com.example.travelereasygate.service;

import com.example.travelereasygate.DAO.ImageAssetDAO;
import com.example.travelereasygate.DAO.PoiDAO;
import com.example.travelereasygate.entity.*;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.xmlbeans.XmlCursor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalTime;
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
 *   參考航班摘要 (單次替換, 沒有填去程/回程班機就是空字串):
 *             {{outbound_departure_airport}} {{outbound_arrival_airport}}
 *             {{outbound_departure_time}} {{outbound_arrival_time}}
 *             {{return_departure_airport}} {{return_arrival_airport}}
 *             {{return_departure_time}} {{return_arrival_time}}
 *             (這組欄位是給範本開頭「一眼看整趟行程」用的精簡摘要, 只取整趟去程/回程的頭尾;
 *             如果去程/回程有轉機, 中間每一段轉機航班會完整列在該天的 {{day.title}} 裡,
 *             不會漏掉, 見下面逐日區塊的說明)
 *   圖片區塊: 一個段落, 內容只放 {{images_block}}
 *   逐日區塊: {{day_start}} ... {{day_end}} 各自獨立一個段落當標記,
 *             中間可以放任意段落/表格, 內容裡可以用 {{day.number}} {{day.title}}
 *             {{day.content}} {{day.meals}} {{day.hotel}} {{day.map}}
 *             (day.content/day.meals 不會出現交通/班機項目; 交通/班機 (含所有轉機航段,
 *             不只頭尾) 改顯示在 {{day.title}} 最前面, 後面接這天的景點名稱;
 *             day.hotel 除了住宿名稱, 有填備註的話也會一併帶上;
 *             day.meals 每筆餐食前面會自動加上「早餐／午餐／晚餐」——第一筆早餐、
 *             第二筆午餐、第三筆(以後)晚餐, 不到三筆就只會出現對應的那幾筆)
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
            boolean hasReferenceFlightTokens = hasAnyFlightPlaceholder(doc);
            // 參考航班欄位常被 Word 拆成多個 run，直接合併儲存格內的文字再替換，避免只得到空白。
            replaceFlightPlaceholdersInTables(doc, data.simpleValues());
            replaceAllPlaceholders(doc, data.simpleValues());
            if (!hasReferenceFlightTokens) insertReferenceFlightFallback(doc, data.simpleValues());
            expandDayBlock(doc, data.days());
            insertImagesBlock(doc, data.images());

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        }
    }

    /** 舊版自訂 Word 範本沒有航班佔位符時，仍在每日行程前插入參考航班資料。 */
    private boolean hasAnyFlightPlaceholder(XWPFDocument doc) {
        List<String> tokens = List.of("{{outbound_departure_airport}}", "{{outbound_arrival_airport}}",
                "{{outbound_departure_time}}", "{{outbound_arrival_time}}", "{{return_departure_airport}}",
                "{{return_arrival_airport}}", "{{return_departure_time}}", "{{return_arrival_time}}");
        return bodyHasFlightToken(doc, tokens);
    }

    private boolean bodyHasFlightToken(IBody body, List<String> tokens) {
        for (XWPFParagraph paragraph : body.getParagraphs()) {
            String text = paragraph.getRuns().stream().map(run -> run.getText(0))
                    .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.joining());
            if (tokens.stream().anyMatch(text::contains)) return true;
        }
        for (XWPFTable table : body.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    if (bodyHasFlightToken(cell, tokens)) return true;
                }
            }
        }
        return false;
    }

    private void insertReferenceFlightFallback(XWPFDocument doc, Map<String, String> values) {
        List<String> rows = new ArrayList<>();
        addFlightFallbackRow(rows, "去程", values, "outbound");
        addFlightFallbackRow(rows, "回程", values, "return");
        if (rows.isEmpty()) return;

        // 若自訂範本已經有「參考航班」標題，沿用該區塊，不要再自動多生一個同名標題。
        XWPFParagraph existingHeading = findParagraphContaining(doc, "參考航班");
        XWPFParagraph marker = existingHeading == null ? findParagraphByExactText(doc, "{{images_block}}") : null;
        if (marker == null && existingHeading == null) marker = findParagraphByExactText(doc, "{{day_start}}");
        XWPFParagraph heading = existingHeading;
        if (heading == null && marker != null && doc.getPosOfParagraph(marker) >= 0) {
            XmlCursor cursor = marker.getCTP().newCursor();
            heading = doc.insertNewParagraph(cursor);
            cursor.dispose();
        } else if (heading == null) {
            heading = doc.createParagraph();
        }
        if (existingHeading == null) {
            XWPFRun title = heading.createRun();
            title.setText("參考航班");
            title.setBold(true);
            title.setColor("0369A1");
            title.setFontSize(13);
        } else {
            // 現有標題下的首個段落作為插入點，令航班值出現在範本既有的參考航班區。
            List<XWPFParagraph> paragraphs = doc.getParagraphs();
            int headingIndex = paragraphs.indexOf(existingHeading);
            if (headingIndex >= 0 && headingIndex + 1 < paragraphs.size()) marker = paragraphs.get(headingIndex + 1);
            else marker = null;
        }
        for (String row : rows) {
            XWPFParagraph paragraph;
            if (marker != null && doc.getPosOfParagraph(marker) >= 0) {
                XmlCursor cursor = marker.getCTP().newCursor();
                paragraph = doc.insertNewParagraph(cursor);
                cursor.dispose();
            } else {
                paragraph = doc.createParagraph();
            }
            paragraph.createRun().setText(row);
        }
    }

    private XWPFParagraph findParagraphContaining(XWPFDocument doc, String text) {
        for (XWPFParagraph paragraph : doc.getParagraphs()) {
            String fullText = paragraph.getRuns().stream().map(run -> run.getText(0))
                    .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.joining());
            if (fullText.contains(text)) return paragraph;
        }
        return null;
    }

    private void addFlightFallbackRow(List<String> rows, String direction, Map<String, String> values, String prefix) {
        String from = emptyIfNull(values.get(prefix + "_departure_airport"));
        String to = emptyIfNull(values.get(prefix + "_arrival_airport"));
        String depart = emptyIfNull(values.get(prefix + "_departure_time"));
        String arrive = emptyIfNull(values.get(prefix + "_arrival_time"));
        if (from.isBlank() && to.isBlank() && depart.isBlank() && arrive.isBlank()) return;
        rows.add(direction + "：" + from + " " + depart + " → " + to + " " + arrive);
    }

    // 「參考航班」摘要用的單一航段資料 (見下方蒐集邏輯的說明)。flightNo: 這段航班在看板上填的航班編號
    // (item.getTransportNumber()), 使用者要求要顯示在機場名稱前面 (例如「CX459 高雄小港機場」), 沒填就
    // 只顯示機場名稱。
    private record FlightLeg(LocalDate date, String flightNo, String fromAirport, String toAirport,
                             LocalTime depTime, LocalTime arrTime, String direction) {}

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

        // 「參考航班」摘要: 去程/回程如果有轉機, 會拆成好幾段 (去程班機1/去程班機2...), 範本這裡只留
        // 4 個欄位放去程、4 個放回程 (出發機場/抵達機場/出發時間/抵達時間), 不會逐段列出——採用「整趟
        // 去程」的頭尾: 第一段的出發機場/時間當這趟的出發資訊, 最後一段的抵達機場/時間當這趟的抵達資訊,
        // 轉機當中經過哪些機場不會顯示。依 day/sort_order 掃過去, 同方向遇到後面的段落只更新「迄點」,
        // 第一次遇到才記「起點」。
        FlightLeg outboundFirst = null, outboundLast = null, returnFirst = null, returnLast = null;
        List<FlightLeg> unclassifiedFlights = new ArrayList<>();

        for (ItineraryDay day : itineraryService.getDays(itinerary.getITID())) {
            List<ItineraryItem> items = itineraryService.getItems(day.getIDID());
            List<RouteSegment> routes = includeRoutes ? itineraryService.getRoutes(day.getIDID()) : List.of();

            StringBuilder content = new StringBuilder();
            StringBuilder meals = new StringBuilder();
            String hotel = "";
            List<ItemData> itemList = new ArrayList<>();
            // Patch 90: 使用者要求「交通要顯示在輸出檔案的當天行程標題, 不用顯示在行程內容」——原本
            // case "transport" 只有「飛機」而且有 flightDirection (outbound/return) 標記的項目會被
            // 記錄下來 (放進最上面的「參考航班」摘要, 見下面), 沒有標記方向的交通項目 (例如市區接駁車、
            // 中途轉乘、或沒被判斷成整趟行程去程/回程的班機) 完全被跳過、不會出現在文件的任何地方。
            // 這裡新增一個清單, 把「這一天出現過的每一筆交通/班機項目」名稱通通收集起來 (不分方向、
            // 不分是不是飛機), 之後併進 day.title, 確保「所有航班都要顯示」——連轉機的中間航段也不例外
            // (因為這裡是逐項目收集, 不像下面 outboundFirst/outboundLast 那樣只取頭尾)。
            List<String> transportTitleParts = new ArrayList<>();
            int mealIndex = 0; // 這天第幾個餐食項目 (從1開始), 用來套用「第一餐早餐/第二餐午餐/第三餐(以後)晚餐」的預設規則

            for (ItineraryItem item : items) {
                String name = item.getCustomName() == null ? "" : item.getCustomName();
                // 景點資料庫裡的介紹說明, 有綁定 POI 才會有 (自訂項目沒有對應的資料庫紀錄, 就沒有介紹文字可帶)。
                // 使用者要求: AI 解析上傳文件時如果抓到這個地點的簡介, 暫存在 item.aiDescription 的版本要
                // 優先顯示, 資料庫裡的舊版本次之——只有使用者事後在行程編輯畫面手動存檔改過, 才會真的變成
                // 資料庫版本 (屆時 ai_description 會被清空, 見 PoiService.updateDescription)。
                Poi poi = item.getPID() != null ? poiDAO.findById(item.getPID()) : null;
                String description = (item.getAiDescription() != null && !item.getAiDescription().isBlank())
                        ? item.getAiDescription()
                        : (poi != null ? poi.getDescription() : null);

                // 拉車距離/時間 (對應這個項目「離開後前往下一站」的路程), 沒勾「路程」選項就不算
                String routeText = null;
                if (includeRoutes) {
                    routeText = routes.stream()
                            .filter(r -> r.getFromItemId() == item.getIIID())
                            .findFirst()
                            .map(r -> (r.isBacktrack() ? "⚠ 疑似回頭路 · " : "🚗 ") + "約 " + r.getDistanceKm()
                                    + " 公里，車程約 " + r.getDurationMin() + " 分鐘")
                            .orElse(null);
                }

                switch (item.getItemType() == null ? "" : item.getItemType()) {
                    case "meal" -> {
                        // 使用者要求「預設第一餐早餐、第二餐午餐、第三餐晚餐, 不到三餐就排到該餐就好」——
                        // 有明確標記時段 (autoArrangeDay() 標的早/中/晚餐) 就優先採用, 沒有標記才退回
                        // 「這天第幾個出現的餐食」預設規則。
                        mealIndex++;
                        String mealLabel = switch (item.getTimeSlot() == null ? "" : item.getTimeSlot()) {
                            case "breakfast" -> "早餐";
                            case "lunch" -> "午餐";
                            case "dinner" -> "晚餐";
                            default -> mealIndex == 1 ? "早餐" : (mealIndex == 2 ? "午餐" : "晚餐");
                        };
                        if (!meals.isEmpty()) meals.append("\n"); // 項目之間空一行
                        meals.append(mealLabel).append("：").append(name);
                    }
                    case "hotel" -> {
                        // Patch 90: 使用者要求「輸出時住宿的備注也要一起輸出」——原本這裡只存住宿名稱,
                        // item.getNote() 完全沒被用到; 跟下面 default 分支 (景點等一般項目) 同樣的做法,
                        // 有備註就接在名稱後面一起輸出。
                        hotel = name;
                        if (item.getNote() != null && !item.getNote().isBlank()) {
                            hotel = hotel + "　" + item.getNote();
                        }
                    }
                    case "transport" -> {
                        // 航班記入上方 outbound_*/return_*「參考航班」摘要；一般交通項目名稱仍放進當日標題。
                        if (isFlightTransport(item)) {
                            FlightLeg leg = new FlightLeg(day.getDayDate(), item.getTransportNumber(),
                                    item.getFromLocation(), item.getToLocation(),
                                    item.getStartTime(), item.getEndTime(), item.getFlightDirection());
                            if ("outbound".equalsIgnoreCase(item.getFlightDirection())) {
                                if (outboundFirst == null) outboundFirst = leg;
                                outboundLast = leg;
                            } else if ("return".equalsIgnoreCase(item.getFlightDirection())) {
                                if (returnFirst == null) returnFirst = leg;
                                returnLast = leg;
                            } else {
                                // 舊資料或手動建立的航班可能沒有方向欄位，仍列入上方參考航班摘要。
                                unclassifiedFlights.add(leg);
                            }
                        }
                        // Patch 90: 不管是不是去程/回程班機、不管是不是飛機 (火車/巴士/接駁車一樣算), 這一天
                        // 只要出現過交通項目, 名稱都收進這裡——併入 day.title 顯示 (見下面 dayTitle 組法),
                        // 不會再整個消失不見; 也不會受限於上面 outboundFirst/outboundLast 只取頭尾的簡化,
                        // 轉機中間的每一段都會各自出現在這裡。
                        if (!name.isBlank() && !isFlightTransport(item)) {
                            transportTitleParts.add(name);
                        }
                    }
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
            // Patch 90: 交通/班機項目 (transportTitleParts, 見上面收集邏輯) 排在最前面——這一天如果有
            // 移動 (含轉機的每一段), 通常代表這天的行程主軸就是「從哪裡到哪裡」, 排在標題最前面比較符合
            // 使用者「交通要顯示在當天行程標題」的需求, 後面再接景點名稱。
            String dayTitle = java.util.stream.Stream.concat(transportTitleParts.stream(),
                            itemList.stream().map(ItemData::name))
                    .filter(n -> n != null && !n.isBlank())
                    .collect(java.util.stream.Collectors.joining("、"));

            days.add(new DayData(day.getDayNumber(), dayTitle, content.toString(), meals.toString(), hotel,
                    itemList, mapImage));
        }

        // 參考航班摘要另外掃描完整行程項目，不依賴逐日內容組裝時的分類分支；
        // 這樣已存在於 itinerary_item 的航班即使沒有被日內容分支辨識，也會出現在範本上方。
        outboundFirst = null;
        outboundLast = null;
        returnFirst = null;
        returnLast = null;
        unclassifiedFlights.clear();
        for (ItineraryDay day : itineraryService.getDays(itinerary.getITID())) {
            for (ItineraryItem item : itineraryService.getItems(day.getIDID())) {
                if (item.getItemType() == null || !"transport".equalsIgnoreCase(item.getItemType().trim())
                        || !isFlightTransport(item)) continue;
                String direction = item.getFlightDirection() == null ? ""
                        : item.getFlightDirection().trim().toLowerCase(Locale.ROOT);
                String name = item.getCustomName() == null ? "" : item.getCustomName();
                if (direction.isBlank()) {
                    if (name.startsWith("去程班機")) direction = "outbound";
                    else if (name.startsWith("回程班機")) direction = "return";
                }
                String flightNo = firstNonBlank(item.getTransportNumber(), flightNumberFromName(name));
                String fromAirport = firstNonBlank(item.getFromLocation(), airportFromName(name, true));
                String toAirport = firstNonBlank(item.getToLocation(), airportFromName(name, false));
                FlightLeg leg = new FlightLeg(day.getDayDate(), flightNo,
                        fromAirport, toAirport, item.getStartTime(), item.getEndTime(), direction);
                if ("outbound".equals(direction)) {
                    if (outboundFirst == null) outboundFirst = leg;
                    outboundLast = leg;
                } else if ("return".equals(direction)) {
                    if (returnFirst == null) returnFirst = leg;
                    returnLast = leg;
                } else {
                    unclassifiedFlights.add(leg);
                }
            }
        }

        // 相容尚未設定 flight_direction 的舊航班：依行程中出現順序將第一段視為去程，
        // 若有多段且尚無回程資料，最後一段補作回程，避免參考航班區整列空白。
        if (!unclassifiedFlights.isEmpty()) {
            if (outboundFirst == null) {
                outboundFirst = unclassifiedFlights.get(0);
                outboundLast = outboundFirst;
            }
            if (returnFirst == null && unclassifiedFlights.size() > 1) {
                returnFirst = unclassifiedFlights.get(unclassifiedFlights.size() - 1);
                returnLast = returnFirst;
            } else if (returnFirst == null && outboundFirst != unclassifiedFlights.get(0)) {
                returnFirst = unclassifiedFlights.get(0);
                returnLast = returnFirst;
            }
        }

        // 使用者要求新增的「參考航班」摘要欄位: 去程/回程各 4 個 (出發機場/抵達機場/出發時間/抵達時間),
        // 沒有填去程或回程班機的行程就給空字串 (範本裡的欄位會直接顯示空白, 不會顯示 null 字樣)。
        // 使用者後續要求: 機場名稱前面要帶出這段航班在看板上填的航班編號 (例如「CX459 高雄小港機場」)——
        // 出發機場用「第一段」的編號 (那段從這個機場起飛), 抵達機場用「最後一段」的編號 (那段降落在這個
        // 機場), 轉機時中間各段的編號不會出現在摘要裡 (摘要本來就只取整趟去程/回程的頭尾, 跟這裡蒐集
        // outboundFirst/outboundLast 的邏輯一致)。
        simple.put("outbound_departure_airport", outboundFirst != null ? withFlightNo(outboundFirst.flightNo(), outboundFirst.fromAirport()) : "");
        simple.put("outbound_arrival_airport", outboundLast != null ? withFlightNo(outboundLast.flightNo(), outboundLast.toAirport()) : "");
        simple.put("outbound_departure_time", outboundFirst != null ? formatFlightTime(outboundFirst.depTime()) : "");
        simple.put("outbound_arrival_time", outboundLast != null ? formatFlightTime(outboundLast.arrTime()) : "");
        simple.put("return_departure_airport", returnFirst != null ? withFlightNo(returnFirst.flightNo(), returnFirst.fromAirport()) : "");
        simple.put("return_arrival_airport", returnLast != null ? withFlightNo(returnLast.flightNo(), returnLast.toAirport()) : "");
        simple.put("return_departure_time", returnFirst != null ? formatFlightTime(returnFirst.depTime()) : "");
        simple.put("return_arrival_time", returnLast != null ? formatFlightTime(returnLast.arrTime()) : "");

        return new TemplateData(simple, days, images);
    }

    private static String emptyIfNull(String s) { return s == null ? "" : s; }

    private static boolean isFlightTransport(ItineraryItem item) {
        String method = item.getTransportMethod() == null ? "" : item.getTransportMethod().trim().toLowerCase(java.util.Locale.ROOT);
        String name = item.getCustomName() == null ? "" : item.getCustomName();
        String lowerName = name.toLowerCase(java.util.Locale.ROOT);
        return method.contains("飛機") || method.contains("航班") || method.contains("航空")
                || method.contains("flight") || method.contains("plane") || method.contains("airplane")
                || item.getFlightDirection() != null
                || name.contains("班機") || name.contains("航班")
                || ((name.contains("→") || name.contains("->"))
                    && (name.contains("機場") || lowerName.contains("airport")));
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? emptyIfNull(fallback) : preferred.trim();
    }

    private static String flightNumberFromName(String name) {
        if (name == null || name.isBlank()) return "";
        String route = name.replaceFirst("^(去程|回程)?班機[:：]?\\s*", "").trim();
        java.util.regex.Matcher matcher = Pattern.compile("^([A-Za-z0-9]{2,8})\\s+").matcher(route);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String airportFromName(String name, boolean departure) {
        if (name == null || name.isBlank()) return "";
        String[] parts = name.split("→|->", 2);
        if (parts.length < 2) return "";
        String airport = (departure ? parts[0] : parts[1]).trim();
        airport = airport.replaceFirst("^(去程|回程)?班機[:：]?\\s*", "");
        airport = airport.replaceFirst("^[A-Za-z0-9]{2,8}\\s+", "");
        return airport.trim();
    }

    // 機場名稱前面帶出航班編號, 例如「CX459 高雄小港機場」; 沒有編號就只顯示機場名稱本身;
    // 機場名稱也沒填 (理論上不太會發生) 就回傳空字串, 不會出現只有編號沒有機場名稱的怪畫面。
    private static String withFlightNo(String flightNo, String airport) {
        String airportText = emptyIfNull(airport);
        if (flightNo == null || flightNo.isBlank()) return airportText;
        return airportText.isBlank() ? flightNo.trim() : flightNo.trim() + " " + airportText;
    }

    // 使用者要求: 參考航班摘要的時間只要顯示時分 (HH:mm), 不需要年月日——日期已經在標題頁「{{date_range}}」
    // 顯示過一次, 這裡不用重複; 時間本身沒填就回傳空字串。
    private static String formatFlightTime(LocalTime time) {
        if (time == null) return "";
        return time.format(DateTimeFormatter.ofPattern("HH:mm"));
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

    /** 直接合併參考航班儲存格內的分段 run，再套用航班欄位值，避免 Word 佔位符被拆開後漏替換。 */
    private void replaceFlightPlaceholdersInTables(IBody body, Map<String, String> values) {
        for (XWPFTable table : body.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    for (XWPFParagraph paragraph : cell.getParagraphs()) {
                        StringBuilder original = new StringBuilder();
                        for (XWPFRun run : paragraph.getRuns()) {
                            String text = run.getText(0);
                            if (text != null) original.append(text);
                        }
                        String replacement = original.toString();
                        for (String key : List.of(
                                "outbound_departure_airport", "outbound_arrival_airport",
                                "outbound_departure_time", "outbound_arrival_time",
                                "return_departure_airport", "return_arrival_airport",
                                "return_departure_time", "return_arrival_time")) {
                            replacement = replacement.replace("{{" + key + "}}", emptyIfNull(values.get(key)));
                        }
                        if (!replacement.equals(original.toString())) {
                            List<XWPFRun> runs = paragraph.getRuns();
                            XWPFRun first = runs.isEmpty() ? paragraph.createRun() : runs.get(0);
                            first.setText(replacement, 0);
                            for (int i = runs.size() - 1; i >= 1; i--) paragraph.removeRun(i);
                        }
                    }
                    if (!cell.getTables().isEmpty()) replaceFlightPlaceholdersInTables(cell, values);
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

        // 使用者反映: 用自訂範本匯出的 docx, Word 打開會跳出「找到無法讀取的內容, 您要復原本文件的
        // 內容嗎」。根因: {{item_start}}...{{item_end}} 這個逐項目區塊通常整個放在表格儲存格裡,
        // 如果這個區塊剛好是那個儲存格「唯一」的內容 (範本作者沒有在區塊外面多留其他段落), 而這一天
        // 又沒有任何會落進 rows 的一般項目 (例如整天只排了餐食/住宿/交通, 沒有景點/自費/自由活動),
        // 或範本區塊本身是空的 ({{item_start}} 後面直接接 {{item_end}}, 沒有樣板段落可複製), 下面
        // 就會一段都不會插入, 接著把 start/end 標記跟範本段落全部移除——儲存格因此變成完全沒有任何
        // <w:p>/<w:tbl> 子元素。這違反 OOXML 規範 (儲存格一定要有至少一段內容), Word 開啟時就判定
        // 檔案損毀。修正: 先判斷「這個區塊是不是這個 body (儲存格) 的唯一內容」, 是的話、且等一下
        // 不會有任何東西可以替補進去, 就在移除標記之前先補一個空白段落佔位, 確保移除完之後這個
        // body 一定還留著至少一段內容, 不會產生結構不合法的空儲存格。
        boolean blockIsOnlyContent = all.size() == blockTemplate.size() + 2; // 只有 start + 範本內容 + end
        boolean willInsertNothing = blockTemplate.isEmpty() || rows.isEmpty();
        if (blockIsOnlyContent && willInsertNothing) {
            XmlCursor placeholderCursor = startMarker.getCTP().newCursor();
            body.insertNewParagraph(placeholderCursor);
            placeholderCursor.dispose();
        }

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
