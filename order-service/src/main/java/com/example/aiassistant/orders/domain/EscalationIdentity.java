package com.example.aiassistant.orders.domain;

import java.util.Objects;

public record EscalationIdentity(
        String displayNumber,
        EscalationReason reason,
        EscalationPriority priority
) {

    public EscalationIdentity {
        Objects.requireNonNull(
                displayNumber,
                "displayNumber must not be null"
        );

        Objects.requireNonNull(
                reason,
                "reason must not be null"
        );

        Objects.requireNonNull(
                priority,
                "priority must not be null"
        );

        if (displayNumber.isEmpty()) {
            throw new IllegalArgumentException(
                    "displayNumber must not be empty"
            );
        }
    }
}