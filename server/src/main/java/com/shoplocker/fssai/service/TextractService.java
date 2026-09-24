package com.shoplocker.fssai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.shoplocker.fssai.exception.FailureCode;
import com.shoplocker.fssai.exception.FssaiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.Block;
import software.amazon.awssdk.services.textract.model.BlockType;
import software.amazon.awssdk.services.textract.model.DetectDocumentTextRequest;
import software.amazon.awssdk.services.textract.model.DetectDocumentTextResponse;
import software.amazon.awssdk.services.textract.model.Document;
import software.amazon.awssdk.services.textract.model.UnsupportedDocumentException;

import java.util.List;

/**
 * OCR service that delegates to either AWS Textract or local Tesseract
 * based on the {@code app.ocr.provider} configuration property.
 *
 * <ul>
 *   <li>{@code tesseract} — self-hosted Tesseract OCR via Tess4J (default, free, no AWS)</li>
 *   <li>{@code textract} — AWS Textract API (requires AWS credentials + per-page cost)</li>
 * </ul>
 *
 * <p>The public API is a single method: {@link #extractText(byte[], String)}.
 * Callers ({@link DocumentValidationService}) do not need to know which backend is used.
 */
@Service
public class TextractService {

    private static final Logger log = LoggerFactory.getLogger(TextractService.class);

    private final PdfPreprocessor pdfPreprocessor;
    private final TesseractOcrService tesseractOcrService;
    private final TextractClient textractClient;

    @Value("${app.ocr.provider:tesseract}")
    private String ocrProvider;

    public TextractService(PdfPreprocessor pdfPreprocessor,
                           TesseractOcrService tesseractOcrService,
                           @Autowired(required = false) TextractClient textractClient) {
        this.pdfPreprocessor = pdfPreprocessor;
        this.tesseractOcrService = tesseractOcrService;
        this.textractClient = textractClient;
    }

    /**
     * Extracts text from a PDF document using the configured OCR provider.
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

        if ("textract".equalsIgnoreCase(ocrProvider)) {
            log.debug("Using AWS Textract for OCR");
            return extractViaTextract(fileBytes, fileName);
        } else {
            log.debug("Using local Tesseract for OCR");
            return tesseractOcrService.extractText(fileBytes, fileName);
        }
    }

    // ─── AWS Textract Path ──────────────────────────────────────────────────

    private String extractViaTextract(byte[] fileBytes, String fileName) {
        if (textractClient == null) {
            throw new FssaiException(
                    "AWS Textract is not configured. Please set app.ocr.provider=tesseract or configure AWS credentials.",
                    FailureCode.TEXTRACT_FAILURE);
        }
        try {
            String primaryText = detectViaTextract(fileBytes);
            if (!primaryText.isEmpty()) {
                return primaryText;
            }
            return fallbackViaRasterizedImages(fileBytes, fileName);

        } catch (FssaiException e) {
            throw e;
        } catch (UnsupportedDocumentException primaryRejection) {
            log.warn("Textract rejected PDF ({} bytes, name={}); falling back to rasterization",
                    fileBytes.length, fileName);
            try {
                return fallbackViaRasterizedImages(fileBytes, fileName);
            } catch (FssaiException preprocessorError) {
                throw preprocessorError;
            } catch (UnsupportedDocumentException fallbackRejection) {
                throw new FssaiException(
                        "This PDF uses features our system can't read. The easiest fix: open the file, select 'Print', choose 'Save as PDF', and upload the flattened file.",
                        FailureCode.UNSUPPORTED_DOCUMENT_FORMAT, fallbackRejection);
            } catch (Exception rasterizerCrash) {
                throw new FssaiException(
                        "Our PDF processing pipeline failed unexpectedly. Please try again in a few minutes.",
                        FailureCode.PDF_PROCESSING_ERROR, rasterizerCrash);
            }
        } catch (Exception e) {
            throw new FssaiException(
                    "We couldn't verify your file right now — our document verifier is temporarily unavailable. Please try again in a few minutes.",
                    FailureCode.TEXTRACT_FAILURE, e);
        }
    }

    private String detectViaTextract(byte[] fileBytes) {
        Document document = Document.builder().bytes(SdkBytes.fromByteArray(fileBytes)).build();
        DetectDocumentTextRequest request = DetectDocumentTextRequest.builder().document(document).build();
        DetectDocumentTextResponse response = textractClient.detectDocumentText(request);
        StringBuilder sb = new StringBuilder();
        for (Block block : response.blocks()) {
            if (BlockType.LINE.equals(block.blockType())) {
                sb.append(block.text()).append('\n');
            }
        }
        return sb.toString();
    }

    private String fallbackViaRasterizedImages(byte[] fileBytes, String fileName) {
        List<byte[]> pageImages = pdfPreprocessor.rasterizeToJpegs(fileBytes);
        StringBuilder sb = new StringBuilder();
        int pageNum = 1;
        log.debug("Rasterization fallback produced {} page images", pageImages.size());
        for (byte[] imageBytes : pageImages) {
            log.debug("Sending page {} to Textract ({} KB)", pageNum, imageBytes.length / 1024);
            sb.append("\n--- Page ").append(pageNum++).append(" ---\n");
            Document imgDoc = Document.builder().bytes(SdkBytes.fromByteArray(imageBytes)).build();
            DetectDocumentTextResponse res = textractClient.detectDocumentText(
                    DetectDocumentTextRequest.builder().document(imgDoc).build());
            for (Block block : res.blocks()) {
                if (BlockType.LINE.equals(block.blockType())) {
                    sb.append(block.text()).append('\n');
                }
            }
        }
        String result = sb.toString().trim();
        if (result.isEmpty()) {
            throw new FssaiException(
                    "We couldn't read any text from \"" + fileName
                            + "\". The file may be an image-only PDF without an OCR layer, or scanned at too low a resolution.",
                    FailureCode.UNSUPPORTED_DOCUMENT_FORMAT);
        }
        return result;
    }
}
