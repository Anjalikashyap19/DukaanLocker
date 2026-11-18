package com.shoplocker.fssai.controller;

import com.shoplocker.fssai.dto.DrugLicenseFetchRequest;
import com.shoplocker.fssai.dto.DrugLicenseVerificationResponse;
import com.shoplocker.fssai.entity.DocumentType;
import com.shoplocker.fssai.entity.Shop;
import com.shoplocker.fssai.entity.User;
import com.shoplocker.fssai.service.DrugLicenseVerificationService;
import com.shoplocker.fssai.service.ShopAccessService;
import com.shoplocker.fssai.service.ShopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * Endpoints for drug license verification.
 * The endpoints are whitelisted in {@code SecurityConfig} under {@code /api/drug-license/**}.
 */
@RestController
@RequestMapping("/api/drug-license")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Drug License Verification", description = "Verify and fetch drug license certificates from the State Drugs Department API.")
public class DrugLicenseVerificationController {

    private final DrugLicenseVerificationService drugLicenseVerificationService;
    private final ShopService shopService;
    private final ShopAccessService shopAccessService;

    public DrugLicenseVerificationController(
            DrugLicenseVerificationService drugLicenseVerificationService,
            ShopService shopService,
            ShopAccessService shopAccessService) {
        this.drugLicenseVerificationService = drugLicenseVerificationService;
        this.shopService = shopService;
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
        DrugLicenseVerificationResponse response = drugLicenseVerificationService.verifyLicense(
                licenseNumber, user.getId(), null, null);
        return ResponseEntity.ok(response);
    }

    /**
     * Fetch and persist drug license for a shop.
     * Verifies the license, generates a PDF certificate, uploads it to local storage,
     * and persists it as the shop's DRUG_LICENSE document.
     *
     * @param request        contains shopId and licenseNumber
     * @param authentication the JWT authentication token
     * @return verification response with PDF URL and document persistence status
     */
    @Operation(summary = "Fetch and persist drug license",
            description = "Verifies a drug license number, generates a PDF certificate, " +
                          "uploads it to local storage and persists it as a " +
                          "DRUG_LICENSE document for the given shop, including the expiry date. " +
                          "The license holder name returned by the State Drugs Department API must match " +
                          "the shop name the user created — otherwise no certificate is created and an error is returned. " +
                          "Returns the verification details including the PDF URL.")
    @PostMapping("/fetch")
    @SecurityRequirement(name = "bearer-jwt")
    public ResponseEntity<DrugLicenseVerificationResponse> fetchAndPersist(
            @Valid @RequestBody DrugLicenseFetchRequest request,
            Authentication authentication) {

        // Validate shop ownership
        User user = shopAccessService.getAuthenticatedUser(authentication);
        Long shopId = Long.parseLong(request.getShopId());
        shopAccessService.validateShopAccess(user, shopId);

        // The certificate may only be created when the license holder name returned by the
        // State Drugs Department API matches the shop / business name the user created.
        Shop shop = shopService.getShopById(shopId);

        DrugLicenseVerificationResponse verification =
                drugLicenseVerificationService.verifyLicense(
                        request.getLicenseNumber(), user.getId(), shopId,
                        shop != null ? shop.getShopName() : null);

        if (!verification.isSuccess()) {
            return ResponseEntity.ok(verification);
        }

        LocalDateTime expiryDate = parseExpiryDate(verification.getExpiryDate());
        LocalDateTime issueDate = parseExpiryDate(verification.getIssueDate());

        shopService.uploadOrReuploadDocument(
                shopId,
                DocumentType.DRUG_LICENSE,
                "DrugLicense_" + verification.getLicenseNumber() + ".pdf",
                verification.getPdfUrl(),
                verification.getLicenseNumber(),
                issueDate,
                expiryDate,
                null,
                verification.getKindOfBusiness()
        );

        return ResponseEntity.ok(verification);
    }

    private LocalDateTime parseExpiryDate(String date) {
        if (date == null || date.trim().isEmpty()) {
            return null;
        }
        String candidate = date.trim();
        java.time.format.DateTimeFormatter[] formatters = {
                java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"),
                java.time.format.DateTimeFormatter.ofPattern("d-M-yyyy"),
                java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                java.time.format.DateTimeFormatter.ofPattern("d/M/yyyy"),
                java.time.format.DateTimeFormatter.ISO_LOCAL_DATE
        };
        for (java.time.format.DateTimeFormatter formatter : formatters) {
            try {
                return java.time.LocalDate.parse(candidate, formatter).atStartOfDay();
            } catch (Exception ignored) {
                // try the next shape
            }
        }
        String dateToken = candidate.split("[T ]")[0];
        if (!dateToken.equals(candidate)) {
            for (java.time.format.DateTimeFormatter formatter : formatters) {
                try {
                    return java.time.LocalDate.parse(dateToken, formatter).atStartOfDay();
                } catch (Exception ignored) {
                    // try the next shape
                }
            }
        }
        log.warn("Could not parse drug license date: {}", date);
        return null;
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DrugLicenseVerificationController.class);
}
