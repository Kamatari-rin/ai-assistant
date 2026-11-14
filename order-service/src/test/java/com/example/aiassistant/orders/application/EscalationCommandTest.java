package com.example.aiassistant.orders.application;

import com.example.aiassistant.orders.domain.Escalation;
import com.example.aiassistant.orders.domain.EscalationIdentity;
import com.example.aiassistant.orders.domain.EscalationPriority;
import com.example.aiassistant.orders.domain.EscalationReason;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class EscalationCommandTest {

    private static final UUID IDEMPOTENCY_KEY =
            UUID.fromString(
                    "f90fd699-154c-4ddb-9b9a-316ea31b2b07"
            );

    private static final EscalationIdentity IDENTITY =
            new EscalationIdentity(
                    "12345",
                    EscalationReason.OUT_OF_STOCK,
                    EscalationPriority.HIGH
            );

    private static final Escalation ESCALATION =
            new Escalation(
                    UUID.fromString(
                            "5a3bb692-d257-491c-9212-d49ea1518ed1"
                    ),
                    UUID.fromString(
                            "09b98c5b-37a5-4fa3-97c7-a016d7517760"
                    ),
                    IDENTITY,
                    IDEMPOTENCY_KEY,
                    Instant.parse("2026-11-11T11:00:00Z")
            );

    @Test
    void commandContainsKeyAndLogicalIdentity() {

        CreateEscalationCommand command =
                new CreateEscalationCommand(
                        IDEMPOTENCY_KEY,
                        IDENTITY
                );

        assertThat(command.idempotencyKey())
                .isEqualTo(IDEMPOTENCY_KEY);

        assertThat(command.identity())
                .isEqualTo(IDENTITY);
    }

    @Test
    void rejectsNullCommandComponents() {

        assertThatNullPointerException()
                .isThrownBy(() ->
                        new CreateEscalationCommand(
                                null,
                                IDENTITY
                        )
                )
                .withMessage(
                        "idempotencyKey must not be null"
                );

        assertThatNullPointerException()
                .isThrownBy(() ->
                        new CreateEscalationCommand(
                                IDEMPOTENCY_KEY,
                                null
                        )
                )
                .withMessage("identity must not be null");
    }

    @Test
    void newlyCreatedResultContainsPersistedEscalation() {

        CreateEscalationResult result =
                CreateEscalationResult.newlyCreated(
                        ESCALATION
                );

        assertThat(result.created()).isTrue();

        assertThat(result.escalation())
                .isSameAs(ESCALATION);
    }

    @Test
    void replayedResultContainsSamePersistedEscalation() {

        CreateEscalationResult result =
                CreateEscalationResult.replayed(
                        ESCALATION
                );

        assertThat(result.created()).isFalse();

        assertThat(result.escalation())
                .isSameAs(ESCALATION);
    }

    @Test
    void rejectsResultWithoutEscalation() {

        assertThatNullPointerException()
                .isThrownBy(() ->
                        CreateEscalationResult.newlyCreated(
                                null
                        )
                )
                .withMessage(
                        "escalation must not be null"
                );

        assertThatNullPointerException()
                .isThrownBy(() ->
                        CreateEscalationResult.replayed(
                                null
                        )
                )
                .withMessage(
                        "escalation must not be null"
                );
    }
}