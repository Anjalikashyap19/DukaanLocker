package com.shoplocker.fssai.dto;

import java.time.Instant;

public record RenewalOrderResponse(
        Long id,
        Long shopId,
        String shopName,
        String documentType,
        String status,
        String dlId,
        String notes,
        Instant requestedAt,
        Instant completedAt) {}
