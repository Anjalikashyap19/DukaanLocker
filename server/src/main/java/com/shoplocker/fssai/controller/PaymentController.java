package com.shoplocker.fssai.controller;

import com.shoplocker.fssai.dto.FssaReturnFilingRequest;
import com.shoplocker.fssai.dto.PaymentBreakdown;
import com.shoplocker.fssai.dto.PaymentCreateRequest;
import com.shoplocker.fssai.dto.PaymentCreateResponse;
import com.shoplocker.fssai.dto.PaymentStatusResponse;
import com.shoplocker.fssai.entity.PaymentTransaction;
import com.shoplocker.fssai.entity.User;
import com.shoplocker.fssai.exception.FailureCode;
import com.shoplocker.fssai.exception.FssaiException;
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
                user, request.getShopId(), request.getDocumentType(), request.getDurationYears(), request.getKob());
        return ResponseEntity.ok(response);
    }

    /**
     * Create a payment for DukaanLocker to file the FSSAI annual return for a
     * shop. Amount = late fee (₹100/day after 31 May) + ₹2,000 service charge.
     * The filing details (turnover etc.) are captured with the payment.
     */
    @Operation(summary = "Create FSSAI annual return filing payment",
            description = "Returns an Easebuzz access key for the calculated late fee + service charge; " +
                          "the DukaanLocker team files the return manually after payment.")
    @PostMapping("/fssa-return")
    public ResponseEntity<PaymentCreateResponse> fssaReturn(
            @RequestBody FssaReturnFilingRequest request,
            Authentication authentication) {
        if (request == null || request.getShopId() == null) {
            throw new FssaiException("shopId is required", FailureCode.INVALID_REQUEST);
        }
        if (request.getAnnualTurnover() == null
                || request.getAnnualTurnover().signum() < 0) {
            throw new FssaiException("annualTurnover is required and must be zero or more",
                    FailureCode.INVALID_REQUEST);
        }
        User user = shopAccessService.getAuthenticatedUser(authentication);
        return ResponseEntity.ok(paymentService.createFssaReturnFilingPayment(user, request));
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
    @PostMapping(value = "/{id}/callback")
    public Object callback(
            @PathVariable Long id,
            @RequestParam(required = false) Map<String, String> formParams,
            @RequestBody(required = false) Map<String, String> jsonBody,
            @RequestHeader(value = "Content-Type", required = false) String contentType) {
        Map<String, String> body = (formParams != null && !formParams.isEmpty())
                ? formParams
                : (jsonBody != null ? jsonBody : Map.of());
        String gatewayPaymentId = body.get("easepayid") != null ? body.get("easepayid")
                : body.get("payment_id") != null ? body.get("payment_id")
                : body.get("txnid");
        String gatewaySignature = body.get("signature") != null ? body.get("signature")
                : body.get("hash");
        PaymentStatusResponse response = paymentService.completePayment(id, gatewayPaymentId, gatewaySignature);

        if (contentType != null && contentType.contains("application/x-www-form-urlencoded")) {
            String redirectUrl = "dukaanlocker://payment/"
                    + ("COMPLETED".equalsIgnoreCase(response.getStatus()) ? "success" : "cancel")
                    + "?tx=" + id;
            return ResponseEntity.status(org.springframework.http.HttpStatus.FOUND)
                    .header("Location", redirectUrl)
                    .build();
        }
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
