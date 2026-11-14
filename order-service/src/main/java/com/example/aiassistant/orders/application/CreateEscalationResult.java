package com.example.aiassistant.orders.application;

import com.example.aiassistant.orders.domain.Escalation;

import java.util.Objects;

public record CreateEscalationResult(
        Escalation escalation,
        boolean created
) {

    public CreateEscalationResult {
        Objects.requireNonNull(
                escalation,
                "escalation must not be null"
        );
    }

    public static CreateEscalationResult newlyCreated(
            Escalation escalation
    ) {

        return new CreateEscalationResult(
                escalation,
                true
        );
    }

    public static CreateEscalationResult replayed(
            Escalation escalation
    ) {

        return new CreateEscalationResult(
                escalation,
                false
        );
    }
}