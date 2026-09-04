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
                repairMergedRegions(sheet);
            }
            replaceAllPlaceholders(wb, data.simpleValues());

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    // 使用者回報「匯出報價單 Excel 都會出錯」——Excel 打開時跳出「發現部分內容有問題」要求修復。
    // 拆開匯出的檔案檢查發現 sheet1.xml 裡 <mergeCells count="27"> 但實際只有 17 個 <mergeCell> 子節點，
    // 這種「宣告的數量」跟「實際內容」對不上的 XML，Excel 的嚴格解析器會直接判定檔案損毀。
    //
    // 根因是 Apache POI 這裡的已知行為：expandLineBlock() 對明細列範圍先用 shiftRows() 把後面的列往下搬，
    // shiftRows() 本身就會連帶搬動/調整落在搬動範圍內的合併儲存格，接著這裡又用 removeMergedRegion()／
    // addMergedRegion() 手動增減合併範圍——兩邊各自維護底層 CTMergeCells 清單，疊加起來就可能讓 XSSFSheet
    // 內部快取的「合併範圍數量」跟實際 CTMergeCells 陣列的長度不同步，寫檔案的時候把不同步的數量直接
    // 序列化成 count 屬性，檔案就壞了。
    //
    // 這一版的修法（強制「重建」一次合併範圍：先讀出全部合併範圍座標、全部移除、再逐一加回去）已經套用過，
    // 但套用之後匯出還是壞（見這次「目前輸出EXCEL還是不行」的回報）——因為 repairMergedRegions() 只能修
    // 「宣告數量跟實際節點數量對不上」，修不了「shiftRows() 幫我們算錯的合併範圍座標本身就是錯的」：
    //
    //   1. Apache POI 的 shiftRows() 對「不是從 A 欄開始」的合併儲存格有多個已知既有 bug（POI Bugzilla
    //      #56454「shiftRows incorrectly handle merged regions that do not contain column 0」、#60709
    //      「shiftRows removes merged regions if shifting several rows in one call」等）。這份範本合計區
    //      的合併儲存格 H13:I13 / H14:I14 / H15:I15 / H17:I17 / F18:G18 / H18:I18 / F19:G19 全部不是從 A 欄
    //      開始，恰好全部落在 expandLineBlock() 呼叫 shiftRows() 搬動的範圍內（{{line_end}} 標記列之後），
    //      shiftRows() 幫我們搬這些合併範圍時就有機會算錯座標——算錯之後不管有沒有事後 repairMergedRegions()
    //      重建，寫進去的 count 屬性都跟實際節點數一致（因為是照著「錯的」座標重建），但座標本身依然是錯的
    //      （落在錯誤的列、或跟其他合併範圍重疊），一樣會被 Excel 判定內容損毀。
    //   2. 額外發現一個獨立、範本裡剛好沒踩到但同樣會讓輸出壞掉的 bug：下面「每一份複製的合併儲存格範圍也
    //      跟著位移」那段舊寫法算新座標時漏加 templateStart（明細列範本本身合併儲存格若換算出來的新座標少了
    //      這段起始列偏移量，複製出來的合併範圍會落到表格最上方，跟標題/表頭的既有合併範圍重疊，一樣會壞
    //      檔）——這份範本剛好在 {{line_start}}...{{line_end}} 中間的那一列本身沒有合併儲存格，才沒有踩到，
    //      但只要旅行社自己設計的範本把「品項名稱」欄之類的合併起來，就會踩到。
    //
    // 修法：完全不呼叫 Sheet.shiftRows()，改成「自己手動搬」——先把 {{line_end}} 標記列（含）之後的所有列
    // (含它們的合併儲存格) 整份拍照起來並從工作表上移除，明細列展開完、複製出對應份數之後，再把拍照起來的
    // 內容整份寫回新的位置。全程只用 addMergedRegion()／removeMergedRegion() 這兩個底層 API 直接算絕對座標，
    // 不假手 shiftRows() 的合併儲存格搬移邏輯，就不會再踩到上面兩個 bug。
    private void repairMergedRegions(Sheet sheet) {
        int numMerged = sheet.getNumMergedRegions();
        if (numMerged == 0) return;
        List<CellRangeAddress> merges = new ArrayList<>();
        for (int i = 0; i < numMerged; i++) {
            merges.add(sheet.getMergedRegion(i));
        }
        for (int i = numMerged - 1; i >= 0; i--) {
            sheet.removeMergedRegion(i);
        }
        for (CellRangeAddress merge : merges) {
            sheet.addMergedRegion(merge);
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

        // 先把模板列的樣式/合併範圍記下來 (連同它們原本的合併儲存格一起從工作表移除, 準備重建)
        List<RowSnapshot> template = new ArrayList<>();
        for (int r = templateStart; r <= templateEnd; r++) {
            template.add(RowSnapshot.capture(sheet, r));
        }
        List<CellRangeAddress> templateMerges = mergedRegionsWithin(sheet, templateStart, templateEnd);

        if (n == 0) {
            // 沒有任何明細: 把模板列整個清空, 只留標記列變空白, 不留殘影; 後面 (合計/頁尾) 完全不用動,
            // 這個分支從來沒有 shiftRows 過, 不是這次「輸出檔案損壞」的成因, 維持原本寫法
            for (int r = templateStart; r <= templateEnd; r++) {
                clearRow(sheet, r);
            }
            clearRow(sheet, startRowIdx);
            clearRow(sheet, endRowIdx);
            return;
        }

        // {{line_end}} 標記列 (含) 之後的所有列 (合計/頁尾等) 要整段往下搬, 挪出空間放複製出來的明細列。
        // 完全不呼叫 Sheet.shiftRows() (原因見上面 CORRUPTION NOTE): 自己先把這段範圍 (含合併儲存格) 整份
        // 拍照起來並從工作表移除, 等明細列都展開完, 再把拍照的內容整份寫回新位置。
        int tailStart = endRowIdx;
        List<RowSnapshot> tail = new ArrayList<>();
        for (int r = tailStart; r <= lastRowNum; r++) {
            tail.add(RowSnapshot.capture(sheet, r));
        }
        List<CellRangeAddress> tailMerges = mergedRegionsWithin(sheet, tailStart, lastRowNum);

        for (int r = templateStart; r <= lastRowNum; r++) {
            Row row = sheet.getRow(r);
            if (row != null) sheet.removeRow(row);
        }

        // 依序把每一筆明細的資料填進對應的列版面
        for (int i = 0; i < n; i++) {
            Map<String, String> data = lineRows.get(i);
            int blockStart = templateStart + i * templateRowCount;
            for (int j = 0; j < templateRowCount; j++) {
                int targetRowIdx = blockStart + j;
                Row targetRow = sheet.createRow(targetRowIdx);
                template.get(j).applyTo(targetRow);
                for (Cell cell : targetRow) {
                    if (cell.getCellType() == CellType.STRING) {
                        applyPlaceholderToCell(cell, data);
                    }
                }
            }
            // 每一份複製的合併儲存格範圍也跟著位移 (絕對座標 = 這一份的起始列 + 範本內的相對列)
            for (CellRangeAddress merge : templateMerges) {
                sheet.addMergedRegion(new CellRangeAddress(
                        merge.getFirstRow() + blockStart, merge.getLastRow() + blockStart,
                        merge.getFirstColumn(), merge.getLastColumn()));
            }
        }

        // 把先前拍照起來的「{{line_end}} 標記列之後」內容整份寫回新位置
        int newTailStart = templateStart + n * templateRowCount;
        for (int k = 0; k < tail.size(); k++) {
            Row row = sheet.createRow(newTailStart + k);
            tail.get(k).applyTo(row);
        }
        for (CellRangeAddress merge : tailMerges) {
            sheet.addMergedRegion(new CellRangeAddress(
                    merge.getFirstRow() + newTailStart, merge.getLastRow() + newTailStart,
                    merge.getFirstColumn(), merge.getLastColumn()));
        }

        // 標記列 (起點/終點) 清空成空白列, 不留 {{line_start}} / {{line_end}} 字樣
        clearRow(sheet, startRowIdx);
        clearRow(sheet, newTailStart); // 這是搬到新位置之後的 {{line_end}} 標記列
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
     *
     * 真正抓到你這次「已經套用 shiftRows 修法、還是壞檔」的根因就在這個方法: 拿使用者實際匯出壞掉的檔案拆開
     * sheet1.xml 檢查, 發現像 B4（{{group_name}}）這種儲存格長這樣:
     *   <c r="B4" s="4" t="inlineStr"><v>新馬5日～星耀樟宜...（第 2 版）</v><is><t>{{group_name}}</t></is></c>
     * 同一格「同時」有 <v>(新值) 跟 <is><t>(舊的佔位符文字), type 還留著原本的 t="inlineStr"——這是不合法的
     * OOXML (t="inlineStr" 的儲存格照規格只能有 <is>, 不能有 <v>), Excel 嚴格解析器直接判定內容損毀。
     *
     * 對照 Apache POI 原始碼 (XSSFCell.setCellValue(RichTextString)／setCellValue(double)) 確認: 如果呼叫
     * setCellValue() 的當下, 這個儲存格「本來就是」inlineStr 型別 (這份範本本身就是用 inlineStr 存文字,
     * 不是 sharedStrings), POI 會直接執行 `_cell.setV(新值)` 把新值寫進 <v>, 但完全沒有把舊的 <is> 元素、
     * 也沒有把 t="inlineStr" 這個型別標記清掉——兩者疊加寫進同一格, 產生上面那種壞掉的 XML。範本裡凡是
     * 「單次替換」的佔位符 (團體名稱/報價狀態/國家/天數/團體人數/備註...這些不在 {{line_start}}~{{line_end}}
     * 明細列區塊裡面的欄位) 全部會踩到這個問題, 而且這是從最一開始 (patch 36 之前) 就存在的既有 bug, 跟
     * shiftRows／合併儲存格座標完全是兩回事, 只是這次剛好被下一層蓋住了才一直沒被單獨抓出來。
     *
     * 修法: 寫入新值之前先呼叫 cell.setBlank() 把儲存格徹底清空 (型別重設成 BLANK, 底層 <is>/<v> 都會被清掉),
     * 再呼叫 setCellValue()——這樣不管這個儲存格原本是不是 inlineStr, 都會從乾淨的狀態重新寫入, 不會再殘留
     * 舊的 <is> 元素。
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
            cell.setBlank(); // 先清空, 避免原本是 inlineStr 型別時舊的 <is> 元素殘留跟新值一起寫進 XML
            if (numeric != null) {
                cell.setCellValue(numeric);
            } else {
                cell.setCellValue(value);
            }
            return;
        }
        String replaced = replacePlaceholders(original, values);
        cell.setBlank(); // 同上, 混合文字的情況一樣要先清空避免殘留舊的 <is> 元素
        cell.setCellValue(replaced);
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

    /**
     * 記錄一列的儲存格樣式跟內容 (字串/數字/布林/公式), 用來複製到新產生的列上。
     * 明細列範本用這個記錄「版面長相」(值之後會被佔位符替換蓋掉); 現在 {{line_end}} 之後的合計/頁尾列
     * 也改用這個記錄再整份搬到新位置 (取代原本的 Sheet.shiftRows()), 所以型別要完整記錄, 不能只記字串——
     * 不然合計區裡如果有人自己加了公式或純數字, 搬過去就會憑空消失變空白。
     * 注意: 公式儲存格只是原封不動存/還原公式文字本身, 不會像 shiftRows() 那樣自動調整公式裡引用到的儲存格
     * 座標——如果範本合計區用公式引用「同一列」的其他欄位 (例如 H13 寫 =E13*1.15), 搬到新列之後那個公式
     * 引用的欄位座標不會跟著變, 要注意這點, 目前的範本規格本來就是靠 {{total.xxx}} 佔位符讓後端算好數字
     * 直接寫入, 不建議在合計區用公式引用明細列以外的欄位。
     */
    private static class RowSnapshot {
        final float height;
        final Map<Integer, CellStyle> styles = new HashMap<>();
        final Map<Integer, CellType> types = new HashMap<>();
        final Map<Integer, String> stringValues = new HashMap<>();
        final Map<Integer, Double> numericValues = new HashMap<>();
        final Map<Integer, Boolean> booleanValues = new HashMap<>();
        final Map<Integer, String> formulaValues = new HashMap<>();

        private RowSnapshot(float height) { this.height = height; }

        static RowSnapshot capture(Sheet sheet, int rowIdx) {
            Row row = sheet.getRow(rowIdx);
            RowSnapshot snap = new RowSnapshot(row != null ? row.getHeightInPoints() : sheet.getDefaultRowHeightInPoints());
            if (row == null) return snap;
            for (Cell cell : row) {
                int col = cell.getColumnIndex();
                snap.styles.put(col, cell.getCellStyle());
                CellType type = cell.getCellType();
                snap.types.put(col, type);
                switch (type) {
                    case STRING -> snap.stringValues.put(col, cell.getStringCellValue());
                    case NUMERIC -> snap.numericValues.put(col, cell.getNumericCellValue());
                    case BOOLEAN -> snap.booleanValues.put(col, cell.getBooleanCellValue());
                    case FORMULA -> snap.formulaValues.put(col, cell.getCellFormula());
                    default -> { /* BLANK / ERROR / _NONE: 只需要樣式, 不用管值 */ }
                }
            }
            return snap;
        }

        void applyTo(Row targetRow) {
            targetRow.setHeightInPoints(height);
            for (Map.Entry<Integer, CellStyle> entry : styles.entrySet()) {
                int col = entry.getKey();
                Cell cell = targetRow.getCell(col);
                if (cell == null) cell = targetRow.createCell(col);
                cell.setCellStyle(entry.getValue());
                CellType type = types.get(col);
                if (type == null) continue;
                switch (type) {
                    case STRING -> cell.setCellValue(stringValues.get(col));
                    case NUMERIC -> cell.setCellValue(numericValues.get(col));
                    case BOOLEAN -> cell.setCellValue(booleanValues.get(col));
                    case FORMULA -> cell.setCellFormula(formulaValues.get(col));
                    default -> { }
                }
            }
        }
    }
}