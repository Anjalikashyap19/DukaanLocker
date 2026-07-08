package com.shoplocker.fssai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.shoplocker.fssai.exception.FailureCode;
import com.shoplocker.fssai.exception.FssaiException;
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
 * Wraps AWS Textract's DetectDocumentText with a server-side PDF rasterization
 * fallback. When Textract natively rejects a PDF (PDF 2.0 in some regions,
 * password-protected, AcroForms, corrupted XREF, image-only-without-OCR),
 * we rasterize the PDF to per-page JPEGs locally via PDFBox and re-submit
 * each page image to Textract, which accepts JPEG/PNG natively.
 */
@Service
public class TextractService {

    private static final Logger log = LoggerFactory.getLogger(TextractService.class);

    private final TextractClient textractClient;
    private final PdfPreprocessor pdfPreprocessor;

    public TextractService(TextractClient textractClient, PdfPreprocessor pdfPreprocessor) {
        this.textractClient = textractClient;
        this.pdfPreprocessor = pdfPreprocessor;
    }

    public String extractText(byte[] fileBytes, String fileName) {
        if (fileBytes == null || fileBytes.length == 0) {
            throw new FssaiException(
                    "The uploaded file \"" + (fileName == null ? "" : fileName)
                            + "\" is empty. Please upload a non-empty PDF document.",
                    FailureCode.INVALID_FILE_FORMAT);
        }

        try {
            String primaryText = detectViaTextract(fileBytes);
            if (!primaryText.isEmpty()) {
                return primaryText;
            }
            // Primary path returned no text - may be a problematic PDF that Textract
            // silently returned 0 LINE blocks for. Try the fallback.
            return fallbackViaRasterizedImages(fileBytes, fileName);

        } catch (FssaiException e) {
            // Top-level guard: re-throw FssaiException unchanged so the specific
            // failure code (400 UNSUPPORTED_DOCUMENT_FORMAT, 400 INVALID_FILE_FORMAT)
            // propagates to GlobalExceptionHandler. Without this, the broad
            // `catch (Exception e)` below would re-map every FssaiException to
            // 502 TEXTRACT_FAILURE.
            throw e;
        } catch (UnsupportedDocumentException primaryRejection) {
            // Textract explicitly rejected the PDF. The bytes are rejected every time
            // (deterministic) - no retry would help. Fall back to per-page image OCR.
            log.warn("Textract rejected PDF ({} bytes, name={}); falling back to rasterization",
                    fileBytes.length, fileName);
            try {
                return fallbackViaRasterizedImages(fileBytes, fileName);
            } catch (UnsupportedDocumentException fallbackRejection) {
                // Textract rejected the rasterized JPEG too - file is fundamentally
                // unprocessable. Return the actionable 400.
                throw new FssaiException(
                        "This PDF uses features our system can't read. The easiest fix: open the file, select 'Print', choose 'Save as PDF', and upload the flattened file.",
                        FailureCode.UNSUPPORTED_DOCUMENT_FORMAT, fallbackRejection);
            } catch (Exception rasterizerCrash) {
                // Genuine rasterizer-internal failure (OOM, native lib, etc.) - 500.
                throw new FssaiException(
                        "Our PDF processing pipeline failed unexpectedly. Please try again in a few minutes.",
                        FailureCode.PDF_PROCESSING_ERROR, rasterizerCrash);
            }
        } catch (Exception e) {
            // Throttling, IAM, size, network - all upstream Textract issues, not file-format.
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
            // Rasterized successfully but OCR returned 0 LINE blocks - most likely an
            // image-only PDF without an OCR layer. This is a document-format issue
            // (UNSUPPORTED_DOCUMENT_FORMAT), not an upload-malformed issue.
            throw new FssaiException(
                    "We couldn't read any text from \"" + fileName
                            + "\". The file may be an image-only PDF without an OCR layer, or scanned at too low a resolution.",
                    FailureCode.UNSUPPORTED_DOCUMENT_FORMAT);
        }
        return result;
    }
}
