package com.shoplocker.fssai.dto;

/**
 * Payment status returned for a payment transaction or webhook callback.
 */
public class PaymentStatusResponse {

    private Long paymentId;
    private String status;
    private java.math.BigDecimal totalAmount;
    private String currency;
    private String gatewayPaymentId;
    private Long renewalOrderId;

    public PaymentStatusResponse() {}

    public PaymentStatusResponse(Long paymentId, String status, java.math.BigDecimal totalAmount,
                                 String currency, String gatewayPaymentId, Long renewalOrderId) {
        this.paymentId = paymentId;
        this.status = status;
        this.totalAmount = totalAmount;
        this.currency = currency;
        this.gatewayPaymentId = gatewayPaymentId;
        this.renewalOrderId = renewalOrderId;
    }

    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public java.math.BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(java.math.BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getGatewayPaymentId() { return gatewayPaymentId; }
    public void setGatewayPaymentId(String gatewayPaymentId) { this.gatewayPaymentId = gatewayPaymentId; }

    public Long getRenewalOrderId() { return renewalOrderId; }
    public void setRenewalOrderId(Long renewalOrderId) { this.renewalOrderId = renewalOrderId; }
}
