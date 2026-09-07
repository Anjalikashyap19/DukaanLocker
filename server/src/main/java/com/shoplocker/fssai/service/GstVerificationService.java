package com.shoplocker.fssai.service;

import java.io.ByteArrayOutputStream;
import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.shoplocker.fssai.dto.GstVerificationResponse;
import com.shoplocker.fssai.exception.FailureCode;
import com.shoplocker.fssai.exception.FssaiException;
import com.shoplocker.fssai.util.GstHtmlGenerator;

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
 * Integrates with the API Setu "GSTN Taxpayers Verification API" to verify
 * GST numbers against the government portal. On successful verification,
 * generates a PDF certificate and uploads it to S3.
 */
@Service
public class GstVerificationService {

    private static final Logger log = LoggerFactory.getLogger(GstVerificationService.class);

    private static final String BASE_URL = "https://apisetu.gov.in/gstn/v2/taxpayers/";

    @Value("${app.api-setu.x-api-key}")
    private String apiKey;

    @Value("${app.api-setu.x-client-id}")
    private String clientId;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final S3Service s3Service;

    public GstVerificationService(S3Service s3Service) {
        this.s3Service = s3Service;
    }

    /**
     * Verifies a GST number against the API Setu GSTN Taxpayers Verification API,
     * generates a PDF certificate, and uploads it to S3.
     *
     * @param gstNumber the GST number to verify (e.g., "27AAPFU0939F1ZV")
     * @return GstVerificationResponse with taxpayer details, PDF URL, and HTML certificate
     */
    public GstVerificationResponse verifyGstNumber(String gstNumber) {
        String normalizedGst = gstNumber.toUpperCase().trim();

        // Step 1: Call API Setu
        String responseBody = callApiSetu(normalizedGst);

        // Step 2: Parse response
        GstVerificationResponse parsed = parseGstResponse(responseBody, normalizedGst);

        // Step 3: If verification succeeded, generate PDF and upload to S3
        if (parsed.isSuccess()) {
            try {
                String certificateHtml = GstHtmlGenerator.generateCertificateHtml(
                        parsed.getGstin(),
                        parsed.getLegalName(),
                        parsed.getTradeName(),
                        parsed.getRegistrationDate(),
                        parsed.getStatus(),
                        parsed.getState(),
                        parsed.getConstitutionOfBusiness(),
                        parsed.getPrincipalPlaceAddress(),
                        parsed.getCentralJurisdiction(),
                        parsed.getPeriodOfValidity(),
                        parsed.getTypeOfRegistration()
                );

                byte[] pdfBytes = convertHtmlToPdf(certificateHtml, normalizedGst);

                String fileKey = "gst/verify/" + normalizedGst.toLowerCase() + "/gst_certificate.pdf";
                String pdfUrl = s3Service.uploadFile(pdfBytes, ContentType.APPLICATION_PDF.getMimeType(), fileKey);

                parsed.setPdfUrl(pdfUrl);
                parsed.setCertificateHtml(certificateHtml);

                log.info("GST certificate PDF generated and uploaded for GSTIN: {}, size: {} bytes", normalizedGst, pdfBytes.length);

            } catch (Exception e) {
                log.error("GST number {} was verified but PDF generation/S3 upload failed. " +
                        "Continuing without the certificate PDF.", normalizedGst, e);
                // Graceful degradation — verification succeeded, just no PDF
            }
        }

        return parsed;
    }

    /**
     * Calls the API Setu GSTN endpoint and returns the raw response body.
     */
    private String callApiSetu(String gstNumber) {
        String url = BASE_URL + gstNumber;

        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(30_000))
                .setResponseTimeout(Timeout.ofMilliseconds(60_000))
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(30_000))
                .build();

        try (CloseableHttpClient client = HttpClients.custom()
                .setDefaultRequestConfig(config)
                .build()) {

            HttpGet request = new HttpGet(url);
            request.setHeader("X-APISETU-APIKEY", apiKey);
            request.setHeader("X-APISETU-CLIENTID", clientId);
            request.setHeader("accept", "application/json");

            log.info("GST verification request for GSTIN: {}, URL: {}", gstNumber, url);

            return client.execute(request, response -> {
                int status = response.getCode();
                log.info("GST verification HTTP status: {}", status);

                if (status == 401) {
                    throw new FssaiException(
                            "Authentication failed with the GST verification service. Please try again later.",
                            FailureCode.GST_VERIFICATION_FAILED);
                }
                if (status == 404) {
                    // Try fallback URL
                    return tryFallbackUrl(gstNumber, config);
                }
                if (status != 200) {
                    throw new FssaiException(
                            "GST verification service returned an error (HTTP " + status + "). Please try again later.",
                            FailureCode.GST_VERIFICATION_FAILED);
                }

                return EntityUtils.toString(response.getEntity());
            });

        } catch (FssaiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to verify GST number: {}", gstNumber, e);
            throw new FssaiException(
                    "We couldn't complete the GST verification right now. Please try again in a few minutes.",
                    FailureCode.GST_VERIFICATION_FAILED, e);
        }
    }

    /**
     * Tries the fallback URL if the primary URL returns 404.
     */
    private String tryFallbackUrl(String gstNumber, RequestConfig config) {
        String fallbackUrl = BASE_URL_FALLBACK + gstNumber;
        log.info("Trying fallback URL: {}", fallbackUrl);

        try (CloseableHttpClient client = HttpClients.custom()
                .setDefaultRequestConfig(config)
                .build()) {

            HttpGet fallbackRequest = new HttpGet(fallbackUrl);
            fallbackRequest.setHeader("X-APISETU-APIKEY", apiKey);
            fallbackRequest.setHeader("X-APISETU-CLIENTID", clientId);
            fallbackRequest.setHeader("accept", "application/json");

            return client.execute(fallbackRequest, response -> {
                int status = response.getCode();
                log.info("GST verification fallback HTTP status: {}", status);

                if (status != 200) {
                    return "{\"error\": \"GSTIN not found\"}";
                }
                return EntityUtils.toString(response.getEntity());
            });

        } catch (Exception e) {
            log.error("Fallback URL also failed for GSTIN: {}", gstNumber, e);
            return "{\"error\": \"GSTIN not found\"}";
        }
    }

    /**
     * Parses the API Setu response and extracts taxpayer details.
     */
    private GstVerificationResponse parseGstResponse(String responseBody, String gstNumber) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            // Check if the response contains an error
            if (root.has("error")) {
                String errorMsg = root.get("error").asText();
                if (errorMsg.contains("not found") || errorMsg.contains("invalid")) {
                    return GstVerificationResponse.error("The GST number was not found on the government portal. Please check and try again.");
                }
                return GstVerificationResponse.error("Error from GST verification service: " + errorMsg);
            }

            // Extract taxpayer details from the response
            // API Setu GSTN V2 response fields
            String legalName = extractField(root, "legalNameOfBusiness");
            String tradeName = extractField(root, "tradeName");
            String registrationDate = extractField(root, "dateOfRegistration");
            String status = extractField(root, "gstnStatus");
            String state = extractField(root, "stateJurisdiction");
            String constitutionOfBusiness = extractField(root, "constitutionOfBusiness");
            String centralJurisdiction = extractField(root, "centralJurisdiction");

            // Build principal place address from address fields
            String principalPlaceAddress = buildPrincipalPlaceAddress(root);

            // Derive period of validity
            String periodOfValidity = derivePeriodOfValidity(registrationDate, status);

            // Derive type of registration from GSTIN (3rd character)
            String typeOfRegistration = deriveTypeOfRegistration(gstNumber);

            if (legalName == null && tradeName == null) {
                return GstVerificationResponse.error("Could not extract taxpayer details from the response.");
            }

            return GstVerificationResponse.ok(gstNumber, legalName, tradeName, registrationDate, status, state,
                    constitutionOfBusiness, principalPlaceAddress, centralJurisdiction,
                    periodOfValidity, typeOfRegistration, null, null);

        } catch (Exception e) {
            log.error("Failed to parse GST verification response", e);
            return GstVerificationResponse.error("Failed to parse the response from the GST verification service.");
        }
    }

    /**
     * Safely extracts a field value from a JSON node.
     */
    private String extractField(JsonNode root, String fieldName) {
        JsonNode node = root.get(fieldName);
        if (node != null && !node.isNull()) {
            return node.asText();
        }
        return null;
    }

    /**
     * Builds the principal place address from individual address fields in the API response.
     */
    private String buildPrincipalPlaceAddress(JsonNode root) {
        StringBuilder address = new StringBuilder();
        String[] addressFields = {"door", "building", "street", "location", "city", "district", "state", "pincode"};
        String[] addressSeparators = {", ", ", ", ", ", ", ", ", ", ", ", " - ", " "};

        for (int i = 0; i < addressFields.length; i++) {
            String value = extractField(root, addressFields[i]);
            if (value != null && !value.trim().isEmpty()) {
                if (address.length() > 0) {
                    address.append(addressSeparators[i]);
                }
                address.append(value.trim());
            }
        }

        return address.length() > 0 ? address.toString() : null;
    }

    /**
     * Derives the period of validity based on registration date and status.
     * For Active: "Valid from {date} until cancelled"
     * For Cancelled: "Cancelled on {cancellationDate}"
     */
    private String derivePeriodOfValidity(String registrationDate, String status) {
        if (status != null && status.equalsIgnoreCase("Cancelled")) {
            return "Cancelled";
        }
        if (registrationDate != null && !registrationDate.isEmpty()) {
            return "Valid from " + registrationDate + " until cancelled";
        }
        return "Until cancelled";
    }

    /**
     * Derives the type of registration from the GSTIN number.
     * The 3rd character of GSTIN indicates the entity type:
     * 1 = Regular, 2 = Composition, 6 = TDS Deductor, 7 = TCS Collector,
     * 8 = Non-resident taxable person, 9 = UIN, A = GST Practitioner,
     * P = Input Service Distributor, S = SEZ Developer/Unit, etc.
     */
    private String deriveTypeOfRegistration(String gstin) {
        if (gstin == null || gstin.length() < 3) {
            return "Regular";
        }
        char thirdChar = gstin.charAt(2);
        switch (thirdChar) {
            case '1': return "Regular";
            case '2': return "Composition";
            case '6': return "TDS Deductor";
            case '7': return "TCS Collector";
            case '8': return "Non-resident taxable person";
            case '9': return "UN Body / Embassy / Consulate";
            case 'A': return "GST Practitioner";
            case 'P': return "Input Service Distributor";
            case 'R': return "Resollector Agent";
            case 'S': return "SEZ Developer / SEZ Unit";
            case 'T': return "TRP (Tax Return Preparer)";
            case 'M': return "Miscellaneous";
            default: return "Regular";
        }
    }

    /**
     * Converts XHTML HTML to PDF using OpenHTMLtoPDF.
     */
    private byte[] convertHtmlToPdf(String xhtml, String gstNumber) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(xhtml, "https://apisetu.gov.in/");
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
                log.warn("Could not load system font for GST PDF, may use default font", e);
            }
            builder.run();

            byte[] pdfBytes = baos.toByteArray();
            log.info("Generated GST PDF: {} bytes for GSTIN {}", pdfBytes.length, gstNumber);
            return pdfBytes;

        } catch (Exception e) {
            log.error("Failed to convert GST HTML to PDF for {}", gstNumber, e);
            throw new FssaiException(
                    "The GST was verified but we couldn't generate the PDF. Please try again.",
                    FailureCode.PDF_PROCESSING_ERROR, e);
        }
    }
}
