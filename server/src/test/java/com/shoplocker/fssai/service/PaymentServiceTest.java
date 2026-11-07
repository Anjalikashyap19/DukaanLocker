package com.shoplocker.fssai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoplocker.fssai.dto.PaymentBreakdown;
import com.shoplocker.fssai.dto.DocumentResponse;
import com.shoplocker.fssai.entity.Document;
import com.shoplocker.fssai.entity.DocumentStatus;
import com.shoplocker.fssai.entity.DocumentType;
import com.shoplocker.fssai.entity.Shop;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class PaymentServiceTest {

    private PaymentService paymentService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                Mockito.mock(com.shoplocker.fssai.repository.PaymentTransactionRepository.class),
                Mockito.mock(com.shoplocker.fssai.repository.DocumentRepository.class),
                Mockito.mock(com.shoplocker.fssai.repository.ShopRepository.class),
                Mockito.mock(com.shoplocker.fssai.repository.UserRepository.class),
                Mockito.mock(com.shoplocker.fssai.repository.RenewalOrderRepository.class),
                Mockito.mock(com.shoplocker.fssai.service.ShopAccessService.class),
                Mockito.mock(com.shoplocker.fssai.service.RenewalOrderService.class),
                Mockito.mock(com.shoplocker.fssai.service.NotificationService.class)
        );
    }

    @Test
    void testCalculateBreakdownAllDurations() {
        // 1 Year
        PaymentBreakdown b1 = paymentService.calculateBreakdown("FSSAI_FOOD_LICENSE", 1, null, 0);
        assertEquals(new BigDecimal("100.00"), b1.getSubtotal());
        assertEquals(new BigDecimal("100.00"), b1.getGovCharge());
        assertEquals(new BigDecimal("500.00"), b1.getServiceCharge());
        assertEquals(new BigDecimal("90.00"), b1.getGstAmount());
        assertEquals(new BigDecimal("0.00"), b1.getPenalty());
        assertEquals(new BigDecimal("690.00"), b1.getTotalAmount());

        // 2 Years
        PaymentBreakdown b2 = paymentService.calculateBreakdown("FSSAI_FOOD_LICENSE", 2, null, 0);
        assertEquals(new BigDecimal("200.00"), b2.getSubtotal());
        assertEquals(new BigDecimal("100.00"), b2.getGovCharge());
        assertEquals(new BigDecimal("700.00"), b2.getServiceCharge());
        assertEquals(new BigDecimal("126.00"), b2.getGstAmount());
        assertEquals(new BigDecimal("1026.00"), b2.getTotalAmount());

        // 3 Years
        PaymentBreakdown b3 = paymentService.calculateBreakdown("FSSAI_FOOD_LICENSE", 3, null, 0);
        assertEquals(new BigDecimal("300.00"), b3.getSubtotal());
        assertEquals(new BigDecimal("100.00"), b3.getGovCharge());
        assertEquals(new BigDecimal("900.00"), b3.getServiceCharge());
        assertEquals(new BigDecimal("162.00"), b3.getGstAmount());
        assertEquals(new BigDecimal("1362.00"), b3.getTotalAmount());

        // 4 Years
        PaymentBreakdown b4 = paymentService.calculateBreakdown("FSSAI_FOOD_LICENSE", 4, null, 0);
        assertEquals(new BigDecimal("400.00"), b4.getSubtotal());
        assertEquals(new BigDecimal("100.00"), b4.getGovCharge());
        assertEquals(new BigDecimal("1200.00"), b4.getServiceCharge());
        assertEquals(new BigDecimal("216.00"), b4.getGstAmount());
        assertEquals(new BigDecimal("1816.00"), b4.getTotalAmount());

        // 5 Years
        PaymentBreakdown b5 = paymentService.calculateBreakdown("FSSAI_FOOD_LICENSE", 5, null, 0);
        assertEquals(new BigDecimal("500.00"), b5.getSubtotal());
        assertEquals(new BigDecimal("100.00"), b5.getGovCharge());
        assertEquals(new BigDecimal("1500.00"), b5.getServiceCharge());
        assertEquals(new BigDecimal("270.00"), b5.getGstAmount());
        assertEquals(new BigDecimal("2270.00"), b5.getTotalAmount());
    }

    @Test
    void testDocumentResponseHoldsKob() {
        DocumentResponse doc = new DocumentResponse(
                1L, 10L, DocumentType.FSSAI_FOOD_LICENSE,
                "fssai.pdf", "https://example.com/fssai.pdf", "10121028000789",
                "Trade/Retail - Retailer",
                LocalDateTime.now(), LocalDateTime.now().plusYears(1),
                DocumentStatus.UPLOADED, 1,
                LocalDateTime.now(), LocalDateTime.now()
        );

        assertEquals("Trade/Retail - Retailer", doc.getKindOfBusiness());
        assertEquals("10121028000789", doc.getDocumentNumber());
    }

    @Test
    void testKobJsonParsingFromDetails() throws Exception {
        String json = """
                {
                  "license": {
                    "LicenseNo": "10121028000789",
                    "fboId": 66108188,
                    "expiryDate": "03-09-2026"
                  },
                  "details": {
                    "contactPerson": "B SRIRAMULU",
                    "companyName": "APOLLO PHARMACY",
                    "kobname": "Trade/Retail - Retailer"
                  }
                }
                """;

        JsonNode root = objectMapper.readTree(json);
        String kob = null;
        JsonNode details = root.path("details");
        if (details.has("kobname") && !details.path("kobname").isNull()) {
            kob = details.path("kobname").asText().trim();
        }
        assertEquals("Trade/Retail - Retailer", kob);
    }
}
