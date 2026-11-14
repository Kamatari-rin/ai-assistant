package com.example.aiassistant.orders.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Escalation(
        UUID id,
        UUID orderId,
        EscalationIdentity identity,
        UUID idempotencyKey,
        Instant createdAt
) {

    public Escalation {
        Objects.requireNonNull(
                id,
                "id must not be null"
        );

        Objects.requireNonNull(
                orderId,
                "orderId must not be null"
        );

        Objects.requireNonNull(
                identity,
                "identity must not be null"
        );

        Objects.requireNonNull(
                idempotencyKey,
                "idempotencyKey must not be null"
        );

        Objects.requireNonNull(
                createdAt,
                "createdAt must not be null"
        );
    }
}