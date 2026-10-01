package com.shoplocker.fssai.service;

import com.shoplocker.fssai.exception.FailureCode;
import com.shoplocker.fssai.exception.FssaiException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Server-side PDF rasterization fallback for PDFs that AWS Textract natively
 * rejects (PDF 2.0 in some regions, password-protected, AcroForms, corrupted
 * XREF, image-only-without-OCR, etc.). Rasterizes each page to a JPEG so
 * the caller can re-submit to Textract (which accepts JPEG/PNG natively).
 *
 * Failure-mode mapping (per code-review):
 * - password-protected PDF (InvalidPasswordException) -> 400 UNSUPPORTED_DOCUMENT_FORMAT
 * - corrupted PDF / PDFBox parse failure (IOException) -> 400 UNSUPPORTED_DOCUMENT_FORMAT
 * - too many pages (> MAX_PAGES) -> 400 UNSUPPORTED_DOCUMENT_FORMAT
 * - genuine rasterizer-internal failure (any other Exception) -> 500 PDF_PROCESSING_ERROR
 */
@Service
public class PdfPreprocessor {

    private static final Logger log = LoggerFactory.getLogger(PdfPreprocessor.class);

    /** Cap on pages we will rasterize to prevent OOM from a malicious PDF. */
    private static final int MAX_PAGES = 20;
    /** 200 DPI is the sweet spot for Textract OCR accuracy vs file size. */
    private static final int RASTER_DPI = 200;

    /**
     * Below this the "text layer" is a false positive - a PDF whose glyphs carry
     * no usable ToUnicode map can still emit a few hundred characters of noise.
     * Mirrors {@link DocumentValidationService#MIN_EXTRACTED_TEXT_LENGTH} so the
     * OCR floor and the text-layer floor can never drift apart.
     */
    private static final int MIN_TEXT_LAYER_CHARS = DocumentValidationService.MIN_EXTRACTED_TEXT_LENGTH;

    /**
     * Share of non-whitespace characters that must be letters or digits. A
     * normal certificate is ~90%+; a font with a broken encoding map scores far
     * below this and is handed back to the OCR pipeline instead.
     */
    private static final double MIN_READABLE_RATIO = 0.6;

    /**
     * Reads the embedded text layer of a born-digital PDF - the exact wording the
     * generator wrote, with no OCR noise, so licence numbers, authority names and
     * expiry dates survive verbatim. Government e-certificates (GST, PAN, state
     * trade licences, ...) are all generated this way.
     *
     * <p>Scanned and image-only PDFs yield nothing usable here; callers fall back
     * to the Tesseract/Textract rasterization pipeline, which stays the safety net.</p>
     *
     * <p>Never throws: an unreadable, encrypted or malformed PDF is reported as
     * {@link Optional#empty()} so this can only ever add coverage, never remove it.</p>
     */
    public Optional<String> extractTextLayer(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) return Optional.empty();
        try (PDDocument document = PDDocument.load(pdfBytes)) {
            // Same ceiling as the rasterizer: past it we let OCR report the
            // friendly page-limit error rather than returning a huge text blob.
            if (document.getNumberOfPages() > MAX_PAGES) return Optional.empty();

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = normalizeExtractedText(stripper.getText(document));

            if (text.length() < MIN_TEXT_LAYER_CHARS) return Optional.empty();
            if (readableRatio(text) < MIN_READABLE_RATIO) return Optional.empty();
            return Optional.of(text);
        } catch (InvalidPasswordException e) {
            log.debug("PDF is password-protected; no text layer available");
            return Optional.empty();
        } catch (Exception e) {
            log.debug("Could not read a PDF text layer ({}); falling back to OCR",
                    e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Collapses the layout noise a text extractor emits without touching the
     * content the validators match on:
     * <ul>
     *   <li>line breaks / runs of spaces become a single space, so keywords that
     *       straddle a line break still match as substrings;</li>
     *   <li>whitespace after a hyphen is dropped, repairing a date split across a
     *       soft wrap ("03-<br>Oct-2026" &rarr; "03-Oct-2026") that would
     *       otherwise break every date pattern in {@link ExpiryDateExtractor}.</li>
     * </ul>
     */
    private String normalizeExtractedText(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        return raw
                // En/em dashes and both Unicode hyphens are used as date separators
                // by some generators; the validators only know ASCII '-'.
                .replace('\u2010', '-')
                .replace('\u2011', '-')
                .replace('\u2013', '-')
                .replaceAll("\\s+", " ")
                .replaceAll("(?<=-)\\s+", "")
                .trim();
    }

    private double readableRatio(String text) {
        int total = 0;
        int readable = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) continue;
            total++;
            if (Character.isLetterOrDigit(c)) readable++;
        }
        return total == 0 ? 0.0 : (double) readable / total;
    }

    public List<byte[]> rasterizeToJpegs(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new FssaiException(
                    "The uploaded PDF is empty. Please upload a non-empty file.",
                    FailureCode.INVALID_FILE_FORMAT);
        }
        try (PDDocument document = PDDocument.load(pdfBytes)) {
            int pageCount = document.getNumberOfPages();
            if (pageCount > MAX_PAGES) {
                throw new FssaiException(
                        "The uploaded PDF has " + pageCount + " pages, which exceeds the "
                                + MAX_PAGES + "-page limit. Please upload a shorter document.",
                        FailureCode.UNSUPPORTED_DOCUMENT_FORMAT);
            }
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            List<byte[]> images = new ArrayList<>(pageCount);
            for (int page = 0; page < pageCount; page++) {
                // renderImage(page, scale, ImageType) keeps 200 DPI RGB output (200 / 72 = ~2.78x).
                BufferedImage bim = pdfRenderer.renderImage(page, RASTER_DPI / 72.0f, ImageType.RGB);
                try {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(bim, "JPEG", baos);
                    images.add(baos.toByteArray());
                } finally {
                    bim.flush();
                }
            }
            return images;
        } catch (FssaiException e) {
            // Re-throw our own exceptions (page-count cap, empty bytes, etc.) unchanged.
            // MUST come before the multi-catch below - FssaiException is a RuntimeException,
            // so the multi-catch would otherwise swallow it.
            throw e;
        } catch (InvalidPasswordException e) {
            throw new FssaiException(
                    "The uploaded PDF is password-protected. Please remove the password and try again.",
                    FailureCode.UNSUPPORTED_DOCUMENT_FORMAT, e);
        } catch (IOException | RuntimeException e) {
            // PDFBox parse failure: IOException (corrupted bytes) or RuntimeException
            // subclass (PDFBox throws IllegalArgumentException / NPE for missing required
            // PDF structure). All indicate a client-bad PDF - map to 400, not 500.
            throw new FssaiException(
                    "The uploaded PDF appears to be corrupted and could not be processed. "
                            + "Please re-export the file (Print > Save as PDF) and try again.",
                    FailureCode.UNSUPPORTED_DOCUMENT_FORMAT, e);
        } catch (Exception e) {
            // Genuine rasterizer-internal failure (checked Exception that's not IOException,
            // OOM, native lib crash, etc.) - 500.
            throw new FssaiException(
                    "Our PDF processing pipeline failed unexpectedly. Please try again in a few minutes.",
                    FailureCode.PDF_PROCESSING_ERROR, e);
        }
    }
}
