package com.example.aiassistant.orders.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Order(
        UUID id,
        String displayNumber,
        OrderStatus status,
        DelayReason delayReason,
        Instant createdAt,
        Instant updatedAt
) {

    public Order {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(displayNumber, "displayNumber must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(delayReason, "delayReason must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");

        if (displayNumber.isEmpty()) {
            throw new IllegalArgumentException(
                    "displayNumber must not be empty"
            );
        }

        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException(
                    "updatedAt must not be before createdAt"
            );
        }
    }
}
