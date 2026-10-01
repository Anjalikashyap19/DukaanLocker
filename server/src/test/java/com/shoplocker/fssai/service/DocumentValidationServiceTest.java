package com.shoplocker.fssai.service;

import com.shoplocker.fssai.entity.DocumentType;
import com.shoplocker.fssai.exception.FailureCode;
import com.shoplocker.fssai.exception.FssaiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers both halves of the upload-time business checks:
 * <ol>
 *   <li>wrong document type (uploading an FSSAI licence where an MSME cert is
 *       expected) — the check that already existed but had no tests;</li>
 *   <li>right document type but issued to a different business.</li>
 * </ol>
 *
 * <p>OCR is stubbed at the {@link TextractService} seam, so these tests assert
 * matching logic rather than Tesseract's accuracy, and need no real PDF.</p>
 */
class DocumentValidationServiceTest {

    private static final String FILE = "doc.pdf";

    /**
     * A plausible FSSAI licence body: long enough to clear
     * {@code MIN_EXTRACTED_TEXT_LENGTH}, and carrying the FSSAI signatures that
     * make a genuine licence identifiable.
     */
    private static final String FSSAI_TEXT = """
            FOOD SAFETY AND STANDARDS AUTHORITY OF INDIA
            FSSAI License
            This license is granted to the establishment named below, subject to
            the provisions of the Food Safety and Standards Act, 2006 and the rules
            made thereunder. The license is valid for the period stated against it.
            Name of Food Business Operator: %s
            Address: %s
            License Number: 11223344556677
            Date of Issue: 01/04/2024
            Valid up to: 31/03/2027
            """;

    /** A GST certificate - distinct signatures from the FSSAI text above. */
    private static final String GST_TEXT = """
            Goods and Services Tax
            GSTIN: 29ABCDE1234F1Z5
            Certificate of Registration
            Name of the registered person: %s
            This is to certify that the above business is registered under the
            Goods and Services Tax Act and the registration is effective from the
            date mentioned against it.
            """;

    /**
     * A West Bengal Certificate of Enlistment - the shape of trade licence that
     * the strict "Municipal Corporation" requirement used to reject. Issued by a
     * <b>Municipality</b> (not a Corporation), numbered as a {@code TRADE LICENCE
     * NO} (never "License Number"), and worded as "in force until" rather than
     * "valid till".
     */
    private static final String WB_ENLISTMENT_TEXT = """
            GOVERNMENT OF WEST BENGAL
            PERMANENT CERTIFICATE OF ENLISTMENT
            Form-24, Rule 82, Sec 118, West Bengal Municipal Act, 1993
            Issuing Authority: Baranagar (Municipality)
            TRADE LICENCE NO: 0917P1008125372790
            Name of the Enlistee: KAVINDER SEHGAL
            Business: MOTOR SPARE PARTS AND LUBRICATING OIL
            This certificate will be in force until the 03-Oct-2026 and is liable
            to be produced at the time of renewal.
            Date of Issue: 04-Oct-2025
            """;

    /**
     * A neutral certificate that matches no other document type's signatures, so
     * it reaches the trade-licence validator instead of the conflict check.
     */
    private static final String UNRELATED_CERTIFICATE_TEXT = """
            Certificate of Registration
            This is to certify that the holder named below has been registered
            under the applicable rules and the entry stands in the register.
            Name of the Holder: Maruthi Traders
            Registration Number: ABC-12345/2024
            Date of Issue: 01/04/2024
            Valid upto: 31/03/2027
            """;

    private DocumentValidationService service;

    @BeforeEach
    void setUp() {
        service = new DocumentValidationService();
        ReflectionTestUtils.setField(service, "businessNameCheckEnabled", true);
    }

    /** Drives the public entry point the controller uses, with OCR stubbed out. */
    private void runOcrAndValidate(DocumentType type, String ocrText) {
        TextractService textract = mock(TextractService.class);
        when(textract.extractText(any(byte[].class), anyString())).thenReturn(ocrText);
        ReflectionTestUtils.setField(service, "textractService", textract);
        service.validateContentWithOcr(type, new byte[]{1, 2, 3}, FILE);
    }

    private String checkOwnership(String shopName, String docText) {
        service.validateBusinessOwnership(
                DocumentType.FSSAI_FOOD_LICENSE, shopName, docText, FILE);
        return docText;
    }

    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("1. wrong document type")
    class WrongType {

        @Test
        @DisplayName("rejects an FSSAI licence uploaded where an MSME cert is expected")
        void rejectsFssaiWhereMsmeExpected() {
            String fssaiLicence = FSSAI_TEXT.formatted("cafe Coffee Day", "MG Road, Bengaluru");

            assertThatThrownBy(() -> runOcrAndValidate(DocumentType.MSME_CERTIFICATE, fssaiLicence))
                    .isInstanceOf(FssaiException.class)
                    .hasMessageContaining("doesn't look like")
                    .hasMessageContaining("Udyam MSME Registration")
                    .hasMessageContaining("FSSAI Food License")
                    .satisfies(ex -> assertThat(((FssaiException) ex).getFailureCode())
                            .isEqualTo(FailureCode.DOCUMENT_TYPE_MISMATCH));
        }

        @Test
        @DisplayName("rejects a GST certificate uploaded as an FSSAI licence")
        void rejectsGstUploadedAsFssai() {
            String gst = GST_TEXT.formatted("Maruthi Traders");

            assertThatThrownBy(() -> runOcrAndValidate(DocumentType.FSSAI_FOOD_LICENSE, gst))
                    .isInstanceOf(FssaiException.class)
                    .hasMessageContaining("GST Registration Certificate")
                    .satisfies(ex -> assertThat(((FssaiException) ex).getFailureCode())
                            .isEqualTo(FailureCode.DOCUMENT_TYPE_MISMATCH));
        }

        @Test
        @DisplayName("accepts the matching document type")
        void acceptsMatchingType() {
            assertThatCode(() -> runOcrAndValidate(DocumentType.FSSAI_FOOD_LICENSE,
                    FSSAI_TEXT.formatted("cafe Coffee Day", "MG Road")))
                    .doesNotThrowAnyException();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("2. business ownership")
    class BusinessOwnership {

        @Test
        @DisplayName("blocks a certificate issued to a different business")
        void blocksAnotherBusiness() {
            String otherBusiness = FSSAI_TEXT.formatted(
                    "Maruthi Traders Private Limited", "12 MG Road, Bengaluru");

            assertThatThrownBy(() -> checkOwnership("cafe Coffee Day", otherBusiness))
                    .isInstanceOf(FssaiException.class)
                    .hasMessageContaining("does not appear to belong to your shop")
                    .satisfies(ex -> assertThat(((FssaiException) ex).getFailureCode())
                            .isEqualTo(FailureCode.BUSINESS_NAME_MISMATCH));
        }

        @Test
        @DisplayName("blocks a completely unrelated GST certificate")
        void blocksUnrelatedGst() {
            assertThatThrownBy(() -> checkOwnership("cafe Coffee Day",
                    GST_TEXT.formatted("Sri Venkateshwara Enterprises")))
                    .isInstanceOf(FssaiException.class)
                    .satisfies(ex -> assertThat(((FssaiException) ex).getFailureCode())
                            .isEqualTo(FailureCode.BUSINESS_NAME_MISMATCH));
        }

        @Test
        @DisplayName("accepts the shop's own certificate")
        void acceptsOwnCertificate() {
            assertThatCode(() -> checkOwnership("cafe Coffee Day",
                    FSSAI_TEXT.formatted("cafe Coffee Day", "MG Road, Bengaluru")))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("accepts when the certificate carries extra legal suffixes")
        void acceptsLegalSuffixes() {
            assertThatCode(() -> checkOwnership("Maruthi Traders",
                    FSSAI_TEXT.formatted("Maruthi Traders Private Limited", "MG Road")))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("accepts when the certificate adds a descriptive word")
        void acceptsDescriptiveWord() {
            // Profile says "cafe Coffee Day", the licence prints the fuller
            // "cafe Coffee Day Foods". A substring check would wrongly reject this.
            assertThatCode(() -> checkOwnership("cafe Coffee Day",
                    FSSAI_TEXT.formatted("cafe Coffee Day Foods Pvt Ltd", "MG Road")))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("tolerates case, punctuation and ampersand differences")
        void toleratesFormatting() {
            assertThatCode(() -> checkOwnership("Sharma & Sons",
                    FSSAI_TEXT.formatted("SHARMA AND SONS", "MG Road")))
                    .doesNotThrowAnyException();
            assertThatCode(() -> checkOwnership("sharma-sons",
                    FSSAI_TEXT.formatted("Sharma Sons", "MG Road")))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("survives a single misread character in a one-word shop name")
        void toleratesSingleCharOcrNoise() {
            // "Verma" rendered as "Nerma": one substitution, and the name is long
            // enough that the tolerance is safe to apply.
            assertThatCode(() -> checkOwnership("Verma",
                    FSSAI_TEXT.formatted("Nerma Traders", "MG Road")))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("does not let a short name word match unrelated text")
        void shortNameWordsStayExact() {
            // "cafe Coffee Day" reduces to {coffee, day}; the 3-letter token must
            // stay exact, or "day" would fuzzy-match "Date" in any licence and let
            // every other business through.
            assertThatThrownBy(() -> checkOwnership("cafe Coffee Day",
                    FSSAI_TEXT.formatted("Maruthi Traders Private Limited", "MG Road")))
                    .isInstanceOf(FssaiException.class)
                    .satisfies(ex -> assertThat(((FssaiException) ex).getFailureCode())
                            .isEqualTo(FailureCode.BUSINESS_NAME_MISMATCH));
        }

        @Test
        @DisplayName("does not block on a name made only of generic words")
        void ignoresGenericTokens() {
            // Nothing distinctive to require, so an unrelated document must not
            // be blocked - otherwise generic shop names break onboarding entirely.
            assertThatCode(() -> checkOwnership("The Shop",
                    FSSAI_TEXT.formatted("Maruthi Traders", "MG Road")))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("never blocks when the profile has no shop name")
        void skipsWhenShopNameMissing() {
            assertThatCode(() -> checkOwnership(null,
                    FSSAI_TEXT.formatted("Anyone Else", "MG Road")))
                    .doesNotThrowAnyException();
            assertThatCode(() -> checkOwnership("   ",
                    FSSAI_TEXT.formatted("Anyone Else", "MG Road")))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("never treats a bad scan as a wrong business")
        void skipsWhenOcrTextTooShort() {
            assertThatCode(() -> checkOwnership("cafe Coffee Day", "too short"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("can be disabled without a deploy")
        void canBeDisabled() {
            ReflectionTestUtils.setField(service, "businessNameCheckEnabled", false);
            String otherBusiness = FSSAI_TEXT.formatted(
                    "Maruthi Traders Private Limited", "MG Road");

            assertThatCode(() -> service.validateBusinessOwnership(
                    DocumentType.FSSAI_FOOD_LICENSE, "cafe Coffee Day", otherBusiness, FILE))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("accepts a match on the proprietor's name alone")
        void acceptsProprietorName() {
            // A state trade licence is commonly issued in the proprietor's
            // personal name while the profile carries the brand name.
            assertThatCode(() -> service.validateBusinessOwnership(
                    DocumentType.TRADE_LICENSE, "New Market", "Kavinder Sehgal",
                    WB_ENLISTMENT_TEXT, FILE))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("still blocks when neither the shop nor the proprietor appears")
        void blocksWhenNeitherNameAppears() {
            assertThatThrownBy(() -> service.validateBusinessOwnership(
                    DocumentType.TRADE_LICENSE, "New Market", "Diya",
                    WB_ENLISTMENT_TEXT, FILE))
                    .isInstanceOf(FssaiException.class)
                    .hasMessageContaining("does not appear to belong to your shop")
                    .satisfies(ex -> assertThat(((FssaiException) ex).getFailureCode())
                            .isEqualTo(FailureCode.BUSINESS_NAME_MISMATCH));
        }

        @Test
        @DisplayName("does not match a name token buried inside a longer word")
        void doesNotMatchInsideLongerWords() {
            // "New Market" reduces to {new, market}; "new" occurs inside every
            // "renewal" clause of a licence. An interior match is a coincidence
            // of spelling, never an identity.
            String renewalProse = """
                    Certificate of Enlistment issued under the Municipal Act
                    The licence is renewable on or before the due date and must be
                    produced at the time of renewal. Renewal fees are payable
                    annually at the municipal office of the district.
                    Name of the Enlistee: Maruthi Traders
                    Licence No: 12345678901234
                    Date of Issue: 01/04/2024
                    Valid upto: 31/03/2027
                    """;

            assertThatThrownBy(() -> checkOwnership("New Market", renewalProse))
                    .isInstanceOf(FssaiException.class)
                    .satisfies(ex -> assertThat(((FssaiException) ex).getFailureCode())
                            .isEqualTo(FailureCode.BUSINESS_NAME_MISMATCH));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("3. trade licence validation")
    class TradeLicenseValidation {

        @Test
        @DisplayName("accepts a West Bengal Certificate of Enlistment")
        void acceptsWestBengalEnlistment() {
            // Issued by a Municipality rather than a Municipal Corporation, and
            // numbered "TRADE LICENCE NO" rather than "License Number" - the
            // exact wording that used to return 422.
            assertThatCode(() -> runOcrAndValidate(DocumentType.TRADE_LICENSE,
                    WB_ENLISTMENT_TEXT))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("rejects a certificate that is not a trade licence at all")
        void rejectsUnrelatedCertificate() {
            assertThatThrownBy(() -> runOcrAndValidate(DocumentType.TRADE_LICENSE,
                    UNRELATED_CERTIFICATE_TEXT))
                    .isInstanceOf(FssaiException.class)
                    .hasMessageContaining("not a valid Trade License")
                    .hasMessageContaining("Document title")
                    .satisfies(ex -> assertThat(((FssaiException) ex).getFailureCode())
                            .isEqualTo(FailureCode.DOCUMENT_VALIDATION_FAILED));
        }

        @Test
        @DisplayName("still surfaces the type mismatch for an FSSAI licence")
        void stillRejectsFssaiAsTradeLicense() {
            assertThatThrownBy(() -> runOcrAndValidate(DocumentType.TRADE_LICENSE,
                    FSSAI_TEXT.formatted("cafe Coffee Day", "MG Road")))
                    .isInstanceOf(FssaiException.class)
                    .hasMessageContaining("FSSAI Food License")
                    .satisfies(ex -> assertThat(((FssaiException) ex).getFailureCode())
                            .isEqualTo(FailureCode.DOCUMENT_TYPE_MISMATCH));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("4. content hashing (the cross-shop duplicate-document key)")
    class ContentHash {

        @Test
        @DisplayName("matches the published SHA-256 test vectors")
        void matchesKnownVectors() {
            assertThat(DocumentValidationService.sha256Hex(new byte[0]))
                    .isEqualTo("e3b0c44298fc1c149afbf4c8996fb924"
                            + "27ae41e4649b934ca495991b7852b855");
            assertThat(DocumentValidationService.sha256Hex("abc".getBytes()))
                    .isEqualTo("ba7816bf8f01cfea414140de5dae2223"
                            + "b00361a396177a9cb410ff61f20015ad");
        }

        @Test
        @DisplayName("is lower-case hex of exactly 64 characters")
        void isLowerHexDigest() {
            String hash = DocumentValidationService.sha256Hex(WB_ENLISTMENT_TEXT.getBytes());
            assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
        }

        @Test
        @DisplayName("is deterministic, so the same file always produces the same key")
        void isDeterministic() {
            byte[] first = WB_ENLISTMENT_TEXT.getBytes();
            byte[] second = WB_ENLISTMENT_TEXT.getBytes();
            assertThat(DocumentValidationService.sha256Hex(first))
                    .isEqualTo(DocumentValidationService.sha256Hex(second));
        }

        @Test
        @DisplayName("a single byte change produces a different key")
        void differsForDifferentContent() {
            byte[] a = "trade licence A".getBytes();
            byte[] b = "trade licence B".getBytes();
            assertThat(DocumentValidationService.sha256Hex(a))
                    .isNotEqualTo(DocumentValidationService.sha256Hex(b));
        }

        @Test
        @DisplayName("null input yields null, not a hash of nothing")
        void nullInputYieldsNull() {
            assertThat(DocumentValidationService.sha256Hex(null)).isNull();
        }
    }
}
