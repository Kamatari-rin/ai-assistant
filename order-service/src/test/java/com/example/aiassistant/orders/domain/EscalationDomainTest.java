package com.example.aiassistant.orders.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class EscalationDomainTest {

    private static final UUID ORDER_ID =
            UUID.fromString(
                    "09b98c5b-37a5-4fa3-97c7-a016d7517760"
            );

    private static final UUID ESCALATION_ID =
            UUID.fromString(
                    "5a3bb692-d257-491c-9212-d49ea1518ed1"
            );

    private static final UUID IDEMPOTENCY_KEY =
            UUID.fromString(
                    "f90fd699-154c-4ddb-9b9a-316ea31b2b07"
            );

    private static final Instant CREATED_AT =
            Instant.parse("2026-11-11T11:00:00Z");

    private static final EscalationIdentity IDENTITY =
            new EscalationIdentity(
                    "12345",
                    EscalationReason.OUT_OF_STOCK,
                    EscalationPriority.HIGH
            );

    @Test
    void equalIdentitiesRepresentSameLogicalRequest() {

        EscalationIdentity duplicate =
                new EscalationIdentity(
                        "12345",
                        EscalationReason.OUT_OF_STOCK,
                        EscalationPriority.HIGH
                );

        assertThat(duplicate).isEqualTo(IDENTITY);

        assertThat(duplicate.hashCode())
                .isEqualTo(IDENTITY.hashCode());
    }

    @Test
    void differentDisplayNumbersRepresentDifferentRequests() {

        EscalationIdentity other =
                new EscalationIdentity(
                        "12346",
                        EscalationReason.OUT_OF_STOCK,
                        EscalationPriority.HIGH
                );

        assertThat(other).isNotEqualTo(IDENTITY);
    }

    @Test
    void preservesDisplayNumberWithoutNormalization() {

        EscalationIdentity leadingZeros =
                new EscalationIdentity(
                        "0012345",
                        EscalationReason.OUT_OF_STOCK,
                        EscalationPriority.HIGH
                );

        EscalationIdentity whitespace =
                new EscalationIdentity(
                        " 12345 ",
                        EscalationReason.OUT_OF_STOCK,
                        EscalationPriority.HIGH
                );

        EscalationIdentity blank =
                new EscalationIdentity(
                        " ",
                        EscalationReason.OUT_OF_STOCK,
                        EscalationPriority.HIGH
                );

        assertThat(leadingZeros.displayNumber())
                .isEqualTo("0012345");

        assertThat(whitespace.displayNumber())
                .isEqualTo(" 12345 ");

        assertThat(blank.displayNumber())
                .isEqualTo(" ");

        assertThat(leadingZeros).isNotEqualTo(IDENTITY);
        assertThat(whitespace).isNotEqualTo(IDENTITY);
        assertThat(blank).isNotEqualTo(IDENTITY);
    }

    @Test
    void rejectsEmptyDisplayNumber() {

        assertThatIllegalArgumentException()
                .isThrownBy(() ->
                        new EscalationIdentity(
                                "",
                                EscalationReason.OUT_OF_STOCK,
                                EscalationPriority.HIGH
                        )
                )
                .withMessage(
                        "displayNumber must not be empty"
                );
    }

    @Test
    void rejectsNullIdentityComponents() {

        assertThatNullPointerException()
                .isThrownBy(() ->
                        new EscalationIdentity(
                                null,
                                EscalationReason.OUT_OF_STOCK,
                                EscalationPriority.HIGH
                        )
                )
                .withMessage(
                        "displayNumber must not be null"
                );

        assertThatNullPointerException()
                .isThrownBy(() ->
                        new EscalationIdentity(
                                "12345",
                                null,
                                EscalationPriority.HIGH
                        )
                )
                .withMessage("reason must not be null");

        assertThatNullPointerException()
                .isThrownBy(() ->
                        new EscalationIdentity(
                                "12345",
                                EscalationReason.OUT_OF_STOCK,
                                null
                        )
                )
                .withMessage("priority must not be null");
    }

    @Test
    void createsEscalationWithPersistedIdentity() {

        Escalation escalation = new Escalation(
                ESCALATION_ID,
                ORDER_ID,
                IDENTITY,
                IDEMPOTENCY_KEY,
                CREATED_AT
        );

        assertThat(escalation.id())
                .isEqualTo(ESCALATION_ID);

        assertThat(escalation.orderId())
                .isEqualTo(ORDER_ID);

        assertThat(escalation.identity())
                .isEqualTo(IDENTITY);

        assertThat(escalation.idempotencyKey())
                .isEqualTo(IDEMPOTENCY_KEY);

        assertThat(escalation.createdAt())
                .isEqualTo(CREATED_AT);
    }

    @Test
    void rejectsNullEscalationComponents() {

        assertThatNullPointerException()
                .isThrownBy(() -> new Escalation(
                        null,
                        ORDER_ID,
                        IDENTITY,
                        IDEMPOTENCY_KEY,
                        CREATED_AT
                ))
                .withMessage("id must not be null");

        assertThatNullPointerException()
                .isThrownBy(() -> new Escalation(
                        ESCALATION_ID,
                        null,
                        IDENTITY,
                        IDEMPOTENCY_KEY,
                        CREATED_AT
                ))
                .withMessage("orderId must not be null");

        assertThatNullPointerException()
                .isThrownBy(() -> new Escalation(
                        ESCALATION_ID,
                        ORDER_ID,
                        null,
                        IDEMPOTENCY_KEY,
                        CREATED_AT
                ))
                .withMessage("identity must not be null");

        assertThatNullPointerException()
                .isThrownBy(() -> new Escalation(
                        ESCALATION_ID,
                        ORDER_ID,
                        IDENTITY,
                        null,
                        CREATED_AT
                ))
                .withMessage(
                        "idempotencyKey must not be null"
                );

        assertThatNullPointerException()
                .isThrownBy(() -> new Escalation(
                        ESCALATION_ID,
                        ORDER_ID,
                        IDENTITY,
                        IDEMPOTENCY_KEY,
                        null
                ))
                .withMessage("createdAt must not be null");
    }

    @Test
    void acceptsEligibleDelayedOutOfStockOrder() {

        Order order = new Order(
                ORDER_ID,
                "12345",
                OrderStatus.DELAYED,
                DelayReason.OUT_OF_STOCK,
                Instant.parse("2026-11-10T09:00:00Z"),
                Instant.parse("2026-11-11T10:30:00Z")
        );

        EscalationEligibilityPolicy policy =
                new EscalationEligibilityPolicy();

        assertThat(policy.isEligible(order, IDENTITY))
                .isTrue();
    }
}