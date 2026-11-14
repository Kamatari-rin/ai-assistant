package com.example.aiassistant.orders.application;

import com.example.aiassistant.orders.domain.EscalationIdentity;

import java.util.Objects;
import java.util.UUID;

public record CreateEscalationCommand(
        UUID idempotencyKey,
        EscalationIdentity identity
) {

    public CreateEscalationCommand {
        Objects.requireNonNull(
                idempotencyKey,
                "idempotencyKey must not be null"
        );

        Objects.requireNonNull(
                identity,
                "identity must not be null"
        );
    }
}