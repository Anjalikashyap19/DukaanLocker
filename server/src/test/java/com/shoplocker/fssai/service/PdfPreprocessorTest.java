package com.shoplocker.fssai.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PdfPreprocessor text layer extraction")
class PdfPreprocessorTest {

    private final PdfPreprocessor preprocessor = new PdfPreprocessor();

    /** Long enough to clear {@link DocumentValidationService#MIN_EXTRACTED_TEXT_LENGTH}. */
    private static final String LINE_1 =
            "PERMANENT CERTIFICATE OF ENLISTMENT issued by Baranagar (Municipality) "
                    + "to KAVINDER SEHGAL, TRADE LICENCE NO: 0917P1008125372790, this "
                    + "certificate will be in force until the 03-Oct-";
    private static final String LINE_2 = "2026 and is liable to be produced at the time of renewal.";

    @Test
    @DisplayName("reads the embedded text of a born-digital PDF")
    void readsEmbeddedTextLayer() throws Exception {
        byte[] pdf = pdfWithText(LINE_1, LINE_2);

        Optional<String> text = preprocessor.extractTextLayer(pdf);

        assertThat(text).isPresent();
        assertThat(text.get()).contains("0917P1008125372790");
        assertThat(text.get()).contains("Municipality");
        // A date split across two text lines must be re-joined, or every date
        // pattern in ExpiryDateExtractor misses it.
        assertThat(text.get()).contains("03-Oct-2026");
        // Newlines are collapsed so keywords straddling a line break still match.
        assertThat(text.get()).doesNotContain("\n");
    }

    @Test
    @DisplayName("falls back to OCR when the PDF has no text at all")
    void emptyWhenThereIsNoTextLayer() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);

            assertThat(preprocessor.extractTextLayer(out.toByteArray())).isEmpty();
        }
    }

    @Test
    @DisplayName("never throws on a password-protected PDF")
    void neverThrowsOnEncryptedPdf() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            AccessPermission ap = new AccessPermission();
            doc.protect(new StandardProtectionPolicy("", "secret", ap));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);

            assertThat(preprocessor.extractTextLayer(out.toByteArray())).isEmpty();
        }
    }

    @Test
    @DisplayName("never throws on bytes that are not a PDF")
    void neverThrowsOnGarbage() {
        assertThat(preprocessor.extractTextLayer("not a pdf at all".getBytes())).isEmpty();
        assertThat(preprocessor.extractTextLayer(null)).isEmpty();
        assertThat(preprocessor.extractTextLayer(new byte[0])).isEmpty();
    }

    private byte[] pdfWithText(String... lines) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 11);
                cs.setLeading(14);
                cs.newLineAtOffset(50, 750);
                for (String line : lines) {
                    cs.showText(line);
                    cs.newLine();
                }
                cs.endText();
            }
            doc.save(out);
            return out.toByteArray();
        }
    }
}
