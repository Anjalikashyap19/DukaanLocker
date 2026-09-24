package com.shoplocker.fssai.service;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.shoplocker.fssai.exception.FailureCode;
import com.shoplocker.fssai.exception.FssaiException;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Self-hosted OCR service using Tesseract via Tess4J.
 * Replaces AWS Textract for document text extraction with zero cloud dependency.
 *
 * <p>Requires Tesseract binary installed on the host:
 * <ul>
 *   <li>Ubuntu/Debian: {@code sudo apt install tesseract-ocr tesseract-ocr-eng}</li>
 *   <li>macOS: {@code brew install tesseract}</li>
 *   <li>Windows: UB Mannheim Tesseract releases</li>
 * </ul>
 *
 * <p>Flow:
 * <ol>
 *   <li>Rasterize PDF to per-page JPEG images via {@link PdfPreprocessor}</li>
 *   <li>Run Tesseract OCR on each page image</li>
 *   <li>Concatenate extracted text</li>
 * </ol>
 */
@Service
public class TesseractOcrService {

    private static final Logger log = LoggerFactory.getLogger(TesseractOcrService.class);

    private final PdfPreprocessor pdfPreprocessor;
    private final ITesseract tesseract;

    public TesseractOcrService(PdfPreprocessor pdfPreprocessor) {
        this.pdfPreprocessor = pdfPreprocessor;
        this.tesseract = createTesseractInstance();
    }

    /**
     * Extracts text from PDF bytes using Tesseract OCR.
     *
     * @param fileBytes PDF file content
     * @param fileName  original file name (for error messages)
     * @return extracted text from all pages
     */
    public String extractText(byte[] fileBytes, String fileName) {
        if (fileBytes == null || fileBytes.length == 0) {
            throw new FssaiException(
                    "The uploaded file \"" + (fileName == null ? "" : fileName)
                            + "\" is empty. Please upload a non-empty PDF document.",
                    FailureCode.INVALID_FILE_FORMAT);
        }

        try {
            // Step 1: Rasterize PDF to per-page JPEG images using existing PdfPreprocessor
            List<byte[]> pageImages = pdfPreprocessor.rasterizeToJpegs(fileBytes);

            if (pageImages.isEmpty()) {
                throw new FssaiException(
                        "We couldn't read any pages from \"" + fileName + "\".",
                        FailureCode.UNSUPPORTED_DOCUMENT_FORMAT);
            }

            // Step 2: Run Tesseract on each page image
            StringBuilder sb = new StringBuilder();
            int pageNum = 1;

            for (byte[] imageBytes : pageImages) {
                log.debug("Running Tesseract OCR on page {} ({} KB)", pageNum, imageBytes.length / 1024);
                sb.append("\n--- Page ").append(pageNum++).append(" ---\n");

                // Write image to temp file (Tesseract works with files, not byte arrays)
                Path tempImage = null;
                try {
                    tempImage = Files.createTempFile("dukaan-ocr-", ".jpg");
                    Files.write(tempImage, imageBytes);

                    String pageText = tesseract.doOCR(tempImage.toFile());
                    if (pageText != null) {
                        sb.append(pageText.trim());
                    }
                } finally {
                    // Clean up temp file
                    if (tempImage != null) {
                        try {
                            Files.deleteIfExists(tempImage);
                        } catch (IOException ignored) {
                        }
                    }
                }
            }

            String result = sb.toString().trim();
            if (result.isEmpty()) {
                throw new FssaiException(
                        "We couldn't read any text from \"" + fileName
                                + "\". The file may be an image-only PDF without readable text, or scanned at too low a resolution.",
                        FailureCode.UNSUPPORTED_DOCUMENT_FORMAT);
            }

            log.info("Tesseract OCR extracted {} characters from {} pages of \"{}\"",
                    result.length(), pageImages.size(), fileName);
            return result;

        } catch (FssaiException e) {
            throw e;
        } catch (TesseractException e) {
            throw new FssaiException(
                    "Our OCR engine encountered an error while reading your document. Please try again.",
                    FailureCode.TEXTRACT_FAILURE, e);
        } catch (Exception e) {
            throw new FssaiException(
                    "Our document processing pipeline failed unexpectedly. Please try again in a few minutes.",
                    FailureCode.PDF_PROCESSING_ERROR, e);
        }
    }

    /**
     * Creates a Tesseract instance with optimal settings for Indian compliance documents.
     */
    private ITesseract createTesseractInstance() {
        Tesseract tess = new Tesseract();

        // Try to find tessdata directory automatically
        String tessdataPath = findTessDataPath();
        if (tessdataPath != null) {
            tess.setDatapath(tessdataPath);
            log.info("Tesseract tessdata path: {}", tessdataPath);
        } else {
            log.warn("Could not find tessdata directory. OCR may fail. " +
                     "Install tesseract-ocr-eng package or set TESSDATA_PATH environment variable.");
        }

        // Language: English (sufficient for Indian compliance documents)
        tess.setLanguage("eng");

        return tess;
    }

    /**
     * Finds the tessdata directory on the system.
     * Searches common installation paths for Linux, macOS, and Windows.
     */
    private String findTessDataPath() {
        // Check environment variable first
        String envPath = System.getenv("TESSDATA_PATH");
        if (envPath != null && !envPath.isEmpty() && new File(envPath).exists()) {
            return envPath;
        }

        // Common tessdata paths
        String[] candidates = {
                "/usr/share/tesseract-ocr/5/tessdata",
                "/usr/share/tesseract-ocr/4/tessdata",
                "/usr/share/tessdata",
                "/usr/local/share/tessdata",
                "/opt/homebrew/share/tessdata",
                "C:/Program Files/Tesseract-OCR/tessdata",
                "C:/Program Files (x86)/Tesseract-OCR/tessdata"
        };

        for (String path : candidates) {
            if (new File(path).exists()) {
                return path;
            }
        }

        return null;
    }
}
