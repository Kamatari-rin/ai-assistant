package com.example.aiassistant.orders.api.dto;

import java.util.UUID;

public record EscalationResponse(
        UUID escalationId,
        String orderNumber,
        EscalationStatusResponse status
) {
}