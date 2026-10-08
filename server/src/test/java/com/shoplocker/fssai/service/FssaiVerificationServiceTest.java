package com.shoplocker.fssai.service;

import com.shoplocker.fssai.dto.FssaiVerificationResponse;
import com.shoplocker.fssai.util.FssaiHtmlGenerator;
import com.shoplocker.fssai.util.QrCodePng;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the FSSAI license merge rules:
 * <ul>
 *   <li>{@code lnchk.php} (details API) is the primary source for FBO / premises info.</li>
 *   <li>{@code check-details.php} (license API) is the ONLY source of the expiry date.</li>
 *   <li>Missing details API record falls back to the license API payload.</li>
 *   <li>Both payloads empty => "not found" error.</li>
 * </ul>
 * Payloads below are the real responses for license 21221160000115.
 */
public class FssaiVerificationServiceTest {

    private static final String DETAILS_JSON = """
            {
                "currentPageNo": 1,
                "totalPages": 0,
                "pageLimit": 10,
                "totalRecords": 0,
                "paginationListRecords": [
                    {
                        "premiseaddress": "D.NO.2-3-27 I, SRI NARAYANAGURU KALYANA MANTAP BUILDING, BANNANJE, UDUPI",
                        "licenseno": "21221160000115",
                        "licensecategoryname": "Registration",
                        "statename": "Karnataka",
                        "statusdesc": "Registration Certificate issued",
                        "licensecategoryid": 3,
                        "talukname": "Udupi",
                        "districtname": "Udupi ",
                        "companyname": "SHYAM SUNDAR / CANARA JUICE JUNCTION",
                        "licenseactiveflag": true,
                        "refid": 126988842,
                        "villagename": "Tenkanidyoor",
                        "premisepincode": 576101
                    }
                ]
            }
            """;

    private static final String LICENSE_JSON = """
            {
                "license": {
                    "LicenseNo": "21221160000115",
                    "fboId": "72892287",
                    "expiryDate": "10-09-2028",
                    "refId": 126988842
                },
                "details": {
                    "contactPerson": "SHYAM SUNDAR",
                    "companyName": "SHYAM SUNDAR / CANARA JUICE JUNCTION",
                    "statePremises": "KA",
                    "districtPremises": 354,
                    "pincodePremises": 576101,
                    "addressPremises": "D.NO.2-3-27 I, SRI NARAYANAGURU KALYANA MANTAP BUILDING, BANNANJE, UDUPI",
                    "kobname": "Trade/Retail - Retailer",
                    "panNo": null,
                    "contactEmail": "cyberinfotech1@gmail.com"
                }
            }
            """;

    private FssaiVerificationResponse merge(String detailsJson, String licenseJson) throws Exception {
        FssaiVerificationService service = new FssaiVerificationService(null);
        Method method = FssaiVerificationService.class
                .getDeclaredMethod("parseAndMerge", String.class, String.class, String.class);
        method.setAccessible(true);
        return (FssaiVerificationResponse) method.invoke(service, detailsJson, licenseJson, "21221160000115");
    }

    @Test
    void mergesBothPayloadsAndKeepsExpiryFromLicenseApi() throws Exception {
        FssaiVerificationResponse response = merge(DETAILS_JSON, LICENSE_JSON);

        assertTrue(response.isSuccess());
        assertEquals("21221160000115", response.getLicenseNumber());
        assertEquals("SHYAM SUNDAR / CANARA JUICE JUNCTION", response.getCompanyName());
        assertEquals("SHYAM SUNDAR", response.getContactPerson());
        assertEquals("Trade/Retail - Retailer", response.getKindOfBusiness());
        assertEquals("Basic License", response.getLicenseCategory());
        assertEquals("Registration Certificate issued", response.getStatus());
        assertEquals(Boolean.TRUE, response.getLicenseActive());
        assertEquals("Karnataka", response.getState());
        assertEquals("Udupi", response.getDistrict());       // trailing space trimmed
        assertEquals("Udupi", response.getTaluk());
        assertEquals("Tenkanidyoor", response.getVillage());
        assertEquals("576101", response.getPincode());
        assertEquals("cyberinfotech1@gmail.com", response.getContactEmail());
        assertEquals("126988842", response.getRefId());
        assertEquals("72892287", response.getFboId());
        assertEquals("10-09-2028", response.getExpiryDate()); // only the license API has this
    }

    @Test
    void detailsApiAloneStillVerifiesButWithoutExpiry() throws Exception {
        FssaiVerificationResponse response = merge(DETAILS_JSON, null);

        assertTrue(response.isSuccess());
        assertEquals("SHYAM SUNDAR / CANARA JUICE JUNCTION", response.getCompanyName());
        assertEquals("Registration Certificate issued", response.getStatus());
        assertNull(response.getExpiryDate());
        assertNull(response.getContactPerson());
    }

    @Test
    void licenseApiAloneFallsBackWhenDetailsApiHasNoRecord() throws Exception {
        FssaiVerificationResponse response = merge(
                "{\"currentPageNo\":1,\"totalPages\":0,\"totalRecords\":0,\"paginationListRecords\":[]}",
                LICENSE_JSON);

        assertTrue(response.isSuccess());
        assertEquals("SHYAM SUNDAR / CANARA JUICE JUNCTION", response.getCompanyName());
        assertEquals("KA", response.getState());
        assertEquals("576101", response.getPincode());
        assertEquals("10-09-2028", response.getExpiryDate());
    }

    @Test
    void bothPayloadsEmptyReportsNotFound() throws Exception {
        FssaiVerificationResponse response = merge(
                "{\"totalRecords\":0,\"paginationListRecords\":[]}", null);

        assertFalse(response.isSuccess());
        assertNotNull(response.getErrorMessage());
        assertTrue(response.getErrorMessage().toLowerCase().contains("not found"));
    }

    @Test
    void expiryDateParsesIntoDocumentExpiryColumn() {
        FssaiVerificationService service = new FssaiVerificationService(null);
        assertEquals(java.time.LocalDateTime.of(2028, 9, 10, 0, 0),
                service.parseExpiryDate("10-09-2028"));
        assertNull(service.parseExpiryDate(null));
        assertNull(service.parseExpiryDate("  "));
        assertNull(service.parseExpiryDate("not-a-date"));
    }

    private String buildHtml(FssaiVerificationResponse response) {
        return FssaiHtmlGenerator.generateCertificateHtml(
                response.getLicenseNumber(), response.getCompanyName(), response.getContactPerson(),
                response.getKindOfBusiness(), response.getLicenseCategory(), response.getStatus(),
                response.getLicenseActive(), response.getState(), response.getDistrict(),
                response.getTaluk(), response.getVillage(), response.getAddress(), response.getPincode(),
                response.getContactEmail(), response.getPanNo(), response.getExpiryDate(),
                response.getFboId(), response.getRefId());
    }

    @Test
    void certificateHtmlCarriesTheMergedFboDetails() throws Exception {
        FssaiVerificationResponse response = merge(DETAILS_JSON, LICENSE_JSON);
        String html = buildHtml(response);

        assertTrue(html.contains("21221160000115"));
        assertTrue(html.contains("SHYAM SUNDAR / CANARA JUICE JUNCTION"));
        assertTrue(html.contains("10-09-2028"));
        assertTrue(html.contains("Karnataka"));
        assertTrue(html.contains("Food Safety and Standards Authority of India"));
        assertTrue(html.contains("FSSAI"));
        assertFalse(html.contains("null"));
    }

    @Test
    void certificateHtmlMatchesTheReferenceLayout() throws Exception {
        FssaiVerificationResponse response = merge(DETAILS_JSON, LICENSE_JSON);
        String html = buildHtml(response);

        // rounded page frame + serif licence block styled after the reference doc
        assertTrue(html.contains("class=\"frame\""), "page frame");
        assertTrue(html.contains("FSSAI License / Registration Number"), "licence block");
        assertTrue(html.contains("Times New Roman"), "serif face");

        // footer carries exactly three images: QR, DukaanLocker logo, verified tick
        assertEquals(3, occurrences(html, "data:image/png;base64,"), "QR + logo + tick");
        assertTrue(html.contains(QrCodePng.pngDataUri("FSSAI:21221160000115")),
                "QR must encode this licence number");
        assertTrue(html.contains("Digitally signed and verified by"), "signature line");

        // "FRO ID" on the sample was a typo — the label stays FBO ID
        assertTrue(html.contains("FBO ID"), "FBO ID label");
    }

    private static int occurrences(String haystack, String needle) {
        int count = 0;
        for (int from = haystack.indexOf(needle); from >= 0; from = haystack.indexOf(needle, from + needle.length())) {
            count++;
        }
        return count;
    }

    @Test
    void certificateHtmlRendersToPdf() throws Exception {
        FssaiVerificationResponse response = merge(DETAILS_JSON, LICENSE_JSON);
        String html = buildHtml(response);

        FssaiVerificationService service = new FssaiVerificationService(null);
        Method render = FssaiVerificationService.class
                .getDeclaredMethod("convertHtmlToPdf", String.class, String.class);
        render.setAccessible(true);
        byte[] pdf = (byte[]) render.invoke(service, html, "21221160000115");

        assertTrue(pdf.length > 1000, "Expected a non-trivial PDF, got " + pdf.length + " bytes");
        assertEquals('%', (char) pdf[0]);
        assertEquals('P', (char) pdf[1]);
    }

    @Test
    void certificateFitsOnASinglePage() throws Exception {
        FssaiVerificationResponse response = merge(DETAILS_JSON, LICENSE_JSON);
        String html = buildHtml(response);

        FssaiVerificationService service = new FssaiVerificationService(null);
        Method render = FssaiVerificationService.class
                .getDeclaredMethod("convertHtmlToPdf", String.class, String.class);
        render.setAccessible(true);
        byte[] pdf = (byte[]) render.invoke(service, html, "21221160000115");

        try (PDDocument document = PDDocument.load(pdf)) {
            assertEquals(1, document.getNumberOfPages(),
                    "FSSAI certificate must be rendered on a single page");
        }
    }

    @Test
    void shopNameMatchesApiFboNameVariants() {
        // "OWNER / BUSINESS NAME" style FBO names match the business part
        assertTrue(FssaiVerificationService.businessNameMatches(
                "Canara Juice Junction", "SHYAM SUNDAR / CANARA JUICE JUNCTION"));
        assertTrue(FssaiVerificationService.businessNameMatches(
                "CANARA JUICE JUNCTION", "SHYAM SUNDAR / CANARA JUICE JUNCTION"));
        // owner-name-only shop still matches the combined FBO name
        assertTrue(FssaiVerificationService.businessNameMatches(
                "Shyam Sundar", "SHYAM SUNDAR / CANARA JUICE JUNCTION"));
        // case / punctuation / corporate suffix insensitive
        assertTrue(FssaiVerificationService.businessNameMatches(
                "Sharma Traders", "SHARMA TRADERS PRIVATE LIMITED"));
        // nothing to compare against -> not blocked
        assertTrue(FssaiVerificationService.businessNameMatches(null, "SHYAM SUNDAR"));
        assertTrue(FssaiVerificationService.businessNameMatches("My Shop", null));
        assertTrue(FssaiVerificationService.businessNameMatches("   ", "SHYAM SUNDAR"));
    }

    @Test
    void shopNameMustMatchOtherwiseCertificateIsRejected() {
        assertFalse(FssaiVerificationService.businessNameMatches(
                "Reliance Fresh", "SHYAM SUNDAR / CANARA JUICE JUNCTION"));
        assertFalse(FssaiVerificationService.businessNameMatches(
                "Canara Juice Corner", "SHYAM SUNDAR / CANARA JUICE JUNCTION"));
        assertFalse(FssaiVerificationService.businessNameMatches(
                "Sharma Traders", "SHYAM SUNDAR / CANARA JUICE JUNCTION"));
    }

    // ---- extractExpiryDate: the expiry-only lookup used by the upload fallback ----

    @Test
    void expiryOnlyLookupReadsExpiryFromLicensePayload() {
        FssaiVerificationService service = new FssaiVerificationService(null);

        assertEquals(LocalDateTime.of(2028, 9, 10, 0, 0), service.extractExpiryDate(LICENSE_JSON));
    }

    @Test
    void expiryOnlyLookupReturnsNullWhenLicenseNodeMissing() {
        FssaiVerificationService service = new FssaiVerificationService(null);

        assertNull(service.extractExpiryDate("{\"details\":{\"companyName\":\"X\"}}"));
        assertNull(service.extractExpiryDate("{}"));
    }

    @Test
    void expiryOnlyLookupReturnsNullWhenExpiryAbsentOrUnusable() {
        FssaiVerificationService service = new FssaiVerificationService(null);

        // Registration-style licence with no expiry printed upstream
        assertNull(service.extractExpiryDate("{\"license\":{\"LicenseNo\":\"21221160000115\"}}"));
        assertNull(service.extractExpiryDate("{\"license\":{\"expiryDate\":null}}"));
        assertNull(service.extractExpiryDate("{\"license\":{\"expiryDate\":\"  \"}}"));
        assertNull(service.extractExpiryDate("{\"license\":{\"expiryDate\":\"not a date\"}}"));
        assertNull(service.extractExpiryDate("{\"license\":{\"expiryDate\":\"10-09-202\"}}"));
    }

    /**
     * The expiry formatter uses SMART resolution, so an impossible day is nudged
     * into the month rather than rejected. Documented here because the fallback
     * relies on it: a slightly-off upstream date still yields an alert instead of
     * silently losing the expiry altogether.
     */
    @Test
    void expiryOnlyLookupNormalisesImpossibleDayRatherThanDroppingIt() {
        FssaiVerificationService service = new FssaiVerificationService(null);

        // 31 Feb 2028 -> 29 Feb 2028 (2028 is a leap year)
        assertEquals(LocalDateTime.of(2028, 2, 29, 0, 0),
                service.extractExpiryDate("{\"license\":{\"expiryDate\":\"31-02-2028\"}}"));
    }

    @Test
    void expiryOnlyLookupSwallowsMalformedPayloadInsteadOfThrowing() {
        FssaiVerificationService service = new FssaiVerificationService(null);

        assertNull(service.extractExpiryDate("not json at all"));
        assertNull(service.extractExpiryDate(""));
    }

    @Test
    void expiryOnlyLookupShortCircuitsOnBlankLicenseNumber() {
        FssaiVerificationService service = new FssaiVerificationService(null);

        // Must not attempt any upstream call for a missing number.
        assertTrue(service.fetchExpiryDateOnly(null).isEmpty());
        assertTrue(service.fetchExpiryDateOnly("   ").isEmpty());
    }
}
