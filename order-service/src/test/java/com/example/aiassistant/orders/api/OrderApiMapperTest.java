package com.example.aiassistant.orders.api;

import com.example.aiassistant.orders.api.dto.DelayReasonResponse;
import com.example.aiassistant.orders.api.dto.OrderEventResponse;
import com.example.aiassistant.orders.api.dto.OrderEventsResponse;
import com.example.aiassistant.orders.api.dto.OrderResponse;
import com.example.aiassistant.orders.api.dto.OrderStatusResponse;
import com.example.aiassistant.orders.domain.DelayReason;
import com.example.aiassistant.orders.domain.Order;
import com.example.aiassistant.orders.domain.OrderEvent;
import com.example.aiassistant.orders.domain.OrderStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderApiMapperTest {

    private static final UUID ORDER_ID =
            UUID.fromString(
                    "09b98c5b-37a5-4fa3-97c7-a016d7517760"
            );

    private static final Instant CREATED_AT =
            Instant.parse("2026-11-10T09:00:00Z");

    private static final Instant UPDATED_AT =
            Instant.parse("2026-11-11T10:30:00Z");

    private static final OrderEvent CREATED_EVENT =
            new OrderEvent(
                    UUID.fromString(
                            "a1000000-0000-4000-8000-000000000001"
                    ),
                    ORDER_ID,
                    "ORDER_CREATED",
                    "Synthetic order was created.",
                    CREATED_AT
            );

    private static final OrderEvent STOCK_UNAVAILABLE_EVENT =
            new OrderEvent(
                    UUID.fromString(
                            "a1000000-0000-4000-8000-000000000003"
                    ),
                    ORDER_ID,
                    "STOCK_UNAVAILABLE",
                    "Required item is temporarily out of stock.",
                    UPDATED_AT
            );

    private final OrderApiMapper mapper =
            new OrderApiMapper();

    @Test
    void mapsOrderToContractResponse() {

        Order order = new Order(
                ORDER_ID,
                "12345",
                OrderStatus.DELAYED,
                DelayReason.OUT_OF_STOCK,
                CREATED_AT,
                UPDATED_AT
        );

        OrderResponse expected = new OrderResponse(
                ORDER_ID,
                "12345",
                OrderStatusResponse.DELAYED,
                DelayReasonResponse.OUT_OF_STOCK,
                UPDATED_AT
        );

        assertThat(mapper.toOrderResponse(order))
                .isEqualTo(expected);
    }

    @Test
    void mapsEventsWithoutChangingTheirOrder() {

        OrderEventsResponse response =
                mapper.toOrderEventsResponse(
                        List.of(
                                CREATED_EVENT,
                                STOCK_UNAVAILABLE_EVENT
                        )
                );

        assertThat(response.items())
                .containsExactly(
                        new OrderEventResponse(
                                "ORDER_CREATED",
                                "Synthetic order was created.",
                                CREATED_AT
                        ),
                        new OrderEventResponse(
                                "STOCK_UNAVAILABLE",
                                "Required item is temporarily out of stock.",
                                UPDATED_AT
                        )
                );
    }

    @Test
    void mapsEmptyEventListToEmptyItems() {

        OrderEventsResponse response =
                mapper.toOrderEventsResponse(List.of());

        assertThat(response.items()).isEmpty();
    }

    @Test
    void preservesExactDisplayNumber() {

        Order order = new Order(
                ORDER_ID,
                " 0012345 ",
                OrderStatus.DELAYED,
                DelayReason.OUT_OF_STOCK,
                CREATED_AT,
                UPDATED_AT
        );

        assertThat(mapper.toOrderResponse(order).displayNumber())
                .isEqualTo(" 0012345 ");
    }

    @Test
    void eventsResponseDefensivelyCopiesItems() {

        OrderEventResponse event = new OrderEventResponse(
                "ORDER_CREATED",
                "Synthetic order was created.",
                CREATED_AT
        );

        List<OrderEventResponse> source =
                new ArrayList<>();

        source.add(event);

        OrderEventsResponse response =
                new OrderEventsResponse(source);

        source.clear();

        assertThat(response.items())
                .containsExactly(event);

        assertThatThrownBy(() ->
                response.items().clear()
        )
                .isInstanceOf(
                        UnsupportedOperationException.class
                );
    }
}
