package com.intelliflow.service;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class OcrService {

    private final Tesseract tesseract;

    public OcrService() {
        this.tesseract = new Tesseract();
        this.tesseract.setDatapath("/opt/homebrew/share/tessdata");
        this.tesseract.setLanguage("eng");
    }

    public String extractText(File file, String fileType) throws IOException, TesseractException {
        if (fileType != null && fileType.contains("pdf")) {
            return extractFromPdf(file);
        } else {
            // For images (png, jpg, tiff, etc.)
            return tesseract.doOCR(file);
        }
    }

    private String extractFromPdf(File file) throws IOException, TesseractException {
        try (PDDocument document = Loader.loadPDF(file)) {
            // First try direct text extraction (works for digital PDFs)
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            // If we got meaningful text, return it
            if (text != null && text.trim().length() > 50) {
                return text.trim();
            }

            return ocrPdfPages(document);
        }
    }

    private String ocrPdfPages(PDDocument document) throws IOException, TesseractException {
        PDFRenderer renderer = new PDFRenderer(document);
        List<String> pages = new ArrayList<>();

        for (int i = 0; i < document.getNumberOfPages(); i++) {
            BufferedImage image = renderer.renderImageWithDPI(i, 300);
            String pageText = tesseract.doOCR(image);
            if (pageText != null && !pageText.trim().isEmpty()) {
                pages.add(pageText.trim());
            }
        }

        return String.join("\n\n--- Page Break ---\n\n", pages);
    }
}