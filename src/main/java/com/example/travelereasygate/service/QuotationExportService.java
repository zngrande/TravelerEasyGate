package com.example.travelereasygate.service;

import com.example.travelereasygate.DAO.ItineraryDAO;
import com.example.travelereasygate.entity.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 報價單 Excel 匯出 (需求: 最後提出的行程報價要能像圖片單據那樣輸出成 Excel)
 *
 * 目前先做「內建固定版型」(不開放旅行社自己上傳 .xlsx 範本), 版面對應紙本單據的四個區塊:
 *   一、費用景點   → 按人頭計費 (PER_PAX) 的項目, 如餐費/住宿/門票
 *   二、接駁車/遊覽車/雜費 → 全團固定一口價 (FIXED_GROUP) 的項目
 *   三、人數區間   → 有設定級距的項目, 列出每一條「幾人~幾人」對應的價錢
 *   四、備註       → 報價單的 note 欄位 (報價含稅燃/出發限制/雪祭期間不適用...這類條款文字)
 *
 * 跟紙本單據不完全一樣的地方: 紙本的「人數區間」表是「雜項支出/N-P價格/團費成本」三欄並存的複合欄位,
 * 我們系統的區間價錢目前只有單一「價錢」欄位 (掛在單一項目底下), 所以這裡改成「項目 + 級距 + 價錢」的列表,
 * 資訊量是一致的, 只是呈現方式比較單純。如果之後真的需要那三欄並存, 要再另外加欄位, 現在先不做。
 */
@Service
public class QuotationExportService {

    private final ItineraryDAO itineraryDAO;
    private final QuotationService quotationService;

    @Autowired
    public QuotationExportService(ItineraryDAO itineraryDAO, QuotationService quotationService) {
        this.itineraryDAO = itineraryDAO;
        this.quotationService = quotationService;
    }

    public byte[] generateExcel(int QID) throws Exception {
        Quotation quotation = quotationService.findById(QID);
        if (quotation == null) throw new IllegalArgumentException("找不到這份報價單");
        Itinerary itinerary = itineraryDAO.findById(quotation.getITID());

        List<QuotationLine> lines = quotationService.findLines(QID);
        Map<String, BigDecimal> totals = quotationService.getTotals(QID);

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("報價單");
            sheet.setColumnWidth(0, 26 * 256);
            for (int c = 1; c <= 6; c++) sheet.setColumnWidth(c, 14 * 256);

            Styles styles = new Styles(wb);

            int r = 0;
            r = writeHeader(sheet, styles, itinerary, quotation, r);
            r++; // 空一行

            r = writeSectionHeading(sheet, styles, "一、費用景點（按人頭計費）", r);
            List<QuotationLine> perPax = lines.stream().filter(l -> !"FIXED_GROUP".equals(l.getCostType())).toList();
            r = writeLineTable(sheet, styles, perPax, r);
            r++;

            r = writeSectionHeading(sheet, styles, "二、接駁車 / 遊覽車 / 雜費（全團固定）", r);
            List<QuotationLine> fixedGroup = lines.stream().filter(l -> "FIXED_GROUP".equals(l.getCostType())).toList();
            r = writeLineTable(sheet, styles, fixedGroup, r);
            r++;

            r = writeSectionHeading(sheet, styles, "三、人數區間", r);
            r = writeTierSection(sheet, styles, lines, r);
            r++;

            r = writeSectionHeading(sheet, styles, "四、總計", r);
            r = writeTotals(sheet, styles, totals, r);
            r++;

            if (quotation.getNote() != null && !quotation.getNote().isBlank()) {
                r = writeSectionHeading(sheet, styles, "備註", r);
                r = writeNote(sheet, styles, quotation.getNote(), r);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    // ------------------------------------------------------------
    // 版面區塊
    // ------------------------------------------------------------

    private int writeHeader(Sheet sheet, Styles styles, Itinerary itinerary, Quotation quotation, int r) {
        Row titleRow = sheet.createRow(r++);
        setCell(titleRow, 0, "團體名稱：" + (itinerary != null ? itinerary.getTitle() : "") + "（第 " + quotation.getVersion() + " 版）", styles.title);

        Row metaRow = sheet.createRow(r++);
        String dateText = itinerary != null && itinerary.getStartDate() != null
                ? itinerary.getStartDate().format(DateTimeFormatter.ofPattern("yy.MM.dd")) : "";
        setCell(metaRow, 0, "出發日期：" + dateText, styles.normal);
        setCell(metaRow, 2, "團體人數：" + quotation.getGroupSize() + " 人", styles.normal);
        setCell(metaRow, 4, "報價狀態：" + statusLabel(quotation.getStatus()), styles.normal);
        return r;
    }

    private String statusLabel(String status) {
        if ("locked".equals(status)) return "已鎖定";
        if ("confirmed".equals(status)) return "已確認";
        return "草稿";
    }

    private int writeSectionHeading(Sheet sheet, Styles styles, String text, int r) {
        Row row = sheet.createRow(r++);
        setCell(row, 0, text, styles.sectionHeading);
        return r;
    }

    private int writeLineTable(Sheet sheet, Styles styles, List<QuotationLine> lines, int r) {
        String[] headers = {"項目", "類別", "單價", "數量", "Net", "NNet", "基本報價", "同業價", "直售價"};
        Row headerRow = sheet.createRow(r++);
        for (int c = 0; c < headers.length; c++) setCell(headerRow, c, headers[c], styles.tableHeader);

        if (lines.isEmpty()) {
            Row empty = sheet.createRow(r++);
            setCell(empty, 0, "（無項目）", styles.normal);
            return r;
        }

        BigDecimal subtotalGross = BigDecimal.ZERO, subtotalNet = BigDecimal.ZERO, subtotalBasic = BigDecimal.ZERO,
                subtotalTrade = BigDecimal.ZERO, subtotalRetail = BigDecimal.ZERO;
        for (QuotationLine line : lines) {
            Row row = sheet.createRow(r++);
            setCell(row, 0, line.getItemName(), styles.normal);
            setCell(row, 1, line.getCategory(), styles.normal);
            setNumericCell(row, 2, line.getUnitPrice(), styles.currency);
            setCell(row, 3, String.valueOf(line.getQuantity()), styles.normal);
            setNumericCell(row, 4, line.getGrossCost(), styles.currency);
            setNumericCell(row, 5, line.getNetCost(), styles.currency);
            setNumericCell(row, 6, line.getBasicPrice(), styles.currency);
            setNumericCell(row, 7, line.getTradePrice(), styles.currency);
            setNumericCell(row, 8, line.getRetailPrice(), styles.currency);

            subtotalGross = subtotalGross.add(nz(line.getGrossCost()));
            subtotalNet = subtotalNet.add(nz(line.getNetCost()));
            subtotalBasic = subtotalBasic.add(nz(line.getBasicPrice()));
            subtotalTrade = subtotalTrade.add(nz(line.getTradePrice()));
            subtotalRetail = subtotalRetail.add(nz(line.getRetailPrice()));
        }

        Row subtotalRow = sheet.createRow(r++);
        setCell(subtotalRow, 0, "小計", styles.tableHeader);
        setNumericCell(subtotalRow, 4, subtotalGross, styles.currencyBold);
        setNumericCell(subtotalRow, 5, subtotalNet, styles.currencyBold);
        setNumericCell(subtotalRow, 6, subtotalBasic, styles.currencyBold);
        setNumericCell(subtotalRow, 7, subtotalTrade, styles.currencyBold);
        setNumericCell(subtotalRow, 8, subtotalRetail, styles.currencyBold);
        return r;
    }

    private int writeTierSection(Sheet sheet, Styles styles, List<QuotationLine> lines, int r) {
        boolean any = false;
        for (QuotationLine line : lines) {
            List<QuotationLineTier> tiers = quotationService.listTiers(line.getQLID());
            if (tiers.isEmpty()) continue;
            any = true;

            Row itemRow = sheet.createRow(r++);
            setCell(itemRow, 0, line.getItemName(), styles.normalBold);

            Row headerRow = sheet.createRow(r++);
            setCell(headerRow, 0, "下限人數", styles.tableHeader);
            setCell(headerRow, 1, "上限人數", styles.tableHeader);
            setCell(headerRow, 2, "價錢", styles.tableHeader);

            for (QuotationLineTier tier : tiers) {
                Row row = sheet.createRow(r++);
                setCell(row, 0, String.valueOf(tier.getMinQty()), styles.normal);
                setCell(row, 1, tier.getMaxQty() != null ? String.valueOf(tier.getMaxQty()) : "以上", styles.normal);
                setNumericCell(row, 2, tier.getPrice(), styles.currency);
            }
            r++; // 每個項目的級距表之間空一行
        }

        if (!any) {
            Row empty = sheet.createRow(r++);
            setCell(empty, 0, "（這份報價單沒有任何項目設定人數區間）", styles.normal);
        }
        return r;
    }

    private int writeTotals(Sheet sheet, Styles styles, Map<String, BigDecimal> totals, int r) {
        String[][] rows = {
                {"grossCost", "Net（總成本）"}, {"netCost", "NNet（總淨成本）"}, {"basicPrice", "基本報價"},
                {"tradePrice", "總同業價"}, {"retailPrice", "總直售價"},
                {"rebateAmount", "總退傭"}, {"profitTrade", "利潤（同業）"}, {"profitRetail", "利潤（直售）"}
        };
        for (String[] pair : rows) {
            Row row = sheet.createRow(r++);
            setCell(row, 0, pair[1], styles.normalBold);
            setNumericCell(row, 1, totals.get(pair[0]), styles.currencyBold);
        }
        return r;
    }

    private int writeNote(Sheet sheet, Styles styles, String note, int r) {
        for (String line : note.split("\n")) {
            Row row = sheet.createRow(r++);
            setCell(row, 0, line, styles.normal);
        }
        return r;
    }

    // ------------------------------------------------------------
    // 小工具
    // ------------------------------------------------------------

    private void setCell(Row row, int col, String text, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(text);
        cell.setCellStyle(style);
    }

    private void setNumericCell(Row row, int col, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(nz(value).doubleValue());
        cell.setCellStyle(style);
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** 集中管理儲存格樣式, 避免每個 sheet.createRow 呼叫都重新 new 一次 (POI 建議 CellStyle 要重複使用)。 */
    private static class Styles {
        final CellStyle title, sectionHeading, tableHeader, normal, normalBold, currency, currencyBold;

        Styles(Workbook wb) {
            Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            title = wb.createCellStyle();
            title.setFont(titleFont);

            Font sectionFont = wb.createFont();
            sectionFont.setBold(true);
            sectionFont.setFontHeightInPoints((short) 12);
            sectionFont.setColor(IndexedColors.DARK_RED.getIndex());
            sectionHeading = wb.createCellStyle();
            sectionHeading.setFont(sectionFont);

            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            tableHeader = wb.createCellStyle();
            tableHeader.setFont(headerFont);
            tableHeader.setFillForegroundColor(IndexedColors.BLUE.getIndex());
            tableHeader.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            applyThinBorder(tableHeader);

            normal = wb.createCellStyle();
            applyThinBorder(normal);

            Font boldFont = wb.createFont();
            boldFont.setBold(true);
            normalBold = wb.createCellStyle();
            normalBold.setFont(boldFont);
            applyThinBorder(normalBold);

            DataFormat format = wb.createDataFormat();
            currency = wb.createCellStyle();
            currency.setDataFormat(format.getFormat("#,##0"));
            applyThinBorder(currency);

            currencyBold = wb.createCellStyle();
            currencyBold.setDataFormat(format.getFormat("#,##0"));
            currencyBold.setFont(boldFont);
            applyThinBorder(currencyBold);
        }

        private void applyThinBorder(CellStyle style) {
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
        }
    }
}
