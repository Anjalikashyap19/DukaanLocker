package com.shoplocker.fssai.service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.shoplocker.fssai.dto.FssaiVerificationResponse;
import com.shoplocker.fssai.exception.FailureCode;
import com.shoplocker.fssai.exception.FssaiException;
import com.shoplocker.fssai.util.FssaiHtmlGenerator;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

/**
 * Verifies FSSAI food license numbers against the public license lookup APIs.
 *
 * <p>Two upstream endpoints are used and merged:</p>
 * <ul>
 *   <li>{@code lnchk.php} — primary source: company name, premises address,
 *       state / district / taluk / village, pincode, license category, status,
 *       active flag and reference id.</li>
 *   <li>{@code check-details.php} — supplementary source: <b>expiry date</b>
 *       (absent from the primary API) plus contact person, email, PAN,
 *       kind of business and FBO id.</li>
 * </ul>
 *
 * <p>On successful verification a PDF certificate is generated and uploaded to
 * local storage (the VPS document path), exactly like the GST / MSME flows.</p>
 */
@Service
public class FssaiVerificationService {

    private static final Logger log = LoggerFactory.getLogger(FssaiVerificationService.class);

    private static final String DETAILS_API_URL = "https://iadv.in/tracker/dist/lnchk.php?ln=";
    private static final String LICENSE_API_URL = "https://iadv.in/tracker/dist/check-details.php?lic_num=";

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static final DateTimeFormatter EXPIRY_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LocalFileStorageService localFileStorageService;

    public FssaiVerificationService(LocalFileStorageService localFileStorageService) {
        this.localFileStorageService = localFileStorageService;
    }

    /**
     * Verifies an FSSAI license number, generates a PDF certificate, and uploads it to local storage.
     *
     * @param licenseNumber the 14-digit FSSAI license number (e.g., "21221160000115")
     * @param userId        the DL user ID for folder structure
     * @param shopId        the shop ID for folder structure
     * @return FssaiVerificationResponse with FBO details, PDF URL and HTML certificate
     */
    public FssaiVerificationResponse verifyLicense(String licenseNumber, Long userId, Long shopId) {
        String normalized = licenseNumber.trim();

        // Step 1: primary details API (throws on transport failure)
        String detailsBody = callDetailsApi(normalized);

        // Step 2: supplementary API carrying the expiry date — never fatal
        String licenseBody = callLicenseApi(normalized);

        // Step 3: merge + map
        FssaiVerificationResponse parsed = parseAndMerge(detailsBody, licenseBody, normalized);

        // Step 4: generate PDF and upload to the VPS document path
        if (parsed.isSuccess()) {
            try {
                String certificateHtml = FssaiHtmlGenerator.generateCertificateHtml(
                        parsed.getLicenseNumber(),
                        parsed.getCompanyName(),
                        parsed.getContactPerson(),
                        parsed.getKindOfBusiness(),
                        parsed.getLicenseCategory(),
                        parsed.getStatus(),
                        parsed.getLicenseActive(),
                        parsed.getState(),
                        parsed.getDistrict(),
                        parsed.getTaluk(),
                        parsed.getVillage(),
                        parsed.getAddress(),
                        parsed.getPincode(),
                        parsed.getContactEmail(),
                        parsed.getPanNo(),
                        parsed.getExpiryDate(),
                        parsed.getFboId(),
                        parsed.getRefId()
                );

                byte[] pdfBytes = convertHtmlToPdf(certificateHtml, normalized);

                String fileKey = "fssai/verify/" + normalized.toLowerCase() + "/fssai_food_license.pdf";
                Long effectiveUserId = userId != null ? userId : 0L;
                Long effectiveShopId = shopId != null ? shopId : 0L;
                String pdfUrl = localFileStorageService.uploadFile(pdfBytes,
                        ContentType.APPLICATION_PDF.getMimeType(),
                        effectiveUserId, effectiveShopId, fileKey);

                parsed.setPdfUrl(pdfUrl);
                parsed.setCertificateHtml(certificateHtml);

                log.info("FSSAI certificate PDF generated and uploaded for license: {}, size: {} bytes",
                        normalized, pdfBytes.length);

            } catch (Exception e) {
                log.error("FSSAI license {} was verified but PDF generation/storage upload failed. " +
                        "Continuing without the certificate PDF.", normalized, e);
                // Graceful degradation — verification succeeded, just no PDF
            }
        }

        return parsed;
    }

    /**
     * Calls the primary details API ({@code lnchk.php}) and returns the raw response body.
     * Transport / HTTP failures raise {@link FssaiException} with {@link FailureCode#FSSAI_VERIFICATION_FAILED}.
     */
    private String callDetailsApi(String licenseNumber) {
        String url = DETAILS_API_URL + licenseNumber;
        log.info("FSSAI details request for license: {}, URL: {}", licenseNumber, url);
        return executeGet(url, licenseNumber, true);
    }

    /**
     * Calls the supplementary license API ({@code check-details.php}) and returns the raw response body,
     * or {@code null} when the call fails. This API only supplies the expiry date and a few extras,
     * so a failure degrades gracefully instead of failing the whole verification.
     */
    private String callLicenseApi(String licenseNumber) {
        String url = LICENSE_API_URL + licenseNumber;
        log.info("FSSAI license/expiry request for license: {}, URL: {}", licenseNumber, url);
        try {
            return executeGet(url, licenseNumber, false);
        } catch (FssaiException e) {
            log.warn("Supplementary FSSAI expiry API failed for {}; continuing without expiry date. Cause: {}",
                    licenseNumber, e.getMessage());
            return null;
        }
    }

    private String executeGet(String url, String licenseNumber, boolean required) {
        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(10))
                .setResponseTimeout(Timeout.ofSeconds(20))
                .setConnectionRequestTimeout(Timeout.ofSeconds(10))
                .build();

        try (CloseableHttpClient client = HttpClients.custom()
                .setDefaultRequestConfig(config)
                .build()) {

            HttpGet request = new HttpGet(url);
            request.setHeader("User-Agent", USER_AGENT);
            request.setHeader("Accept", "application/json, text/plain, */*");
            request.setHeader("Referer", "https://iadv.in/");

            return client.execute(request, response -> {
                int status = response.getCode();
                log.info("FSSAI upstream HTTP status {} for license {}", status, licenseNumber);

                if (status == 200) {
                    return EntityUtils.toString(response.getEntity());
                }
                if (!required) {
                    return null;
                }
                if (status == 404) {
                    return "{\"error\": \"License not found\"}";
                }
                throw new FssaiException(
                        "FSSAI verification service returned an error (HTTP " + status + "). Please try again later.",
                        FailureCode.FSSAI_VERIFICATION_FAILED);
            });

        } catch (FssaiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to verify FSSAI license: {}", licenseNumber, e);
            if (!required) {
                return null;
            }
            throw new FssaiException(
                    "We couldn't complete the FSSAI verification right now. Please try again in a few minutes.",
                    FailureCode.FSSAI_VERIFICATION_FAILED, e);
        }
    }

    /**
     * Merges both upstream payloads into a single response. The primary details API wins for
     * overlapping fields; the supplementary API fills gaps and is the only source of the expiry date.
     */
    private FssaiVerificationResponse parseAndMerge(String detailsBody, String licenseBody, String licenseNumber) {
        try {
            JsonNode record = null;
            if (detailsBody != null && !detailsBody.isBlank()) {
                JsonNode detailsRoot = objectMapper.readTree(detailsBody);
                if (detailsRoot.hasNonNull("error")) {
                    log.info("Details API reported an error for {}: {}", licenseNumber, detailsRoot.get("error").asText());
                } else {
                    JsonNode list = detailsRoot.path("paginationListRecords");
                    if (list.isArray() && !list.isEmpty()) {
                        record = list.get(0);
                    }
                }
            }

            JsonNode licenseNode = null;
            JsonNode detailsExtras = null;
            if (licenseBody != null && !licenseBody.isBlank()) {
                JsonNode licenseRoot = objectMapper.readTree(licenseBody);
                JsonNode lic = licenseRoot.path("license");
                if (!lic.isMissingNode() && !lic.isNull() && lic.has("LicenseNo")) {
                    licenseNode = lic;
                    detailsExtras = licenseRoot.path("details");
                }
            }

            if (record == null && licenseNode == null) {
                return FssaiVerificationResponse.error(
                        "The FSSAI license number was not found. Please check the number and try again.");
            }

            // ---- Primary details (API 2), falling back to API 1 ----
            String companyName = text(record, "companyname");
            if (isBlank(companyName)) companyName = text(detailsExtras, "companyName");

            String address = text(record, "premiseaddress");
            if (isBlank(address)) address = text(detailsExtras, "addressPremises");

            String state = text(record, "statename");
            if (isBlank(state)) state = text(detailsExtras, "statePremises");

            String district = text(record, "districtname");
            if (isBlank(district)) district = text(detailsExtras, "districtPremises");

            String pincode = text(record, "premisepincode");
            if (isBlank(pincode)) pincode = text(detailsExtras, "pincodePremises");

            String licenseCategory = text(record, "licensecategoryname");
            String status = text(record, "statusdesc");
            String taluk = text(record, "talukname");
            String village = text(record, "villagename");

            Boolean licenseActive = null;
            if (record != null && record.has("licenseactiveflag") && !record.get("licenseactiveflag").isNull()) {
                licenseActive = record.get("licenseactiveflag").asBoolean();
            }

            String refId = text(record, "refid");
            if (isBlank(refId)) refId = licenseNode != null && licenseNode.has("refId")
                    ? String.valueOf(licenseNode.get("refId").asLong()) : null;

            // ---- Supplementary details (API 1) — expiry date lives only here ----
            String expiryDate = licenseNode != null ? text(licenseNode, "expiryDate") : null;
            String fboId = licenseNode != null ? text(licenseNode, "fboId") : null;
            String contactPerson = text(detailsExtras, "contactPerson");
            String contactEmail = text(detailsExtras, "contactEmail");
            String panNo = text(detailsExtras, "panNo");
            String kindOfBusiness = text(detailsExtras, "kobname");

            String finalLicenseNumber = text(licenseNode, "LicenseNo");
            if (isBlank(finalLicenseNumber)) finalLicenseNumber = text(record, "licenseno");
            if (isBlank(finalLicenseNumber)) finalLicenseNumber = licenseNumber;

            if (isBlank(companyName) && isBlank(address)) {
                return FssaiVerificationResponse.error(
                        "Could not extract food business operator details for this license.");
            }

            return FssaiVerificationResponse.ok(finalLicenseNumber, companyName, contactPerson, kindOfBusiness,
                    licenseCategory, status, licenseActive, state, district, taluk, village, address, pincode,
                    contactEmail, panNo, expiryDate, fboId, refId, null, null);

        } catch (Exception e) {
            log.error("Failed to parse FSSAI verification response for {}", licenseNumber, e);
            return FssaiVerificationResponse.error("Failed to parse the response from the FSSAI verification service.");
        }
    }

    private String text(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        String text = value.asText();
        if (text == null) return null;
        text = text.trim();
        return text.isEmpty() ? null : text;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * Parses the upstream {@code dd-MM-yyyy} expiry date into a {@link LocalDateTime}
     * suitable for the {@code documents.expiry_date} column. Returns {@code null} when
     * absent or unparseable.
     */
    public LocalDateTime parseExpiryDate(String expiryDate) {
        if (isBlank(expiryDate)) return null;
        try {
            return LocalDate.parse(expiryDate.trim(), EXPIRY_FORMATTER).atStartOfDay();
        } catch (DateTimeParseException e) {
            log.warn("Could not parse FSSAI expiry date: {}", expiryDate);
            return null;
        }
    }

    /**
     * Converts XHTML to PDF using OpenHTMLtoPDF.
     */
    private byte[] convertHtmlToPdf(String xhtml, String licenseNumber) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(xhtml, "https://iadv.in/");
            builder.toStream(baos);

            // Try system fonts, fall back gracefully if not found
            try {
                String[] fontPaths = {
                        "C:/Windows/Fonts/arial.ttf",
                        "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
                        "/usr/share/fonts/TTF/DejaVuSans.ttf",
                        "/System/Library/Fonts/Helvetica.ttc"
                };
                for (String fontPath : fontPaths) {
                    File fontFile = new File(fontPath);
                    if (fontFile.exists()) {
                        builder.useFont(fontFile, "Arial");
                        break;
                    }
                }
            } catch (Exception e) {
                log.warn("Could not load system font for FSSAI PDF, may use default font", e);
            }
            builder.run();

            byte[] pdfBytes = baos.toByteArray();
            log.info("Generated FSSAI PDF: {} bytes for license {}", pdfBytes.length, licenseNumber);
            return pdfBytes;

        } catch (Exception e) {
            log.error("Failed to convert FSSAI HTML to PDF for {}", licenseNumber, e);
            throw new FssaiException(
                    "The license was verified but we couldn't generate the PDF. Please try again.",
                    FailureCode.PDF_PROCESSING_ERROR, e);
        }
    }
}
