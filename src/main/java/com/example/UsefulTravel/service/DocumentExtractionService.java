package com.example.UsefulTravel.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * DocumentExtractionService - 把使用者上傳的 PDF / Word 檔案轉成純文字,
 * 抽出來的文字會直接丟給 AiParseService.parseText() 走跟貼上文字一樣的 AI 解析流程。
 *
 * 支援: .pdf (PDFBox) / .docx (Apache POI)
 * 不支援: 舊版 .doc (二進位格式, 需要額外的 poi-scratchpad, 先不處理),
 *         掃描版 PDF (圖片型 PDF 抽不出文字, 之後要接 OCR 才能處理)
 */
@Service
public class DocumentExtractionService {

    public static class ExtractResult {
        public final String text;
        public final String sourceType; // pdf / docx

        public ExtractResult(String text, String sourceType) {
            this.text = text;
            this.sourceType = sourceType;
        }
    }

    /**
     * 依副檔名判斷格式並抽取文字
     */
    public ExtractResult extract(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new IllegalArgumentException("找不到檔名，請確認有選擇檔案");
        }
        String lower = filename.toLowerCase(Locale.ROOT);

        if (lower.endsWith(".pdf")) {
            return new ExtractResult(extractPdf(file), "pdf");
        } else if (lower.endsWith(".docx")) {
            return new ExtractResult(extractDocx(file), "docx");
        } else if (lower.endsWith(".doc")) {
            throw new IllegalArgumentException("目前不支援舊版 .doc 格式，請另存成 .docx 後再上傳");
        } else {
            throw new IllegalArgumentException("不支援的檔案格式，目前只接受 .pdf 或 .docx");
        }
    }

    private String extractPdf(MultipartFile file) throws IOException {
        try (InputStream in = file.getInputStream();
             PDDocument document = Loader.loadPDF(in.readAllBytes())) {

            if (document.isEncrypted()) {
                throw new IllegalArgumentException("這份 PDF 有加密保護，請先解除密碼再上傳");
            }

            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException(
                        "沒有從 PDF 抽到任何文字，這可能是掃描版/圖片型 PDF（目前尚未支援 OCR）");
            }
            return text;
        }
    }

    private String extractDocx(MultipartFile file) throws IOException {
        try (InputStream in = file.getInputStream();
             XWPFDocument document = new XWPFDocument(in);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {

            String text = extractor.getText();
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("沒有從 Word 檔案抽到任何文字內容");
            }
            return text;
        }
    }
}
