package com.shoplocker.fssai.service;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.shoplocker.fssai.dto.DrugLicenseVerificationResponse;
import com.shoplocker.fssai.exception.FssaiException;
import com.shoplocker.fssai.util.BusinessNameMatcher;
import com.shoplocker.fssai.util.CertificateTemplate;
import com.shoplocker.fssai.util.DrugLicenseHtmlGenerator;

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
 * Verifies drug license numbers against the State Drugs Department API.
 *
 * <p>On successful verification a PDF certificate is generated and uploaded to
 * local storage, exactly like the FSSAI / GST / MSME flows.</p>
 */
@Service
public class DrugLicenseVerificationService {

    private static final Logger log = LoggerFactory.getLogger(DrugLicenseVerificationService.class);

    @Value("${app.drug-license.api-base-url}")
    private String drugLicenseApiBaseUrl;

    @Value("${app.drug-license.pdf-base-uri}")
    private String pdfBaseUri;

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LocalFileStorageService localFileStorageService;

    public DrugLicenseVerificationService(LocalFileStorageService localFileStorageService) {
        this.localFileStorageService = localFileStorageService;
    }

    /**
     * Verifies a drug license number, validates business-name ownership, generates a PDF
     * certificate, and uploads it to local storage.
     *
     * @param licenseNumber         the drug license number (e.g., "RLF20AP2026007296")
     * @param userId                the DL user ID for folder structure
     * @param shopId                the shop ID for folder structure
     * @param expectedBusinessName  the shop / business name the user created (null = skip validation)
     * @return DrugLicenseVerificationResponse with license details, PDF URL and HTML certificate
     */
    public DrugLicenseVerificationResponse verifyLicense(String licenseNumber, Long userId, Long shopId,
                                                         String expectedBusinessName) {
        String normalized = licenseNumber.trim();

        // Step 1: call the State Drugs Department API
        String body = callDrugLicenseApi(normalized);

        // Step 2: parse the response
        DrugLicenseVerificationResponse parsed = parseResponse(body, normalized);
        if (!parsed.isSuccess()) {
            return parsed;
        }

        // Step 3: the certificate must belong to the user's own business — compare the
        // license holder name returned by the API with the shop name the user created.
        if (parsed.isSuccess() && !businessNameMatches(expectedBusinessName, parsed.getLicenseHolderName())) {
            log.info("Drug license {} rejected: holder name '{}' does not match shop name '{}'",
                    normalized, parsed.getLicenseHolderName(), expectedBusinessName);
            return DrugLicenseVerificationResponse.error(
                    "Seems like this certificate doesn't belong to your business.");
        }

        // Step 4: only the fetch path generates and stores a PDF. A read-only
        // verification has no user/shop to file it under.
        if (userId == null || shopId == null) {
            parsed.setCertificateHtml(certificateHtml(parsed, false));
            return parsed;
        }

        try {
            byte[] pdfBytes = renderCertificate(parsed, normalized);
            String fileKey = "drug-license/verify/" + normalized.toLowerCase() + "/drug_license.pdf";
            String pdfUrl = localFileStorageService.uploadFile(pdfBytes,
                    ContentType.APPLICATION_PDF.getMimeType(),
                    userId, shopId, fileKey);

            parsed.setPdfUrl(pdfUrl);
            parsed.setContentHash(sha256Hex(pdfBytes));

            log.info("Drug license certificate PDF generated and uploaded for license: {}, size: {} bytes",
                    normalized, pdfBytes.length);
        } catch (FssaiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Drug license {} was verified but PDF generation/storage upload failed", normalized, e);
            throw new FssaiException(
                    "The license was verified but we couldn't generate or store the certificate PDF. Please try again.",
                    com.shoplocker.fssai.exception.FailureCode.PDF_PROCESSING_ERROR, e);
        }

        return parsed;
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /**
     * Renders the certificate as a <b>single page</b>. If it spills onto a second page,
     * re-renders with the compact layout.
     */
    byte[] renderCertificate(DrugLicenseVerificationResponse data, String licenseNumber) {
        String html = certificateHtml(data, false);
        byte[] pdf = convertHtmlToPdf(html, licenseNumber);
        int pages = pageCount(pdf);
        if (pages <= 1) {
            data.setCertificateHtml(html);
            return pdf;
        }

        log.info("Drug license certificate for {} spans {} pages at full size; re-rendering with the compact layout",
                licenseNumber, pages);
        html = certificateHtml(data, true);
        pdf = convertHtmlToPdf(html, licenseNumber);
        data.setCertificateHtml(html);

        pages = pageCount(pdf);
        if (pages > 1) {
            log.warn("Drug license certificate for {} still spans {} pages after the compact layout",
                    licenseNumber, pages);
        }
        return pdf;
    }

    private String certificateHtml(DrugLicenseVerificationResponse data, boolean compact) {
        return DrugLicenseHtmlGenerator.generateCertificateHtml(
                data.getLicenseNumber(),
                data.getLicenseHolderName(),
                data.getKindOfBusiness(),
                data.getIssueDate(),
                data.getExpiryDate(),
                data.getState(),
                data.getDistrict(),
                data.getFlatDoor(),
                data.getPremises(),
                data.getContactPerson(),
                data.getRegistrationNumber(),
                compact);
    }

    /** Pages in the rendered PDF; {@code 1} when it cannot be read (never forces a re-render). */
    int pageCount(byte[] pdf) {
        try (org.apache.pdfbox.pdmodel.PDDocument document = org.apache.pdfbox.pdmodel.PDDocument.load(pdf)) {
            return document.getNumberOfPages();
        } catch (Exception e) {
            log.warn("Could not count the certificate's page count: {}", e.getMessage());
            return 1;
        }
    }

    /**
     * Calls the State Drugs Department API and returns the raw response body.
     * Transport / HTTP failures raise {@link FssaiException}.
     */
    private String callDrugLicenseApi(String licenseNumber) {
        String url = drugLicenseApiBaseUrl + licenseNumber;
        log.info("Drug license request for license: {}, URL: {}", licenseNumber, url);
        return executeGet(url, licenseNumber, true);
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

            return client.execute(request, response -> {
                int status = response.getCode();
                log.info("Drug license upstream HTTP status {} for license {}", status, licenseNumber);

                if (status == 200) {
                    return EntityUtils.toString(response.getEntity());
                }
                if (status == 404) {
                    return "{\"error\": \"License not found\"}";
                }
                throw new FssaiException(
                        "Drug license verification service returned an error (HTTP " + status + "). Please try again later.",
                        com.shoplocker.fssai.exception.FailureCode.FSSAI_VERIFICATION_FAILED);
            });

        } catch (FssaiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to verify drug license: {}", licenseNumber, e);
            throw new FssaiException(
                    "We couldn't complete the drug license verification right now. Please try again in a few minutes.",
                    com.shoplocker.fssai.exception.FailureCode.FSSAI_VERIFICATION_FAILED, e);
        }
    }

    /**
     * Parses the raw upstream JSON payload into a {@link DrugLicenseVerificationResponse}.
     * Returns an error response when the license is not found or the payload cannot be parsed.
     */
    private DrugLicenseVerificationResponse parseResponse(String body, String licenseNumber) {
        if (body == null || body.isBlank()) {
            return DrugLicenseVerificationResponse.error("No response from drug license verification service.");
        }

        try {
            JsonNode root = objectMapper.readTree(body);

            // Upstream may wrap the record under a "record"/"data" key, or return it at the top level.
            JsonNode record = root.path("record");
            if (record.isMissingNode() || record.isNull()) {
                record = root.path("data");
            }
            if (record.isMissingNode() || record.isNull() || record.size() == 0) {
                record = root;
            }

            // Check for an explicit error flag/message
            if (root.hasNonNull("error")) {
                return DrugLicenseVerificationResponse.error("Drug license not found: " + root.get("error").asText());
            }
            if (record.hasNonNull("error")) {
                return DrugLicenseVerificationResponse.error("Drug license not found: " + record.get("error").asText());
            }

            String licenseNumberValue = text(record, "licenseNumber");
            if (isBlank(licenseNumberValue)) licenseNumberValue = text(record, "licenseno");
            if (isBlank(licenseNumberValue)) licenseNumberValue = text(root, "licenseNumber");
            if (isBlank(licenseNumberValue)) licenseNumberValue = licenseNumber;

            String licenseHolderName = text(record, "licenseHolderName");
            if (isBlank(licenseHolderName)) licenseHolderName = text(record, "holderName");
            if (isBlank(licenseHolderName)) licenseHolderName = text(root, "licenseHolderName");

            String kindOfBusiness = text(record, "kindOfBusiness");
            if (isBlank(kindOfBusiness)) kindOfBusiness = text(record, "kob");
            if (isBlank(kindOfBusiness)) kindOfBusiness = text(root, "kindOfBusiness");

            String issueDate = text(record, "issueDate");
            if (isBlank(issueDate)) issueDate = text(record, "dateOfIssue");
            if (isBlank(issueDate)) issueDate = text(root, "issueDate");

            String expiryDate = text(record, "expiryDate");
            if (isBlank(expiryDate)) expiryDate = text(record, "validTill");
            if (isBlank(expiryDate)) expiryDate = text(root, "expiryDate");

            String state = text(record, "state");
            if (isBlank(state)) state = text(root, "state");

            String district = text(record, "district");
            if (isBlank(district)) district = text(root, "district");

            String flatDoor = text(record, "flatDoor");
            if (isBlank(flatDoor)) flatDoor = text(record, "flatDoorNo");
            if (isBlank(flatDoor)) flatDoor = text(root, "flatDoor");

            String premises = text(record, "premises");
            if (isBlank(premises)) premises = text(root, "premises");

            String contactPerson = text(record, "contactPerson");
            if (isBlank(contactPerson)) contactPerson = text(root, "contactPerson");

            String registrationNumber = text(record, "registrationNumber");
            if (isBlank(registrationNumber)) registrationNumber = text(root, "registrationNumber");

            if (isBlank(licenseHolderName) && isBlank(premises)) {
                return DrugLicenseVerificationResponse.error(
                        "Could not extract drug license details for this license number.");
            }

            return DrugLicenseVerificationResponse.ok(
                    licenseNumberValue, licenseHolderName, kindOfBusiness, issueDate,
                    expiryDate, state, district, flatDoor, premises, contactPerson,
                    registrationNumber, null, null);

        } catch (Exception e) {
            log.error("Failed to parse drug license verification response for {}", licenseNumber, e);
            return DrugLicenseVerificationResponse.error("Failed to parse the response from the drug license verification service.");
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
     * Matches the shop / business name the user created against the license holder name
     * returned by the State Drugs Department API.
     *
     * <p>Returns {@code true} when either name is missing — there is nothing to compare,
     * so the fetch is not blocked.</p>
     */
    static boolean businessNameMatches(String shopName, String apiCompanyName) {
        return BusinessNameMatcher.matches(shopName, apiCompanyName);
    }

    /**
     * Converts XHTML to PDF using OpenHTMLtoPDF.
     */
    private byte[] convertHtmlToPdf(String xhtml, String licenseNumber) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(xhtml, pdfBaseUri);
            builder.toStream(baos);

            CertificateTemplate.registerFonts(builder);
            builder.run();

            byte[] pdfBytes = baos.toByteArray();
            log.info("Generated Drug License PDF: {} bytes for license {}", pdfBytes.length, licenseNumber);
            return pdfBytes;

        } catch (Exception e) {
            log.error("Failed to convert Drug License HTML to PDF for {}", licenseNumber, e);
            throw new FssaiException(
                    "The license was verified but we couldn't generate the PDF. Please try again.",
                    com.shoplocker.fssai.exception.FailureCode.PDF_PROCESSING_ERROR, e);
        }
    }
}
