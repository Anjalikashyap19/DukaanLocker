package com.shoplocker.fssai.controller;

import com.shoplocker.fssai.dto.GstFetchRequest;
import com.shoplocker.fssai.dto.GstVerificationResponse;
import com.shoplocker.fssai.entity.DocumentType;
import com.shoplocker.fssai.service.GstVerificationService;
import com.shoplocker.fssai.service.ShopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints for GST number verification via API Setu.
 * The endpoints are whitelisted in {@code SecurityConfig} under {@code /api/gst/**}.
 */
@RestController
@RequestMapping("/api/gst")
@Tag(name = "GST Verification", description = "GSTN Taxpayers Verification via API Setu — verify and fetch GSTIN details against the government portal.")
public class GstVerificationController {

    private final GstVerificationService gstService;
    private final ShopService shopService;

    public GstVerificationController(GstVerificationService gstService, ShopService shopService) {
        this.gstService = gstService;
        this.shopService = shopService;
    }

    @Operation(
            summary = "Verify GST number (read-only)",
            description = "Verifies a GSTIN against the government portal via API Setu. " +
                          "Returns taxpayer details, generates a PDF certificate, and uploads it to S3. " +
                          "Does NOT persist the document record — use /fetch for that."
    )
    @GetMapping("/verify/{gstNumber}")
    public ResponseEntity<GstVerificationResponse> verifyGstNumber(@PathVariable String gstNumber) {
        GstVerificationResponse response = gstService.verifyGstNumber(gstNumber);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Fetch and persist GST document",
            description = "Verifies a GSTIN against the government portal via API Setu, " +
                          "generates a PDF certificate, uploads it to S3, " +
                          "and persists it as a GST document for the given shop. " +
                          "Returns the verification details including the PDF URL."
    )
    @PostMapping("/fetch")
    public ResponseEntity<GstVerificationResponse> fetchAndPersistGst(@Valid @RequestBody GstFetchRequest request) {
        GstVerificationResponse verification = gstService.verifyGstNumber(request.getGstin());

        if (!verification.isSuccess()) {
            return ResponseEntity.ok(verification);
        }

        Long shopId = Long.parseLong(request.getShopId());
        shopService.uploadOrReuploadDocument(
                shopId,
                DocumentType.GST,
                "gst_certificate.pdf",
                verification.getPdfUrl(),
                request.getGstin(),
                null,
                null
        );

        return ResponseEntity.ok(verification);
    }
}
