package com.shoplocker.fssai.controller;

import com.shoplocker.fssai.dto.RenewalOrderResponse;
import com.shoplocker.fssai.entity.DocumentType;
import com.shoplocker.fssai.entity.User;
import com.shoplocker.fssai.exception.FailureCode;
import com.shoplocker.fssai.exception.FssaiException;
import com.shoplocker.fssai.service.RenewalOrderService;
import com.shoplocker.fssai.service.ShopAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/renewals")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Renewals", description = "Renew document requests - DukaanLocker renews it for you")
public class RenewalController {

    private final RenewalOrderService renewalOrderService;
    private final ShopAccessService shopAccessService;

    public RenewalController(RenewalOrderService renewalOrderService,
                             ShopAccessService shopAccessService) {
        this.renewalOrderService = renewalOrderService;
        this.shopAccessService = shopAccessService;
    }

    public record CreateRenewalRequest(Long shopId, String documentType, String notes) {}

    public record StatusUpdateRequest(String status, String notes) {}

    @PostMapping
    @Operation(summary = "Request renewal of a document",
            description = "Creates a renewal order; the certificate is uploaded to the user's Dukaan Locker (DL ID folder) within 24 hours.")
    public ResponseEntity<RenewalOrderResponse> requestRenewal(
            @RequestBody CreateRenewalRequest request, Authentication authentication) {
        User user = shopAccessService.getAuthenticatedUser(authentication);
        if (request == null || request.shopId() == null || request.documentType() == null) {
            throw new FssaiException("shopId and documentType are required", FailureCode.INVALID_REQUEST);
        }
        DocumentType docType;
        try {
            docType = DocumentType.valueOf(request.documentType().toUpperCase().replace("-", "_"));
        } catch (IllegalArgumentException e) {
            throw new FssaiException("Invalid document type: " + request.documentType(), FailureCode.INVALID_REQUEST);
        }
        shopAccessService.validateShopAccess(user, request.shopId());
        return ResponseEntity.ok(renewalOrderService.requestRenewal(user, request.shopId(), docType, request.notes()));
    }

    @GetMapping
    @Operation(summary = "List the current user's renewal orders")
    public ResponseEntity<List<RenewalOrderResponse>> listMine(Authentication authentication) {
        User user = shopAccessService.getAuthenticatedUser(authentication);
        return ResponseEntity.ok(renewalOrderService.listForUser(user.getId()));
    }

    @GetMapping("/all")
    @Operation(summary = "List all renewal orders (ops/admin)")
    public ResponseEntity<List<RenewalOrderResponse>> listAll(Authentication authentication) {
        User user = shopAccessService.getAuthenticatedUser(authentication);
        if (user.getRole() != com.shoplocker.fssai.entity.Role.ADMIN) {
            throw new FssaiException("Admin access required", FailureCode.FORBIDDEN);
        }
        return ResponseEntity.ok(renewalOrderService.listAll());
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Update a renewal order's status (ops/admin)",
            description = "Setting COMPLETED notifies the user their certificate is in the locker.")
    public ResponseEntity<RenewalOrderResponse> updateStatus(
            @PathVariable Long id, @RequestBody StatusUpdateRequest request, Authentication authentication) {
        User user = shopAccessService.getAuthenticatedUser(authentication);
        if (user.getRole() != com.shoplocker.fssai.entity.Role.ADMIN) {
            throw new FssaiException("Admin access required", FailureCode.FORBIDDEN);
        }
        return ResponseEntity.ok(renewalOrderService.updateStatus(id, request.status(), request.notes()));
    }
}
