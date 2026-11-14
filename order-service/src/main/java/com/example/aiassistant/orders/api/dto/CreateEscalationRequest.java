package com.example.aiassistant.orders.api.dto;

public record CreateEscalationRequest(
        EscalationReasonRequest reason,
        EscalationPriorityRequest priority
) {
}