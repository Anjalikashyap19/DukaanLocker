package com.shoplocker.fssai.controller;

import com.shoplocker.fssai.dto.FssaiFetchRequest;
import com.shoplocker.fssai.dto.FssaiVerificationResponse;
import com.shoplocker.fssai.entity.DocumentType;
import com.shoplocker.fssai.entity.Shop;
import com.shoplocker.fssai.entity.User;
import com.shoplocker.fssai.service.FssaiVerificationService;
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
 * Endpoints for FSSAI food license verification.
 * The endpoints are whitelisted in {@code SecurityConfig} under {@code /api/fssai/**}.
 */
@RestController
@RequestMapping("/api/fssai")
@Tag(name = "FSSAI Verification", description = "FSSAI food license verification — verify and fetch license details, generate the certificate PDF and persist it for a shop.")
public class FssaiVerificationController {

    private final FssaiVerificationService fssaiService;
    private final ShopService shopService;
    private final ShopAccessService shopAccessService;

    public FssaiVerificationController(FssaiVerificationService fssaiService,
                                       ShopService shopService,
                                       ShopAccessService shopAccessService) {
        this.fssaiService = fssaiService;
        this.shopService = shopService;
        this.shopAccessService = shopAccessService;
    }

    @Operation(
            summary = "Verify FSSAI license number (read-only)",
            description = "Verifies a 14-digit FSSAI food license number against the license portal, " +
                          "returns the food business operator details, generates a PDF certificate " +
                          "and uploads it to local storage. Does NOT persist the document record — use /fetch for that."
    )
    @GetMapping("/verify/{licenseNumber}")
    public ResponseEntity<FssaiVerificationResponse> verifyFssaiLicense(@PathVariable String licenseNumber) {
        FssaiVerificationResponse response = fssaiService.verifyLicense(licenseNumber, null, null);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Fetch and persist FSSAI food license document",
            description = "Verifies a 14-digit FSSAI license number, generates a PDF certificate, " +
                          "uploads it to local storage (VPS document path) and persists it as an " +
                          "FSSAI Food License document for the given shop, including the expiry date. " +
                          "The FBO name returned by the FSSAI API must match the shop name the user " +
                          "created — otherwise no certificate is created and an error is returned. " +
                          "Returns the verification details including the PDF URL."
    )
    @PostMapping("/fetch")
    @SecurityRequirement(name = "bearer-jwt")
    public ResponseEntity<FssaiVerificationResponse> fetchAndPersistFssai(
            @Valid @RequestBody FssaiFetchRequest request,
            Authentication authentication) {

        // Validate shop ownership
        User user = shopAccessService.getAuthenticatedUser(authentication);
        Long shopId = Long.parseLong(request.getShopId());
        shopAccessService.validateShopAccess(user, shopId);

        // The certificate may only be created when the FBO name returned by the FSSAI
        // API matches the shop / business name the user created.
        Shop shop = shopService.getShopById(shopId);

        FssaiVerificationResponse verification =
                fssaiService.verifyLicense(request.getLicenseNumber(), user.getId(), shopId,
                        shop != null ? shop.getShopName() : null);

        if (!verification.isSuccess()) {
            return ResponseEntity.ok(verification);
        }

        LocalDateTime expiryDate = fssaiService.parseExpiryDate(verification.getExpiryDate());

        shopService.uploadOrReuploadDocument(
                shopId,
                DocumentType.FSSAI_FOOD_LICENSE,
                "FSSAI_License_" + verification.getLicenseNumber() + ".pdf",
                verification.getPdfUrl(),
                verification.getLicenseNumber(),
                null,
                expiryDate
        );

        return ResponseEntity.ok(verification);
    }
}
