package com.shoplocker.fssai.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A payment transaction for document renewal via the Easebuzz gateway.
 * One row per renewal payment attempt; status moves PENDING -> COMPLETED/FAILED
 * after the Easebuzz webhook verifies the signature.
 */
@Entity
@Table(name = "payment_transactions")
public class PaymentTransaction {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 50)
    private DocumentType documentType;

    @Column(name = "document_id")
    private Long documentId;

    @Column(nullable = false)
    private Integer durationYears;

    @Column(name = "subtotal", nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "gov_charge", nullable = false, precision = 10, scale = 2)
    private BigDecimal govCharge;

    @Column(name = "service_charge", nullable = false, precision = 10, scale = 2)
    private BigDecimal serviceCharge;

    @Column(name = "gst_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal gstAmount;

    @Column(name = "penalty", nullable = false, precision = 10, scale = 2)
    private BigDecimal penalty;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "gateway", length = 50)
    private String gateway;

    @Column(name = "gateway_order_id", length = 100)
    private String gatewayOrderId;

    @Column(name = "gateway_payment_id", length = 100)
    private String gatewayPaymentId;

    @Column(name = "gateway_signature", length = 200)
    private String gatewaySignature;

    @Column(name = "gateway_checkout_url", length = 500)
    private String gatewayCheckoutUrl;

    @Column(name = "renewal_order_id")
    private Long renewalOrderId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public PaymentTransaction() {}

    public PaymentTransaction(User user, Shop shop, DocumentType documentType, Long documentId,
                              Integer durationYears, BigDecimal subtotal,
                              BigDecimal govCharge, BigDecimal serviceCharge,
                              BigDecimal gstAmount, BigDecimal penalty,
                              BigDecimal totalAmount, String currency) {
        this.user = user;
        this.shop = shop;
        this.documentType = documentType;
        this.documentId = documentId;
        this.durationYears = durationYears;
        this.subtotal = subtotal;
        this.govCharge = govCharge;
        this.serviceCharge = serviceCharge;
        this.gstAmount = gstAmount;
        this.penalty = penalty;
        this.totalAmount = totalAmount;
        this.currency = currency;
        this.status = STATUS_PENDING;
        this.gateway = "easbuzz";
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) status = STATUS_PENDING;
        if (currency == null) currency = "INR";
        if (gateway == null) gateway = "easbuzz";
        if (penalty == null) penalty = BigDecimal.ZERO;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Shop getShop() { return shop; }
    public void setShop(Shop shop) { this.shop = shop; }

    public DocumentType getDocumentType() { return documentType; }
    public void setDocumentType(DocumentType documentType) { this.documentType = documentType; }

    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }

    public Integer getDurationYears() { return durationYears; }
    public void setDurationYears(Integer durationYears) { this.durationYears = durationYears; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public BigDecimal getGovCharge() { return govCharge; }
    public void setGovCharge(BigDecimal govCharge) { this.govCharge = govCharge; }

    public BigDecimal getServiceCharge() { return serviceCharge; }
    public void setServiceCharge(BigDecimal serviceCharge) { this.serviceCharge = serviceCharge; }

    public BigDecimal getGstAmount() { return gstAmount; }
    public void setGstAmount(BigDecimal gstAmount) { this.gstAmount = gstAmount; }

    public BigDecimal getPenalty() { return penalty; }
    public void setPenalty(BigDecimal penalty) { this.penalty = penalty; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getGateway() { return gateway; }
    public void setGateway(String gateway) { this.gateway = gateway; }

    public String getGatewayOrderId() { return gatewayOrderId; }
    public void setGatewayOrderId(String gatewayOrderId) { this.gatewayOrderId = gatewayOrderId; }

    public String getGatewayPaymentId() { return gatewayPaymentId; }
    public void setGatewayPaymentId(String gatewayPaymentId) { this.gatewayPaymentId = gatewayPaymentId; }

    public String getGatewaySignature() { return gatewaySignature; }
    public void setGatewaySignature(String gatewaySignature) { this.gatewaySignature = gatewaySignature; }

    public String getGatewayCheckoutUrl() { return gatewayCheckoutUrl; }
    public void setGatewayCheckoutUrl(String gatewayCheckoutUrl) { this.gatewayCheckoutUrl = gatewayCheckoutUrl; }

    public Long getRenewalOrderId() { return renewalOrderId; }
    public void setRenewalOrderId(Long renewalOrderId) { this.renewalOrderId = renewalOrderId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
