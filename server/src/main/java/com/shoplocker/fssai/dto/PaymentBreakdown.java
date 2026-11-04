package com.shoplocker.fssai.dto;

/**
 * Breakdown of a renewal payment amount, in INR.
 */
public class PaymentBreakdown {

    private java.math.BigDecimal subtotal;
    private java.math.BigDecimal govCharge;
    private java.math.BigDecimal serviceCharge;
    private java.math.BigDecimal gstAmount;
    private java.math.BigDecimal penalty;
    private java.math.BigDecimal totalAmount;
    private Integer gstRate; // percent, e.g. 18

    public PaymentBreakdown() {}

    public PaymentBreakdown(java.math.BigDecimal subtotal, java.math.BigDecimal govCharge,
                            java.math.BigDecimal serviceCharge, java.math.BigDecimal gstAmount,
                            java.math.BigDecimal penalty, java.math.BigDecimal totalAmount,
                            Integer gstRate) {
        this.subtotal = subtotal;
        this.govCharge = govCharge;
        this.serviceCharge = serviceCharge;
        this.gstAmount = gstAmount;
        this.penalty = penalty;
        this.totalAmount = totalAmount;
        this.gstRate = gstRate;
    }

    public java.math.BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(java.math.BigDecimal subtotal) { this.subtotal = subtotal; }

    public java.math.BigDecimal getGovCharge() { return govCharge; }
    public void setGovCharge(java.math.BigDecimal govCharge) { this.govCharge = govCharge; }

    public java.math.BigDecimal getServiceCharge() { return serviceCharge; }
    public void setServiceCharge(java.math.BigDecimal serviceCharge) { this.serviceCharge = serviceCharge; }

    public java.math.BigDecimal getGstAmount() { return gstAmount; }
    public void setGstAmount(java.math.BigDecimal gstAmount) { this.gstAmount = gstAmount; }

    public java.math.BigDecimal getPenalty() { return penalty; }
    public void setPenalty(java.math.BigDecimal penalty) { this.penalty = penalty; }

    public java.math.BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(java.math.BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public Integer getGstRate() { return gstRate; }
    public void setGstRate(Integer gstRate) { this.gstRate = gstRate; }
}
