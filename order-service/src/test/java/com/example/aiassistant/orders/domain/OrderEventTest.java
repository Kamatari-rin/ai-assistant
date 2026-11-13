package com.example.aiassistant.orders.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class OrderEventTest {

    private static final UUID EVENT_ID =
            UUID.fromString("a1000000-0000-4000-8000-000000000003");

    private static final UUID ORDER_ID =
            UUID.fromString("09b98c5b-37a5-4fa3-97c7-a016d7517760");

    private static final Instant CREATED_AT =
            Instant.parse("2026-11-11T10:30:00Z");

    @Test
    void createsEventWithExpectedValues() {

        OrderEvent event = new OrderEvent(
                EVENT_ID,
                ORDER_ID,
                "STOCK_UNAVAILABLE",
                "Required item is temporarily out of stock.",
                CREATED_AT
        );

        assertThat(event.id()).isEqualTo(EVENT_ID);
        assertThat(event.orderId()).isEqualTo(ORDER_ID);
        assertThat(event.type()).isEqualTo("STOCK_UNAVAILABLE");
        assertThat(event.description())
                .isEqualTo("Required item is temporarily out of stock.");
        assertThat(event.createdAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void allowsEventTypeNotPresentInDemoFixtures() {

        OrderEvent event = new OrderEvent(
                EVENT_ID,
                ORDER_ID,
                "CUSTOM_EVENT",
                "Synthetic event.",
                CREATED_AT
        );

        assertThat(event.type()).isEqualTo("CUSTOM_EVENT");
    }

    @Test
    void preservesEmptyEventStringsAllowedByCurrentSchema() {

        OrderEvent event = new OrderEvent(
                EVENT_ID,
                ORDER_ID,
                "",
                "",
                CREATED_AT
        );

        assertThat(event.type()).isEmpty();
        assertThat(event.description()).isEmpty();
    }

    @Test
    void rejectsNullComponents() {

        assertThatNullPointerException()
                .isThrownBy(() -> new OrderEvent(
                        null,
                        ORDER_ID,
                        "STOCK_UNAVAILABLE",
                        "Synthetic event.",
                        CREATED_AT
                ))
                .withMessage("id must not be null");

        assertThatNullPointerException()
                .isThrownBy(() -> new OrderEvent(
                        EVENT_ID,
                        null,
                        "STOCK_UNAVAILABLE",
                        "Synthetic event.",
                        CREATED_AT
                ))
                .withMessage("orderId must not be null");

        assertThatNullPointerException()
                .isThrownBy(() -> new OrderEvent(
                        EVENT_ID,
                        ORDER_ID,
                        null,
                        "Synthetic event.",
                        CREATED_AT
                ))
                .withMessage("type must not be null");

        assertThatNullPointerException()
                .isThrownBy(() -> new OrderEvent(
                        EVENT_ID,
                        ORDER_ID,
                        "STOCK_UNAVAILABLE",
                        null,
                        CREATED_AT
                ))
                .withMessage("description must not be null");

        assertThatNullPointerException()
                .isThrownBy(() -> new OrderEvent(
                        EVENT_ID,
                        ORDER_ID,
                        "STOCK_UNAVAILABLE",
                        "Synthetic event.",
                        null
                ))
                .withMessage("createdAt must not be null");
    }
}
