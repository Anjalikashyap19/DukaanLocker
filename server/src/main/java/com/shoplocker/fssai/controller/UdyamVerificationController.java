package com.shoplocker.fssai.controller;

import com.shoplocker.fssai.dto.MsmeParsedData;
import com.shoplocker.fssai.dto.UdyamFetchRequest;
import com.shoplocker.fssai.dto.UdyamInitResponse;
import com.shoplocker.fssai.dto.UdyamVerifyRequest;
import com.shoplocker.fssai.dto.UdyamVerifyResponse;
import com.shoplocker.fssai.entity.DocumentType;
import com.shoplocker.fssai.entity.User;
import com.shoplocker.fssai.service.ShopAccessService;
import com.shoplocker.fssai.service.ShopService;
import com.shoplocker.fssai.service.UdyamVerificationService;
import com.shoplocker.fssai.util.MsmeDataParser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints for Udyam (MSME) government portal verification.
 * {@code /init}, {@code /captcha/**}, and {@code /verify} are public (whitelisted in SecurityConfig).
 * {@code /fetch} requires JWT authentication and validates shop ownership.
 */
@RestController
@RequestMapping("/api/udyam")
@Tag(name = "Udyam MSME Verification", description = "Government portal verification — init session, verify Udyam number, generate PDF certificate, fetch and persist for existing users.")
public class UdyamVerificationController {

    private final UdyamVerificationService udyamService;
    private final ShopService shopService;
    private final ShopAccessService shopAccessService;

    public UdyamVerificationController(UdyamVerificationService udyamService,
                                       ShopService shopService,
                                       ShopAccessService shopAccessService) {
        this.udyamService = udyamService;
        this.shopService = shopService;
        this.shopAccessService = shopAccessService;
    }

    @Operation(
            summary = "Initialise Udyam verification session",
            description = "Connects to the government Udyam portal, creates a session, " +
                          "and returns a CAPTCHA image the user must solve before verifying."
    )
    @PostMapping("/init")
    public ResponseEntity<UdyamInitResponse> initSession() {
        UdyamInitResponse response = udyamService.initSession();
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get CAPTCHA image for a session",
            description = "Returns the raw PNG captcha image bytes for the given session. " +
                          "Use the sessionId from the /init response."
    )
    @GetMapping("/captcha/{sessionId}")
    public ResponseEntity<byte[]> getCaptchaImage(@PathVariable String sessionId) {
        byte[] imageBytes = udyamService.getCaptchaImage(sessionId);
        if (imageBytes == null || imageBytes.length == 0) {
            return ResponseEntity.notFound().build();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        headers.setContentLength(imageBytes.length);
        headers.setCacheControl("no-store");  // don't cache — each session has its own captcha
        return ResponseEntity.ok().headers(headers).body(imageBytes);
    }

    @Operation(
            summary = "Verify Udyam number and generate PDF certificate",
            description = "Submits the Udyam number + CAPTCHA to the government portal. " +
                          "On success, fetches the certificate HTML, converts it to a PDF, " +
                          "uploads to S3, and returns the PDF URL."
    )
    @PostMapping("/verify")
    public ResponseEntity<UdyamVerifyResponse> verify(@Valid @RequestBody UdyamVerifyRequest request) {
        UdyamVerifyResponse response = udyamService.verifyAndGeneratePdf(request);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Fetch and persist MSME certificate for an existing shop",
            description = "Verifies a Udyam number against the government portal, " +
                          "generates a PDF certificate from parsed data, uploads to S3, " +
                          "and persists it as an MSME_CERTIFICATE document for the given shop. " +
                          "Requires JWT authentication and shop ownership validation."
    )
    @PostMapping("/fetch")
    public ResponseEntity<UdyamVerifyResponse> fetchAndPersistUdyam(
            @Valid @RequestBody UdyamFetchRequest request,
            Authentication authentication) {

        // Validate shop ownership
        User user = shopAccessService.getAuthenticatedUser(authentication);
        Long shopId = Long.parseLong(request.getShopId());
        if (!shopAccessService.canAccessShop(user, shopId)) {
            return ResponseEntity.status(403).body(UdyamVerifyResponse.error("Access denied: you do not own this shop"));
        }

        // Verify against government portal
        UdyamVerifyRequest verifyReq = new UdyamVerifyRequest();
        verifyReq.setSessionId(request.getSessionId());
        verifyReq.setUdyamNumber(request.getUdyamNumber());
        verifyReq.setCaptchaText(request.getCaptchaText());

        UdyamVerifyResponse verifyResult = udyamService.verifyAndGeneratePdf(verifyReq);

        if (!verifyResult.isSuccess()) {
            return ResponseEntity.ok(verifyResult);
        }

        // Parse MSME data from HTML and regenerate PDF from parsed data
        String finalPdfUrl = verifyResult.getPdfUrl();
        if (verifyResult.getCertificateHtml() != null) {
            try {
                MsmeParsedData parsedData = MsmeDataParser.parse(verifyResult.getCertificateHtml());
                if (parsedData != null) {
                    String regeneratedPdfUrl = udyamService.generatePdfFromParsedData(parsedData, request.getUdyamNumber());
                    if (regeneratedPdfUrl != null) {
                        finalPdfUrl = regeneratedPdfUrl;
                    }
                }
            } catch (Exception e) {
                // Graceful degradation — keep the original PDF URL
            }
        }

        // Persist document
        shopService.uploadOrReuploadDocument(
                shopId,
                DocumentType.MSME_CERTIFICATE,
                "Udyam_Certificate_" + request.getUdyamNumber() + ".pdf",
                finalPdfUrl,
                request.getUdyamNumber(),
                null,
                null
        );

        verifyResult.setPdfUrl(finalPdfUrl);
        return ResponseEntity.ok(verifyResult);
    }
}
