package com.shoplocker.fssai.dto;

import java.math.BigDecimal;

/**
 * Details the team needs to file an FSSAI annual return on the user's behalf.
 * Captured before payment so the work item carries everything required.
 */
public class FssaReturnFilingRequest {

    private Long shopId;
    private String financialYear;       // e.g. "2025-26"
    private BigDecimal annualTurnover;  // required
    private Integer employeeCount;      // optional (manufacturers)
    private String kindOfBusiness;      // optional
    private String foodCategory;        // optional
    private String notes;               // optional free text

    public FssaReturnFilingRequest() {}

    public Long getShopId() { return shopId; }
    public void setShopId(Long shopId) { this.shopId = shopId; }

    public String getFinancialYear() { return financialYear; }
    public void setFinancialYear(String financialYear) { this.financialYear = financialYear; }

    public BigDecimal getAnnualTurnover() { return annualTurnover; }
    public void setAnnualTurnover(BigDecimal annualTurnover) { this.annualTurnover = annualTurnover; }

    public Integer getEmployeeCount() { return employeeCount; }
    public void setEmployeeCount(Integer employeeCount) { this.employeeCount = employeeCount; }

    public String getKindOfBusiness() { return kindOfBusiness; }
    public void setKindOfBusiness(String kindOfBusiness) { this.kindOfBusiness = kindOfBusiness; }

    public String getFoodCategory() { return foodCategory; }
    public void setFoodCategory(String foodCategory) { this.foodCategory = foodCategory; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
