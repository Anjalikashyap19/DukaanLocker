package com.shoplocker.fssai.service;

import com.shoplocker.fssai.dto.*;
import com.shoplocker.fssai.entity.*;
import com.shoplocker.fssai.exception.FailureCode;
import com.shoplocker.fssai.exception.FssaiException;
import com.shoplocker.fssai.repository.DocumentRepository;
import com.shoplocker.fssai.repository.PaymentTransactionRepository;
import com.shoplocker.fssai.repository.RenewalOrderRepository;
import com.shoplocker.fssai.repository.ShopRepository;
import com.shoplocker.fssai.repository.UserRepository;
import com.shoplocker.fssai.util.DlIds;
import lombok.extern.slf4j.Slf4j;
import java.util.Arrays;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Payment service for FSSAI document renewal via the Easebuzz gateway.
 *
 * <p>Pricing (all amounts in INR, GST 18% flat on service charge only):</p>
 * <ul>
 *   <li><b>Basic:</b> gov ₹100/yr + service (1y ₹500, 2y ₹700, 3y ₹900, 4y ₹1,200, 5y ₹1,500)</li>
 *   <li><b>State:</b> gov by KOB (retailer/wholesaler/hotel/restaurant ₹2,000;
 *       manufacturing/dairy ₹3,000; mills ₹5,000) + service ₹1,000/yr</li>
 *   <li><b>Central:</b> gov ₹8,850/yr + service ₹2,000/yr</li>
 * </ul>
 * <p>Total = (govCharge × years) + (serviceCharge × years) + 18% × (serviceCharge × years)
 *   (+ penalty, applied only after expiry — reserved column, default 0).</p>
 */
@Slf4j
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private static final BigDecimal GST_RATE = new BigDecimal("0.18");

    /** Basic-tier service charge by duration (1-5 years). */
    private static final Map<Integer, BigDecimal> BASIC_SERVICE_CHARGE = Map.of(
            1, new BigDecimal("500"),
            2, new BigDecimal("700"),
            3, new BigDecimal("900"),
            4, new BigDecimal("1200"),
            5, new BigDecimal("1500")
    );

    /** State-tier gov charge by kind of business. */
    private static final Map<String, BigDecimal> STATE_GOV_CHARGE_BY_KOB = Map.of(
            "retailer", new BigDecimal("2000"),
            "wholeseller", new BigDecimal("2000"),
            "wholesaler", new BigDecimal("2000"),
            "hotel", new BigDecimal("2000"),
            "restaurant", new BigDecimal("2000"),
            "manufacturing", new BigDecimal("3000"),
            "dairy", new BigDecimal("3000"),
            "mills", new BigDecimal("5000")
    );

    @Value("${app.payment.easbuzz.merchant-key:}")
    private String easbuzzMerchantKey;

    @Value("${app.payment.easbuzz.merchant-salt:}")
    private String easbuzzMerchantSalt;

    @Value("${app.payment.easbuzz.environment:test}")
    private String easbuzzEnvironment;

    @Value("${app.fssai.license-url:https://iadv.in/tracker/dist/check-details.php?lic_num=}")
    private String fssaiLicenseApiUrl;

    @Value("${app.fssai.referer:https://iadv.in/}")
    private String fssaiReferer;

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final DocumentRepository documentRepository;
    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final RenewalOrderRepository renewalOrderRepository;
    private final ShopAccessService shopAccessService;
    private final RenewalOrderService renewalOrderService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PaymentService(PaymentTransactionRepository paymentTransactionRepository,
                          DocumentRepository documentRepository,
                          ShopRepository shopRepository,
                          UserRepository userRepository,
                          RenewalOrderRepository renewalOrderRepository,
                          ShopAccessService shopAccessService,
                          RenewalOrderService renewalOrderService,
                          NotificationService notificationService) {
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.documentRepository = documentRepository;
        this.shopRepository = shopRepository;
        this.userRepository = userRepository;
        this.renewalOrderRepository = renewalOrderRepository;
        this.shopAccessService = shopAccessService;
        this.renewalOrderService = renewalOrderService;
        this.notificationService = notificationService;
    }

    /** Calculate the full cost breakdown for a renewal payment.
     *
     * @param daysExpired Days past expiry (0 if not expired). Used to calculate penalty:
     *   0-90 days: 3x gov charge, 90-180 days: 5x gov charge, 180+ days: cannot renew.
     */
    @Transactional(readOnly = true)
    public PaymentBreakdown calculateBreakdown(String documentType, Integer durationYears, String kob,
                                               long daysExpired) {
        if (durationYears == null || durationYears < 1 || durationYears > 5) {
            throw new FssaiException("durationYears must be between 1 and 5", FailureCode.INVALID_REQUEST);
        }

        if (documentType == null || documentType.isBlank()) {
            log.warn("calculateBreakdown called with null/blank documentType");
            throw new FssaiException("documentType is required", FailureCode.INVALID_REQUEST);
        }

        // Handle case where documentType might already be a DocumentType enum name or have different casing
        String normalizedType = documentType.trim().toUpperCase().replace("-", "_");
        DocumentType docType;
        try {
            docType = DocumentType.valueOf(normalizedType);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid document type received: '{}' (normalized: '{}')", documentType, normalizedType);
            throw new FssaiException("Invalid document type: " + documentType + ". Supported: " + Arrays.toString(DocumentType.values()), FailureCode.INVALID_REQUEST);
        }

        BigDecimal govChargePerYear;
        BigDecimal serviceChargePerYear;

        String licenseCategory = docType.name();

        if (docType == DocumentType.FSSAI_FOOD_LICENSE) {
            // Determine tier from license category (Basic / State / Central)
            String category = licenseCategory != null ? licenseCategory.toUpperCase(Locale.ROOT) : "";
            if (category.contains("CENTRAL")) {
                govChargePerYear = new BigDecimal("8850");
                serviceChargePerYear = new BigDecimal("2000");
            } else if (category.contains("STATE")) {
                govChargePerYear = stateGovCharge(kob);
                serviceChargePerYear = new BigDecimal("1000");
            } else {
                // Basic (default for FSSAI)
                govChargePerYear = new BigDecimal("100");
                serviceChargePerYear = BASIC_SERVICE_CHARGE.get(durationYears);
                if (serviceChargePerYear == null) {
                    throw new FssaiException("Unsupported duration for Basic license: " + durationYears,
                            FailureCode.INVALID_REQUEST);
                }
            }
        } else {
            throw new FssaiException("Renewal payments are only supported for FSSAI Food License",
                    FailureCode.INVALID_REQUEST);
        }

        BigDecimal subtotal = govChargePerYear.multiply(new BigDecimal(durationYears));
        BigDecimal serviceCharge = serviceChargePerYear.multiply(new BigDecimal(durationYears));
        BigDecimal gstAmount = serviceCharge.multiply(GST_RATE).setScale(2, RoundingMode.HALF_UP);

        // Penalty calculation for expired documents
        BigDecimal penalty = BigDecimal.ZERO;
        if (daysExpired > 0) {
            if (daysExpired <= 90) {
                penalty = govChargePerYear.multiply(new BigDecimal(3));
            } else if (daysExpired <= 180) {
                penalty = govChargePerYear.multiply(new BigDecimal(5));
            } else {
                throw new FssaiException("License expired more than 180 days ago. Cannot renew.",
                        FailureCode.INVALID_REQUEST);
            }
        }

        BigDecimal totalAmount = subtotal.add(serviceCharge).add(gstAmount).add(penalty);

        return new PaymentBreakdown(
                subtotal.setScale(2, RoundingMode.HALF_UP),
                govChargePerYear.setScale(2, RoundingMode.HALF_UP),
                serviceCharge.setScale(2, RoundingMode.HALF_UP),
                gstAmount,
                penalty.setScale(2, RoundingMode.HALF_UP),
                totalAmount.setScale(2, RoundingMode.HALF_UP),
                18
        );
    }

    private BigDecimal stateGovCharge(String kob) {
        if (kob == null || kob.isBlank()) {
            throw new FssaiException("Kind of business is required for State-tier FSSAI pricing",
                    FailureCode.INVALID_REQUEST);
        }
        String key = kob.trim().toLowerCase(Locale.ROOT);
        for (Map.Entry<String, BigDecimal> entry : STATE_GOV_CHARGE_BY_KOB.entrySet()) {
            if (key.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        throw new FssaiException("Unknown kind of business for State-tier pricing: " + kob,
                FailureCode.INVALID_REQUEST);
    }

    /**
     * Create a PENDING payment transaction for a document renewal.
     * Resolves the license category and KOB from the previously uploaded FSSAI document.
     */
    @Transactional
    public PaymentCreateResponse createPayment(User user, Long shopId, String documentType,
                                               Integer durationYears) {
        try {
            if (documentType == null || documentType.isBlank()) {
                log.warn("createPayment called with null/blank documentType for user={}, shopId={}", user.getId(), shopId);
                throw new FssaiException("documentType is required", FailureCode.INVALID_REQUEST);
            }
            log.info("Creating renewal payment for user={}, shopId={}, documentType={}, durationYears={}",
                    user.getId(), shopId, documentType, durationYears);

            shopAccessService.validateShopAccess(user, shopId);

            Shop shop = shopRepository.findById(shopId)
                    .orElseThrow(() -> new FssaiException("Shop not found", FailureCode.SHOP_NOT_FOUND));

            // Look up the previously uploaded FSSAI document for this shop
            Optional<Document> existingDoc = documentRepository.findByShopIdAndDocumentType(
                    shopId, DocumentType.FSSAI_FOOD_LICENSE);
            if (existingDoc.isEmpty()) {
                log.warn("No FSSAI Food License document found for shopId={}, user={}", shopId, user.getId());
                throw new FssaiException("No FSSAI Food License document found for this shop",
                        FailureCode.DOCUMENT_NOT_FOUND);
            }

            Document doc = existingDoc.get();
            String licenseNumber = doc.getDocumentNumber();
            if (licenseNumber == null || licenseNumber.isBlank()) {
                throw new FssaiException("FSSAI license number not found on the uploaded document",
                        FailureCode.INVALID_REQUEST);
            }

            // Resolve KOB: prefer stored value, otherwise call the upstream FSSAI API
            String kob = doc.getKindOfBusiness();
            if (kob == null || kob.isBlank()) {
                try {
                    kob = fetchKobFromFssaiApi(licenseNumber);
                    if (kob != null && !kob.isBlank()) {
                        doc.setKindOfBusiness(kob);
                        documentRepository.save(doc);
                    }
                } catch (Exception e) {
                    log.warn("Failed to fetch KOB from FSSAI API for licenseNumber={}: {}", licenseNumber, e.getMessage());
                    // Continue without KOB — pricing will fall back to Basic tier
                }
            }

            // Determine license tier from stored category / document type
            String licenseCategory = doc.getDocumentType() != null ? doc.getDocumentType().name() : "FSSAI_FOOD_LICENSE";

            // Calculate days expired for penalty calculation
            long daysExpired = 0;
            if (doc.getExpiryDate() != null && doc.getExpiryDate().toLocalDate().isBefore(LocalDate.now())) {
                daysExpired = ChronoUnit.DAYS.between(doc.getExpiryDate().toLocalDate(), LocalDate.now());
            }

            PaymentBreakdown breakdown = calculateBreakdown(documentType, durationYears, kob, daysExpired);

            // Idempotency: return an existing open PENDING payment for the same shop+doc+duration
            Optional<PaymentTransaction> existing = paymentTransactionRepository
                    .findTopByUserIdAndShopIdAndDocumentTypeAndDocumentIdAndStatusOrderByCreatedAtDesc(
                            user.getId(), shopId, DocumentType.valueOf(documentType.toUpperCase().replace("-", "_")), doc.getId(),
                            PaymentTransaction.STATUS_PENDING);
            if (existing.isPresent()) {
                PaymentTransaction pt = existing.get();
                return toResponse(pt, breakdownFromEntity(pt));
            }

            PaymentTransaction tx = new PaymentTransaction(
                    user, shop, DocumentType.FSSAI_FOOD_LICENSE, doc.getId(),
                    durationYears,                 breakdown.getSubtotal(), breakdown.getGovCharge(),
                    breakdown.getServiceCharge(), breakdown.getGstAmount(),
                    breakdown.getPenalty(), breakdown.getTotalAmount(), "INR"
            );
            tx = paymentTransactionRepository.save(tx);

            // Build Easebuzz checkout data
            String orderId = "DL" + tx.getId() + System.currentTimeMillis();
            String gatewayCheckoutUrl = buildEasebuzzCheckoutUrl(orderId, breakdown.getTotalAmount(),
                    user, shop, tx.getId());

            tx.setGatewayOrderId(orderId);
            paymentTransactionRepository.save(tx);

            log.info("Payment created: tx={} user={} shop={} amount={} orderId={}",
                    tx.getId(), user.getId(), shopId, breakdown.getTotalAmount(), orderId);

            return new PaymentCreateResponse(
                    tx.getId(),
                    breakdown,
                    tx.getStatus(),
                    tx.getCurrency(),
                    tx.getGateway(),
                    orderId,
                    gatewayCheckoutUrl
            );
        } catch (FssaiException e) {
            log.warn("Payment creation failed with FssaiException: {} (code={})", e.getMessage(), e.getFailureCode());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error creating payment for user={}, shopId={}, docType={}: {}",
                    user.getId(), shopId, documentType, e.getMessage(), e);
            throw new FssaiException("Unexpected error: " + e.getMessage(), FailureCode.INTERNAL_ERROR, e);
        }
    }

    /**
     * Verify an Easebuzz webhook callback and mark the transaction COMPLETED.
     * Creates/links a RenewalOrder and notifies the user.
     */
    @Transactional
    public PaymentStatusResponse completePayment(Long paymentId, String gatewayPaymentId,
                                                 String gatewaySignature) {
        PaymentTransaction tx = paymentTransactionRepository.findById(paymentId)
                .orElseThrow(() -> new FssaiException("Payment transaction not found",
                        FailureCode.NOT_FOUND));

        if (PaymentTransaction.STATUS_COMPLETED.equals(tx.getStatus())) {
            return toStatusResponse(tx);
        }

        // Verify Easebuzz signature
        if (!verifyEasebuzzSignature(tx, gatewayPaymentId, gatewaySignature)) {
            tx.setStatus(PaymentTransaction.STATUS_FAILED);
            paymentTransactionRepository.save(tx);
            throw new FssaiException("Invalid payment signature", FailureCode.INVALID_REQUEST);
        }

        tx.setStatus(PaymentTransaction.STATUS_COMPLETED);
        tx.setGatewayPaymentId(gatewayPaymentId);
        tx.setGatewaySignature(gatewaySignature);
        tx = paymentTransactionRepository.save(tx);

        // Create / link a renewal order
        RenewalOrder renewalOrder = new RenewalOrder(
                tx.getUser(), tx.getShop(), tx.getDocumentType(), tx.getDocumentId(),
                ensureDlId(tx.getUser())
        );
        renewalOrder.setPaymentId(String.valueOf(tx.getId()));
        renewalOrder.setPaymentStatus(PaymentTransaction.STATUS_COMPLETED);
        renewalOrder.setDurationYears(tx.getDurationYears());
        renewalOrder.setNotes("Renewal paid via Easebuzz (payment id " + tx.getId() + ")");
        renewalOrder = renewalOrderRepository.save(renewalOrder);

        tx.setRenewalOrderId(renewalOrder.getId());
        paymentTransactionRepository.save(tx);

        String docName = com.shoplocker.fssai.scheduler.NotificationCopy.formatDocumentName(
                tx.getDocumentType());
        notificationService.sendPushAndCreateNotification(
                tx.getUser().getId(),
                "Payment received — renewal in progress",
                String.format("Your %s renewal payment for %s (₹%s) is confirmed. " +
                                "The certificate will be uploaded to your Dukaan Locker within 24 hours.",
                        docName, tx.getShop().getShopName(), tx.getTotalAmount()),
                "RENEWAL_REQUESTED",
                tx.getShop().getId(),
                Map.of(
                        "shopId", String.valueOf(tx.getShop().getId()),
                        "documentType", tx.getDocumentType().name(),
                        "renewalId", String.valueOf(renewalOrder.getId()),
                        "paymentId", String.valueOf(tx.getId()),
                        "route", "renewal"
                ),
                "renewal:payment:" + tx.getId() + ":completed"
        );

        log.info("Payment completed: tx={} renewalOrder={}", tx.getId(), renewalOrder.getId());
        return toStatusResponse(tx);
    }

    @Transactional(readOnly = true)
    public PaymentStatusResponse getPayment(Long paymentId, User user) {
        PaymentTransaction tx = paymentTransactionRepository.findById(paymentId)
                .orElseThrow(() -> new FssaiException("Payment transaction not found",
                        FailureCode.NOT_FOUND));
        if (!tx.getUser().getId().equals(user.getId())) {
            throw new FssaiException("You do not own this payment", FailureCode.FORBIDDEN);
        }
        return toStatusResponse(tx);
    }

    @Transactional(readOnly = true)
    public List<PaymentTransaction> getMyPayments(User user) {
        return paymentTransactionRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
    }

    /** Ensure the user has a DL ID, assigning one on first use. */
    private String ensureDlId(User user) {
        if (DlIds.isMissing(user.getDlId())) {
            String dlId = DlIds.forUser(user.getId());
            user.setDlId(dlId);
            userRepository.save(user);
            return dlId;
        }
        return user.getDlId();
    }

    /** Fetch the kind of business from the FSSAI supplementary API. */
    private String fetchKobFromFssaiApi(String licenseNumber) {
        try {
            String url = fssaiLicenseApiUrl + licenseNumber;
            RequestConfig config = RequestConfig.custom()
                    .setConnectTimeout(Timeout.ofSeconds(10))
                    .setResponseTimeout(Timeout.ofSeconds(20))
                    .setConnectionRequestTimeout(Timeout.ofSeconds(10))
                    .build();
            try (CloseableHttpClient client = HttpClients.custom()
                    .setDefaultRequestConfig(config)
                    .build()) {
                HttpGet request = new HttpGet(url);
                request.setHeader("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                request.setHeader("Accept", "application/json, text/plain, */*");
                request.setHeader("Referer", fssaiReferer);

                return client.execute(request, response -> {
                    int status = response.getCode();
                    if (status != 200) return null;
                    String body = EntityUtils.toString(response.getEntity());
                    if (body == null || body.isBlank()) return null;
                    try {
                        JsonNode root = objectMapper.readTree(body);
                        JsonNode license = root.path("license");
                        JsonNode target;
                        if (license.isMissingNode() || license.isNull() || license.size() == 0) {
                            JsonNode details = root.path("details");
                            if (details.isMissingNode() || details.isNull()) return null;
                            target = details;
                        } else {
                            target = license;
                        }
                        return text(target, "kobname");
                    } catch (Exception e) {
                        log.warn("Could not parse FSSAI KOB API response for {}", licenseNumber, e);
                        return null;
                    }
                });
            }
        } catch (Exception e) {
            log.warn("FSSAI KOB API call failed for license {}: {}", licenseNumber, e.getMessage());
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        String text = value.asText();
        return (text == null || text.trim().isEmpty()) ? null : text.trim();
    }

    /** Build the Easebuzz checkout URL for the Android client to open. */
    private String buildEasebuzzCheckoutUrl(String orderId, BigDecimal amount,
                                            User user, Shop shop, Long txId) {
        String base = "sandbox".equalsIgnoreCase(easbuzzEnvironment)
                ? "https://sandbox.easebuzz.in/pg/merchant/payment"
                : "https://www.easebuzz.in/pg/merchant/payment";
        // Easebuzz expects form fields: key|order_id|amount|productinfo|firstname|email|udf5|salt
        // for signature. We include productinfo and udf5 for proper SDK/hosted page support.
        return base + "?key=" + encode(easbuzzMerchantKey)
                + "&order_id=" + encode(orderId)
                + "&amount=" + encode(amount.setScale(2, RoundingMode.HALF_UP).toPlainString())
                + "&currency=INR"
                + "&productinfo=FSSAI Renewal"
                + "&name=" + encode(user.getUserName())
                + "&email=" + encode(user.getEmailId() != null ? user.getEmailId() : user.getMobileNumber())
                + "&udf1=" + encode(String.valueOf(txId))
                + "&udf2=" + encode(shop.getShopName())
                + "&udf3=" + encode(String.valueOf(shop.getId()))
                + "&udf5=" + encode(user.getUserName())
                + "&return_url=" + encode(appReturnUrl(txId))
                + "&cancel_url=" + encode(appCancelUrl(txId));
    }

    private String appReturnUrl(Long txId) {
        return "dukaanlocker://payment/success?tx=" + txId;
    }

    private String appCancelUrl(Long txId) {
        return "dukaanlocker://payment/cancel?tx=" + txId;
    }

    private static String encode(String s) {
        if (s == null) return "";
        try {
            return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    /** Verify the Easebuzz webhook signature (merchant key + salt based checksum). */
    private boolean verifyEasebuzzSignature(PaymentTransaction tx, String gatewayPaymentId,
                                            String gatewaySignature) {
        if (easbuzzMerchantKey == null || easbuzzMerchantKey.isBlank()
                || easbuzzMerchantSalt == null || easbuzzMerchantSalt.isBlank()) {
            log.warn("Easebuzz merchant key/salt not configured; accepting callback without signature");
            return true;
        }
        if (gatewaySignature == null || gatewaySignature.isBlank()) return false;

        // Easebuzz checksum: sha512(key|txnid|amount|productinfo|firstname|email|udf5|salt)
        // Fields must match exactly what is sent in the checkout URL
        String data = easbuzzMerchantKey + "|" + tx.getGatewayOrderId() + "|"
                + tx.getTotalAmount() + "|FSSAI Renewal|" + tx.getUser().getUserName() + "|"
                + (tx.getUser().getEmailId() != null ? tx.getUser().getEmailId() : tx.getUser().getMobileNumber())
                + "|" + tx.getUser().getUserName() + "|" + easbuzzMerchantSalt;
        String expected = sha512(data);
        return expected.equalsIgnoreCase(gatewaySignature);
    }

    private static String sha512(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-512");
            byte[] hash = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-512 not available", e);
        }
    }

    private PaymentCreateResponse toResponse(PaymentTransaction tx, PaymentBreakdown breakdown) {
        return new PaymentCreateResponse(
                tx.getId(), breakdown, tx.getStatus(), tx.getCurrency(),
                tx.getGateway(), tx.getGatewayOrderId(), null
        );
    }

    private PaymentStatusResponse toStatusResponse(PaymentTransaction tx) {
        return new PaymentStatusResponse(
                tx.getId(), tx.getStatus(), tx.getTotalAmount(), tx.getCurrency(),
                tx.getGatewayPaymentId(), tx.getRenewalOrderId()
        );
    }

    private PaymentBreakdown breakdownFromEntity(PaymentTransaction tx) {
        return new PaymentBreakdown(
                tx.getSubtotal(), tx.getGovCharge(), tx.getServiceCharge(),
                tx.getGstAmount(), tx.getPenalty(), tx.getTotalAmount(), 18
        );
    }

}
