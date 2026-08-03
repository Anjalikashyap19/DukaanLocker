package com.shoplocker.fssai.controller;

import com.shoplocker.fssai.dto.UdyamInitResponse;
import com.shoplocker.fssai.dto.UdyamVerifyRequest;
import com.shoplocker.fssai.dto.UdyamVerifyResponse;
import com.shoplocker.fssai.service.UdyamVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Public endpoints for Udyam (MSME) government portal verification.
 * Both endpoints are whitelisted in {@code SecurityConfig} under {@code /api/udyam/**}.
 */
@RestController
@RequestMapping("/api/udyam")
@Tag(name = "Udyam MSME Verification", description = "Government portal verification — init session, verify Udyam number, generate PDF certificate.")
public class UdyamVerificationController {

    private final UdyamVerificationService udyamService;

    public UdyamVerificationController(UdyamVerificationService udyamService) {
        this.udyamService = udyamService;
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
}
