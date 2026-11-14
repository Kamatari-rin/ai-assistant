package com.example.aiassistant.orders.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OrderEvent(
        UUID id,
        UUID orderId,
        String type,
        String description,
        Instant createdAt
) {

    public OrderEvent {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(orderId, "orderId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
