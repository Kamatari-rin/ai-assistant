package com.example.aiassistant.orders.api.dto;

import java.time.Instant;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String displayNumber,
        OrderStatusResponse status,
        DelayReasonResponse delayReason,
        Instant updatedAt
) {
}
