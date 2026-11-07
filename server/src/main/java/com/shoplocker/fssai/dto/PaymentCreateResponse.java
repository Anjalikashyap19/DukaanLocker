package com.shoplocker.fssai.dto;

/**
 * Response returned when a renewal payment is created. Contains the cost
 * breakdown and the Easebuzz payment link (checkout URL) the client opens.
 */
public class PaymentCreateResponse {

    private Long paymentId;
    private PaymentBreakdown breakdown;
    private String status;
    private String currency;
    private String gateway;
    private String gatewayOrderId;
    private String kob;
    private String gatewayCheckoutUrl;
    private String accessKey;

    public PaymentCreateResponse() {}

    public PaymentCreateResponse(Long paymentId, PaymentBreakdown breakdown, String status,
                                 String currency, String gateway, String gatewayOrderId,
                                 String kob, String gatewayCheckoutUrl, String accessKey) {
        this.paymentId = paymentId;
        this.breakdown = breakdown;
        this.status = status;
        this.currency = currency;
        this.gateway = gateway;
        this.gatewayOrderId = gatewayOrderId;
        this.kob = kob;
        this.gatewayCheckoutUrl = gatewayCheckoutUrl;
        this.accessKey = accessKey;
    }

    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }

    public PaymentBreakdown getBreakdown() { return breakdown; }
    public void setBreakdown(PaymentBreakdown breakdown) { this.breakdown = breakdown; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getGateway() { return gateway; }
    public void setGateway(String gateway) { this.gateway = gateway; }

    public String getGatewayOrderId() { return gatewayOrderId; }
    public void setGatewayOrderId(String gatewayOrderId) { this.gatewayOrderId = gatewayOrderId; }

    public String getKob() { return kob; }
    public void setKob(String kob) { this.kob = kob; }

    public String getGatewayCheckoutUrl() { return gatewayCheckoutUrl; }
    public void setGatewayCheckoutUrl(String gatewayCheckoutUrl) { this.gatewayCheckoutUrl = gatewayCheckoutUrl; }

    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
}
