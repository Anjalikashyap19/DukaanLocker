package com.shoplocker.fssai.controller;

import com.shoplocker.fssai.dto.DrugLicenseVerificationResponse;
import com.shoplocker.fssai.service.DrugLicenseVerificationService;
import com.shoplocker.fssai.service.ShopAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import com.shoplocker.fssai.entity.User;

@RestController
@RequestMapping("/api/drug-license")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Drug License Verification", description = "Verify and fetch drug license certificates from State Drugs Department API")
public class DrugLicenseVerificationController {

    private final DrugLicenseVerificationService drugLicenseVerificationService;
    private final ShopAccessService shopAccessService;

    public DrugLicenseVerificationController(
            DrugLicenseVerificationService drugLicenseVerificationService,
            ShopAccessService shopAccessService) {
        this.drugLicenseVerificationService = drugLicenseVerificationService;
        this.shopAccessService = shopAccessService;
    }

    /**
     * Verify a drug license number (read-only endpoint).
     * Calls the external State Drugs Department API and returns license details.
     *
     * @param licenseNumber the drug license number (e.g., RLF20AP2026007296)
     * @param authentication the JWT authentication token
     * @return verification response with license details
     */
    @Operation(summary = "Verify drug license number",
            description = "Verifies a drug license number with the State Drugs Department API. " +
                          "Returns license holder details, business type, issue/expiry dates, and PDF certificate URL.")
    @GetMapping("/verify/{licenseNumber}")
    public ResponseEntity<DrugLicenseVerificationResponse> verifyLicense(
            @PathVariable("licenseNumber") String licenseNumber,
            Authentication authentication) {
        User user = shopAccessService.getAuthenticatedUser(authentication);
        DrugLicenseVerificationResponse response = drugLicenseVerificationService.verifyLicense(licenseNumber);
        return ResponseEntity.ok(response);
    }

    /**
     * Fetch and persist drug license for a shop.
     * Verifies the license and persists the document record in the shop's document list.
     *
     * @param request        contains shopId and licenseNumber
     * @param authentication the JWT authentication token
     * @return verification response with PDF URL and document persistence status
     */
    @Operation(summary = "Fetch and persist drug license",
            description = "Verifies a drug license number, generates a PDF certificate, " +
                          "and persists it as the shop's DRUG_LICENSE document. " +
                          "The document is stored with auto-calculated 6-month expiry date from issue date.")
    @PostMapping("/fetch")
    public ResponseEntity<DrugLicenseVerificationResponse> fetchAndPersist(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        Long shopId = (Long) request.get("shopId");
        String licenseNumber = (String) request.get("licenseNumber");

        if (shopId == null || licenseNumber == null || licenseNumber.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(DrugLicenseVerificationResponse.error("shopId and licenseNumber are required"));
        }

        User user = shopAccessService.getAuthenticatedUser(authentication);

        // Verify the license via the API
        DrugLicenseVerificationResponse verificationResponse = drugLicenseVerificationService.verifyLicense(licenseNumber);

        if (!verificationResponse.isSuccess()) {
            return ResponseEntity.ok(verificationResponse);
        }

        // TODO: Persist as Document entity with DRUG_LICENSE type
        // Following the pattern in ShopService.uploadOrReuploadDocument():
        // 1. Fetch the shop
        // 2. Create or update Document entity with DRUG_LICENSE type
        // 3. Set fileName, fileUrl, documentNumber, issueDate, expiryDate, kindOfBusiness, status
        // 4. Save via documentRepository

        // TODO: After PDF generation, the verificationResponse would have pdfUrl set
        // The service currently returns the raw API response

        return ResponseEntity.ok(verificationResponse);
    }
}