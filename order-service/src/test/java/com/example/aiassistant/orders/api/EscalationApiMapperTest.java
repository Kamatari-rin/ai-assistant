package com.example.aiassistant.orders.api;

import com.example.aiassistant.orders.api.dto.CreateEscalationRequest;
import com.example.aiassistant.orders.api.dto.EscalationPriorityRequest;
import com.example.aiassistant.orders.api.dto.EscalationReasonRequest;
import com.example.aiassistant.orders.api.dto.EscalationResponse;
import com.example.aiassistant.orders.api.dto.EscalationStatusResponse;
import com.example.aiassistant.orders.application.CreateEscalationCommand;
import com.example.aiassistant.orders.domain.Escalation;
import com.example.aiassistant.orders.domain.EscalationIdentity;
import com.example.aiassistant.orders.domain.EscalationPriority;
import com.example.aiassistant.orders.domain.EscalationReason;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EscalationApiMapperTest {

    private final EscalationApiMapper mapper =
            new EscalationApiMapper();

    @Test
    void mapsValidatedRequestToApplicationCommand() {

        UUID key = UUID.randomUUID();

        CreateEscalationCommand command =
                mapper.toCommand(
                        " 0012345 ",
                        key,
                        new CreateEscalationRequest(
                                EscalationReasonRequest.OUT_OF_STOCK,
                                EscalationPriorityRequest.HIGH
                        )
                );

        assertThat(command.idempotencyKey())
                .isEqualTo(key);

        assertThat(command.identity())
                .isEqualTo(
                        new EscalationIdentity(
                                " 0012345 ",
                                EscalationReason.OUT_OF_STOCK,
                                EscalationPriority.HIGH
                        )
                );
    }

    @Test
    void mapsPersistedEscalationToContractResponse() {

        UUID escalationId = UUID.randomUUID();

        Escalation escalation = new Escalation(
                escalationId,
                UUID.randomUUID(),
                new EscalationIdentity(
                        "0012345",
                        EscalationReason.OUT_OF_STOCK,
                        EscalationPriority.HIGH
                ),
                UUID.randomUUID(),
                Instant.parse("2026-11-14T07:00:00Z")
        );

        EscalationResponse response =
                mapper.toResponse(escalation);

        assertThat(response)
                .isEqualTo(
                        new EscalationResponse(
                                escalationId,
                                "0012345",
                                EscalationStatusResponse.CREATED
                        )
                );
    }
}