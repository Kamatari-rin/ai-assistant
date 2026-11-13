package com.example.aiassistant.orders.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class OrderTest {

    private static final UUID ORDER_ID =
            UUID.fromString("09b98c5b-37a5-4fa3-97c7-a016d7517760");

    private static final Instant CREATED_AT =
            Instant.parse("2026-11-10T09:00:00Z");

    private static final Instant UPDATED_AT =
            Instant.parse("2026-11-11T10:30:00Z");

    @Test
    void createsOrderWithExpectedValues() {

        Order order = new Order(
                ORDER_ID,
                "12345",
                OrderStatus.DELAYED,
                DelayReason.OUT_OF_STOCK,
                CREATED_AT,
                UPDATED_AT
        );

        assertThat(order.id()).isEqualTo(ORDER_ID);
        assertThat(order.displayNumber()).isEqualTo("12345");
        assertThat(order.status()).isEqualTo(OrderStatus.DELAYED);
        assertThat(order.delayReason()).isEqualTo(DelayReason.OUT_OF_STOCK);
        assertThat(order.createdAt()).isEqualTo(CREATED_AT);
        assertThat(order.updatedAt()).isEqualTo(UPDATED_AT);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "12345",
            "0012345",
            "ORDER-A",
            " 12345 ",
            " "
    })
    void preservesDisplayNumberExactly(String displayNumber) {

        Order order = new Order(
                ORDER_ID,
                displayNumber,
                OrderStatus.DELAYED,
                DelayReason.OUT_OF_STOCK,
                CREATED_AT,
                UPDATED_AT
        );

        assertThat(order.displayNumber()).isEqualTo(displayNumber);
    }

    @Test
    void rejectsEmptyDisplayNumber() {

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Order(
                        ORDER_ID,
                        "",
                        OrderStatus.DELAYED,
                        DelayReason.OUT_OF_STOCK,
                        CREATED_AT,
                        UPDATED_AT
                ))
                .withMessage("displayNumber must not be empty");
    }

    @Test
    void rejectsNullComponents() {

        assertThatNullPointerException()
                .isThrownBy(() -> new Order(
                        null,
                        "12345",
                        OrderStatus.DELAYED,
                        DelayReason.OUT_OF_STOCK,
                        CREATED_AT,
                        UPDATED_AT
                ))
                .withMessage("id must not be null");

        assertThatNullPointerException()
                .isThrownBy(() -> new Order(
                        ORDER_ID,
                        null,
                        OrderStatus.DELAYED,
                        DelayReason.OUT_OF_STOCK,
                        CREATED_AT,
                        UPDATED_AT
                ))
                .withMessage("displayNumber must not be null");

        assertThatNullPointerException()
                .isThrownBy(() -> new Order(
                        ORDER_ID,
                        "12345",
                        null,
                        DelayReason.OUT_OF_STOCK,
                        CREATED_AT,
                        UPDATED_AT
                ))
                .withMessage("status must not be null");

        assertThatNullPointerException()
                .isThrownBy(() -> new Order(
                        ORDER_ID,
                        "12345",
                        OrderStatus.DELAYED,
                        null,
                        CREATED_AT,
                        UPDATED_AT
                ))
                .withMessage("delayReason must not be null");

        assertThatNullPointerException()
                .isThrownBy(() -> new Order(
                        ORDER_ID,
                        "12345",
                        OrderStatus.DELAYED,
                        DelayReason.OUT_OF_STOCK,
                        null,
                        UPDATED_AT
                ))
                .withMessage("createdAt must not be null");

        assertThatNullPointerException()
                .isThrownBy(() -> new Order(
                        ORDER_ID,
                        "12345",
                        OrderStatus.DELAYED,
                        DelayReason.OUT_OF_STOCK,
                        CREATED_AT,
                        null
                ))
                .withMessage("updatedAt must not be null");
    }

    @Test
    void rejectsUpdateBeforeCreation() {

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Order(
                        ORDER_ID,
                        "12345",
                        OrderStatus.DELAYED,
                        DelayReason.OUT_OF_STOCK,
                        CREATED_AT,
                        CREATED_AT.minusSeconds(1)
                ))
                .withMessage(
                        "updatedAt must not be before createdAt"
                );
    }

    @Test
    void allowsEqualCreationAndUpdateTimestamps() {

        Order order = new Order(
                ORDER_ID,
                "12345",
                OrderStatus.DELAYED,
                DelayReason.OUT_OF_STOCK,
                CREATED_AT,
                CREATED_AT
        );

        assertThat(order.updatedAt()).isEqualTo(order.createdAt());
    }
}
