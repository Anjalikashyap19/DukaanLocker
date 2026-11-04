package com.shoplocker.fssai.controller;

import com.shoplocker.fssai.dto.PaymentBreakdown;
import com.shoplocker.fssai.dto.PaymentCreateRequest;
import com.shoplocker.fssai.dto.PaymentCreateResponse;
import com.shoplocker.fssai.dto.PaymentStatusResponse;
import com.shoplocker.fssai.entity.PaymentTransaction;
import com.shoplocker.fssai.entity.User;
import com.shoplocker.fssai.service.PaymentService;
import com.shoplocker.fssai.service.ShopAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Payments", description = "FSSAI document renewal payments via Easebuzz gateway")
public class PaymentController {

    private final PaymentService paymentService;
    private final ShopAccessService shopAccessService;

    public PaymentController(PaymentService paymentService,
                             ShopAccessService shopAccessService) {
        this.paymentService = paymentService;
        this.shopAccessService = shopAccessService;
    }

    /**
     * Create a renewal payment for an FSSAI document expiring within 15 days.
     * Returns the cost breakdown and the Easebuzz checkout URL.
     */
    @Operation(summary = "Create a renewal payment",
            description = "Calculates the renewal cost (Basic / State / Central tier) " +
                          "and returns a Easebuzz checkout URL for the customer to pay.")
    @PostMapping("/renew")
    public ResponseEntity<PaymentCreateResponse> renew(
            @Valid @RequestBody PaymentCreateRequest request,
            Authentication authentication) {
        User user = shopAccessService.getAuthenticatedUser(authentication);
        PaymentCreateResponse response = paymentService.createPayment(
                user, request.getShopId(), request.getDocumentType(), request.getDurationYears());
        return ResponseEntity.ok(response);
    }

    /**
     * Get the status of a payment transaction.
     */
    @Operation(summary = "Get payment status",
            description = "Returns the current status of a payment transaction.")
    @GetMapping("/{id}")
    public ResponseEntity<PaymentStatusResponse> getPayment(
            @PathVariable Long id, Authentication authentication) {
        User user = shopAccessService.getAuthenticatedUser(authentication);
        PaymentStatusResponse response = paymentService.getPayment(id, user);
        return ResponseEntity.ok(response);
    }

    /**
     * Easebuzz webhook callback. Verifies the signature and marks the payment COMPLETED.
     */
    @Operation(summary = "Easebuzz payment callback",
            description = "Webhook endpoint called by Easebuzz after the customer completes payment.")
    @PostMapping("/{id}/callback")
    public ResponseEntity<PaymentStatusResponse> callback(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String gatewayPaymentId = body.get("payment_id") != null ? body.get("payment_id")
                : body.get("txnid");
        String gatewaySignature = body.get("signature") != null ? body.get("signature")
                : body.get("hash");
        PaymentStatusResponse response = paymentService.completePayment(id, gatewayPaymentId, gatewaySignature);
        return ResponseEntity.ok(response);
    }

    /**
     * List the current user's payment transactions.
     */
    @Operation(summary = "List my payments",
            description = "Returns all payment transactions for the authenticated user.")
    @GetMapping("/my")
    public ResponseEntity<List<PaymentTransaction>> listMyPayments(Authentication authentication) {
        User user = shopAccessService.getAuthenticatedUser(authentication);
        List<PaymentTransaction> payments = paymentService.getMyPayments(user);
        return ResponseEntity.ok(payments);
    }
}
