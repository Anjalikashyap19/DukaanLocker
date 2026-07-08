package com.shoplocker.fssai.service;

import com.shoplocker.fssai.exception.FailureCode;
import com.shoplocker.fssai.exception.FssaiException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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

    /** Cap on pages we will rasterize to prevent OOM from a malicious PDF. */
    private static final int MAX_PAGES = 20;
    /** 200 DPI is the sweet spot for Textract OCR accuracy vs file size. */
    private static final int RASTER_DPI = 200;

    public List<byte[]> rasterizeToJpegs(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new FssaiException(
                    "The uploaded PDF is empty. Please upload a non-empty file.",
                    FailureCode.INVALID_FILE_FORMAT);
        }
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
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
                // renderImage(page, scale) is the forward-compat form in PDFBox 3.x
                // (200 DPI / 72 base = ~2.78x scale; renderImageWithDPI is deprecated in 3.0.4).
                // Use the explicit 3-arg form (scale + color space) instead of the
                // deprecated renderImageWithDPI, but keep the same RGB output and 200 DPI.
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
            // subclass (PDFBox 3.x throws IllegalArgumentException / NPE for missing required
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
