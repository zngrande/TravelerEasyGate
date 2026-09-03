package com.example.travelereasygate.service;

import com.example.travelereasygate.entity.Itinerary;
import com.example.travelereasygate.entity.Quotation;
import com.example.travelereasygate.entity.QuotationLine;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 「同業版型」Excel 範本合併引擎: 讀取旅行社上傳的 .xlsx 範本, 把佔位符換成這份報價單的實際資料,
 * 並把 {{line_start}}...{{line_end}} 之間的列 (row) 依報價單明細筆數複製多份。
 * 設計理念跟 TemplateMergeService (Word 客戶版型) 對齊, 差別是這裡的重複單位是「列」而不是「段落」。
 *
 * 範本製作規格:
 *   單次替換 (整份範本任何儲存格都可以用, 出現一次就換一次):
 *     {{group_name}}  團體名稱 (行程標題 + 第幾版)
 *     {{country}}     國家/地區
 *     {{days_count}}  天數
 *     {{date_range}}  出發~回程日期
 *     {{group_size}}  團體人數
 *     {{status}}      報價狀態 (草稿/已鎖定/已確認)
 *     {{note}}        備註 (報價單 note 欄位)
 *     {{total.gross_cost}} {{total.net_cost}} {{total.basic_price}}
 *     {{total.trade_price}} {{total.retail_price}} {{total.rebate_amount}}
 *     {{total.profit_trade}} {{total.profit_retail}}
 *
 *   明細列區塊: 用兩列各自獨立的「標記列」(A欄整列只放這個標記) 當起點/終點:
 *     {{line_start}}
 *     ... (中間放一列或多列當「一筆明細的列版面」, 隨便設計, 裡面用 {{line.xxx}}) ...
 *     {{line_end}}
 *   系統會把中間的列版面依這份報價單實際的明細筆數複製對應份數, 標記列本身在輸出時會變成空白列。
 *   可用欄位: {{line.name}} {{line.category}} {{line.unit_price}} {{line.quantity}}
 *            {{line.gross_cost}} {{line.net_cost}} {{line.basic_price}}
 *            {{line.trade_price}} {{line.retail_price}}
 *
 * 注意事項:
 *   - {{line_start}} / {{line_end}} 這兩個標記, 每個工作表(分頁)裡只能各出現一次, 而且該列裡只能有這個標記,
 *     不要跟其他文字/欄位混在同一列。
 *   - 明細列版面如果有合併儲存格 (merged cell), 複製後的每一份都會套用同樣的合併範圍。
 *   - 目前只處理第一個符合的 {{line_start}}...{{line_end}} 區塊；如果範本有多個分頁, 每個分頁最多展開一組。
 */
@Service
public class ExcelTemplateMergeService {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([a-zA-Z0-9_.]+)\\}\\}");
    private static final Pattern FULL_PLACEHOLDER = Pattern.compile("^\\{\\{([a-zA-Z0-9_.]+)\\}\\}$");
    private static final String LINE_START = "{{line_start}}";
    private static final String LINE_END = "{{line_end}}";

    private final QuotationService quotationService;

    @Autowired
    public ExcelTemplateMergeService(QuotationService quotationService) {
        this.quotationService = quotationService;
    }

    // ---------------- 資料結構 ----------------

    public record ExcelTemplateData(Map<String, String> simpleValues, List<Map<String, String>> lineRows) {}

    // ---------------- 對外進入點 ----------------

    public byte[] merge(byte[] templateBytes, ExcelTemplateData data) throws Exception {
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(templateBytes))) {
            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                Sheet sheet = wb.getSheetAt(s);
                expandLineBlock(sheet, data.lineRows());
            }
            replaceAllPlaceholders(wb, data.simpleValues());

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    /** 從資料庫組出合併用的 ExcelTemplateData, 給 ExportController 呼叫。 */
    public ExcelTemplateData buildTemplateData(Quotation quotation, Itinerary itinerary) {
        Map<String, String> simple = new HashMap<>();
        String groupName = (itinerary != null ? nz(itinerary.getTitle()) : "") + "（第 " + quotation.getVersion() + " 版）";
        simple.put("group_name", groupName);
        simple.put("country", itinerary != null ? nz(itinerary.getCountry()) : "");
        simple.put("days_count", itinerary != null ? String.valueOf(itinerary.getDaysCount()) : "");
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy/MM/dd");
        String dateRange = (itinerary != null && itinerary.getStartDate() != null)
                ? itinerary.getStartDate().format(fmt) + " ~ " + itinerary.getEndDate().format(fmt) : "";
        simple.put("date_range", dateRange);
        simple.put("group_size", String.valueOf(quotation.getGroupSize()));
        simple.put("status", statusLabel(quotation.getStatus()));
        simple.put("note", nz(quotation.getNote()));

        Map<String, BigDecimal> totals = quotationService.getTotals(quotation.getQID());
        simple.put("total.gross_cost", money(totals.get("grossCost")));
        simple.put("total.net_cost", money(totals.get("netCost")));
        simple.put("total.basic_price", money(totals.get("basicPrice")));
        simple.put("total.trade_price", money(totals.get("tradePrice")));
        simple.put("total.retail_price", money(totals.get("retailPrice")));
        simple.put("total.rebate_amount", money(totals.get("rebateAmount")));
        simple.put("total.profit_trade", money(totals.get("profitTrade")));
        simple.put("total.profit_retail", money(totals.get("profitRetail")));

        List<QuotationLine> lines = quotationService.findLines(quotation.getQID());
        List<Map<String, String>> lineRows = new ArrayList<>();
        for (QuotationLine line : lines) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("line.name", nz(line.getItemName()));
            row.put("line.category", nz(line.getCategory()));
            row.put("line.unit_price", money(line.getUnitPrice()));
            row.put("line.quantity", String.valueOf(line.getQuantity()));
            row.put("line.gross_cost", money(line.getGrossCost()));
            row.put("line.net_cost", money(line.getNetCost()));
            row.put("line.basic_price", money(line.getBasicPrice()));
            row.put("line.trade_price", money(line.getTradePrice()));
            row.put("line.retail_price", money(line.getRetailPrice()));
            lineRows.add(row);
        }

        return new ExcelTemplateData(simple, lineRows);
    }

    // ---------------- 明細列展開 ----------------

    /**
     * 找 {{line_start}} / {{line_end}} 這兩列, 把中間的列版面依 lineRows 筆數複製,
     * 每一份帶入對應那一筆明細的資料, 原本的標記列則清空成空白列。
     */
    private void expandLineBlock(Sheet sheet, List<Map<String, String>> lineRows) {
        int startRowIdx = -1, endRowIdx = -1;
        int lastRowNum = sheet.getLastRowNum();
        for (int r = 0; r <= lastRowNum; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            String marker = soleMarkerText(row);
            if (LINE_START.equals(marker) && startRowIdx == -1) startRowIdx = r;
            else if (LINE_END.equals(marker) && startRowIdx != -1 && endRowIdx == -1) { endRowIdx = r; break; }
        }
        if (startRowIdx == -1 || endRowIdx == -1 || endRowIdx <= startRowIdx + 1) {
            // 這個分頁沒有明細區塊, 或範本沒放模板列, 不處理
            return;
        }

        int templateStart = startRowIdx + 1;
        int templateEnd = endRowIdx - 1;
        int templateRowCount = templateEnd - templateStart + 1;
        int n = Math.max(lineRows.size(), 0);

        // 先把模板列的樣式/合併範圍記下來, 因為等一下 shiftRows 之後原本的列物件可能被搬走
        List<RowSnapshot> template = new ArrayList<>();
        for (int r = templateStart; r <= templateEnd; r++) {
            template.add(RowSnapshot.capture(sheet, r));
        }
        List<CellRangeAddress> templateMerges = mergedRegionsWithin(sheet, templateStart, templateEnd);

        if (n == 0) {
            // 沒有任何明細: 把模板列整個清空, 只留標記列變空白, 不留殘影
            for (int r = templateStart; r <= templateEnd; r++) {
                clearRow(sheet, r);
            }
            clearRow(sheet, startRowIdx);
            clearRow(sheet, endRowIdx);
            return;
        }

        int extraRows = (n - 1) * templateRowCount;
        if (extraRows > 0) {
            sheet.shiftRows(templateEnd + 1, Math.max(lastRowNum, templateEnd), extraRows, true, false);
        }

        // 依序把每一筆明細的資料填進對應的列版面
        for (int i = 0; i < n; i++) {
            Map<String, String> data = lineRows.get(i);
            for (int j = 0; j < templateRowCount; j++) {
                int targetRowIdx = templateStart + i * templateRowCount + j;
                RowSnapshot snap = template.get(j);
                Row targetRow = sheet.getRow(targetRowIdx);
                if (targetRow == null) targetRow = sheet.createRow(targetRowIdx);
                snap.applyTo(targetRow);
                for (Cell cell : targetRow) {
                    if (cell.getCellType() == CellType.STRING) {
                        applyPlaceholderToCell(cell, data);
                    }
                }
            }
            // 每一份複製的合併儲存格範圍也跟著位移
            int offset = i * templateRowCount;
            for (CellRangeAddress merge : templateMerges) {
                sheet.addMergedRegion(new CellRangeAddress(
                        merge.getFirstRow() + offset, merge.getLastRow() + offset,
                        merge.getFirstColumn(), merge.getLastColumn()));
            }
        }

        // 標記列 (起點/終點) 清空成空白列, 不留 {{line_start}} / {{line_end}} 字樣
        clearRow(sheet, startRowIdx);
        int newEndRowIdx = templateStart + n * templateRowCount;
        clearRow(sheet, newEndRowIdx);
    }

    /** 如果這一列「只有」一個字串儲存格且內容剛好是某個標記, 回傳該標記文字; 否則回傳 null。 */
    private String soleMarkerText(Row row) {
        String found = null;
        for (Cell cell : row) {
            if (cell.getCellType() != CellType.STRING) continue;
            String text = cell.getStringCellValue() == null ? "" : cell.getStringCellValue().trim();
            if (text.isEmpty()) continue;
            if (found != null) return null; // 同一列已經有別的文字了, 不算是純標記列
            found = text;
        }
        return found;
    }

    // 找出「模板列範圍」裡面原本就有的合併儲存格範圍, 換算成「相對模板起始列」的座標回傳 (給每一份複製
    // 各自重新加回去用), 並把這些原始的合併範圍從工作表上真正移除——這裡直接用「索引」操作, 不要用座標
    // 比對 (先前的版本用「絕對座標 + 再加一次 fromRow」去比對, 邏輯是錯的, 永遠比對不到, 導致原本的合併
    // 範圍留在原地沒被移除；後面 addMergedRegion() 幫每一份複製加回一樣座標的合併範圍時, 跟這個「殘留」的
    // 舊合併範圍完全重疊, Apache POI 會直接丟例外 IllegalStateException: Cannot add merged region ...
    // because it already exists，這正是「範本裡的明細列版面只要有合併儲存格，匯出 Excel 就會出錯」的成因)。
    private List<CellRangeAddress> mergedRegionsWithin(Sheet sheet, int fromRow, int toRow) {
        List<CellRangeAddress> result = new ArrayList<>();
        List<Integer> indicesToRemove = new ArrayList<>();
        for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
            CellRangeAddress region = sheet.getMergedRegion(i);
            if (region.getFirstRow() >= fromRow && region.getLastRow() <= toRow) {
                result.add(new CellRangeAddress(region.getFirstRow() - fromRow, region.getLastRow() - fromRow,
                        region.getFirstColumn(), region.getLastColumn()));
                indicesToRemove.add(i);
            }
        }
        // 一定要由大到小移除: Apache POI 每移除一個合併範圍, 後面的索引就會整個往前重排一位,
        // 如果照原本由小到大的順序移除, 移除第一個之後其餘索引全部錯位, 會刪錯合併範圍。
        for (int idx = indicesToRemove.size() - 1; idx >= 0; idx--) {
            sheet.removeMergedRegion(indicesToRemove.get(idx));
        }
        return result;
    }

    private void clearRow(Sheet sheet, int rowIdx) {
        Row row = sheet.getRow(rowIdx);
        if (row == null) return;
        for (Cell cell : row) {
            cell.setBlank();
        }
    }

    // ---------------- 一般佔位符替換 ----------------

    private void replaceAllPlaceholders(Workbook wb, Map<String, String> values) {
        for (int s = 0; s < wb.getNumberOfSheets(); s++) {
            Sheet sheet = wb.getSheetAt(s);
            for (Row row : sheet) {
                for (Cell cell : row) {
                    if (cell.getCellType() != CellType.STRING) continue;
                    String original = cell.getStringCellValue();
                    if (original == null || !original.contains("{{")) continue;
                    applyPlaceholderToCell(cell, values);
                }
            }
        }
    }

    /**
     * 把一個字串型儲存格裡的佔位符換成實際資料。
     * 如果這個儲存格「整格」剛好就是一個佔位符 (例如整格只有 {{line.unit_price}}), 而且對應的值是數字,
     * 就直接寫成數字儲存格 (不是文字), 這樣範本裡如果有 SUM 公式加總這一欄才會正常運作；
     * 其他情況 (佔位符跟其他文字混在同一格, 或值本身不是數字) 一律當文字處理。
     */
    private void applyPlaceholderToCell(Cell cell, Map<String, String> values) {
        String original = cell.getStringCellValue();
        if (original == null) return;
        String trimmed = original.trim();
        Matcher fullMatch = FULL_PLACEHOLDER.matcher(trimmed);
        if (fullMatch.matches()) {
            String key = fullMatch.group(1);
            String value = values.get(key);
            if (value == null) return; // 找不到對應的值, 保留原本的佔位符字樣方便除錯
            Double numeric = tryParseNumber(value);
            if (numeric != null) {
                cell.setCellValue(numeric);
            } else {
                cell.setCellValue(value);
            }
            return;
        }
        cell.setCellValue(replacePlaceholders(original, values));
    }

    private Double tryParseNumber(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Double.parseDouble(value.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String replacePlaceholders(String text, Map<String, String> values) {
        if (text == null || !text.contains("{{")) return text;
        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = values.get(key);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value != null ? value : matcher.group(0)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    // ---------------- 小工具 ----------------

    private String statusLabel(String status) {
        if ("locked".equals(status)) return "已鎖定";
        if ("confirmed".equals(status)) return "已確認";
        return "草稿";
    }

    private String money(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private String nz(String value) {
        return value == null ? "" : value;
    }

    /** 記錄一列的儲存格樣式跟公式/文字內容範本 (值本身之後會被佔位符替換蓋掉), 用來複製到新產生的列上。 */
    private static class RowSnapshot {
        final float height;
        final Map<Integer, CellStyle> styles = new HashMap<>();
        final Map<Integer, String> stringValues = new HashMap<>();

        private RowSnapshot(float height) { this.height = height; }

        static RowSnapshot capture(Sheet sheet, int rowIdx) {
            Row row = sheet.getRow(rowIdx);
            RowSnapshot snap = new RowSnapshot(row != null ? row.getHeightInPoints() : sheet.getDefaultRowHeightInPoints());
            if (row == null) return snap;
            for (Cell cell : row) {
                snap.styles.put(cell.getColumnIndex(), cell.getCellStyle());
                if (cell.getCellType() == CellType.STRING) {
                    snap.stringValues.put(cell.getColumnIndex(), cell.getStringCellValue());
                }
            }
            return snap;
        }

        void applyTo(Row targetRow) {
            targetRow.setHeightInPoints(height);
            for (Map.Entry<Integer, CellStyle> entry : styles.entrySet()) {
                Cell cell = targetRow.getCell(entry.getKey());
                if (cell == null) cell = targetRow.createCell(entry.getKey());
                cell.setCellStyle(entry.getValue());
                String template = stringValues.get(entry.getKey());
                if (template != null) cell.setCellValue(template);
            }
        }
    }
}
