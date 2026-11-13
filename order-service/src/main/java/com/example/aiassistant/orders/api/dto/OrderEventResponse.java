package com.example.aiassistant.orders.api.dto;

import java.time.Instant;

public record OrderEventResponse(
        String type,
        String description,
        Instant createdAt
) {
}
