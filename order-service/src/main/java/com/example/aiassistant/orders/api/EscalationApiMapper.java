package com.example.aiassistant.orders.api;

import com.example.aiassistant.orders.api.dto.CreateEscalationRequest;
import com.example.aiassistant.orders.api.dto.EscalationResponse;
import com.example.aiassistant.orders.api.dto.EscalationStatusResponse;
import com.example.aiassistant.orders.application.CreateEscalationCommand;
import com.example.aiassistant.orders.domain.Escalation;
import com.example.aiassistant.orders.domain.EscalationIdentity;
import com.example.aiassistant.orders.domain.EscalationPriority;
import com.example.aiassistant.orders.domain.EscalationReason;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class EscalationApiMapper {

    public CreateEscalationCommand toCommand(
            String displayNumber,
            UUID idempotencyKey,
            CreateEscalationRequest request
    ) {

        EscalationReason reason = switch (request.reason()) {
            case OUT_OF_STOCK ->
                    EscalationReason.OUT_OF_STOCK;
        };

        EscalationPriority priority = switch (request.priority()) {
            case HIGH ->
                    EscalationPriority.HIGH;
        };

        return new CreateEscalationCommand(
                idempotencyKey,
                new EscalationIdentity(
                        displayNumber,
                        reason,
                        priority
                )
        );
    }

    public EscalationResponse toResponse(
            Escalation escalation
    ) {

        return new EscalationResponse(
                escalation.id(),
                escalation.identity().displayNumber(),
                EscalationStatusResponse.CREATED
        );
    }
}