package com.example.aiassistant.orders.application;

import com.example.aiassistant.orders.application.exception.OrderNotFoundException;
import com.example.aiassistant.orders.application.port.OrderRepository;
import com.example.aiassistant.orders.domain.DelayReason;
import com.example.aiassistant.orders.domain.Order;
import com.example.aiassistant.orders.domain.OrderEvent;
import com.example.aiassistant.orders.domain.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class OrderQueryServiceTest {

    private static final UUID MAIN_ORDER_ID =
            UUID.fromString(
                    "09b98c5b-37a5-4fa3-97c7-a016d7517760"
            );

    private static final UUID EMPTY_HISTORY_ORDER_ID =
            UUID.fromString(
                    "09b98c5b-37a5-4fa3-97c7-a016d7517762"
            );

    private static final Order MAIN_ORDER = new Order(
            MAIN_ORDER_ID,
            "12345",
            OrderStatus.DELAYED,
            DelayReason.OUT_OF_STOCK,
            Instant.parse("2026-11-10T09:00:00Z"),
            Instant.parse("2026-11-11T10:30:00Z")
    );

    private static final Order EMPTY_HISTORY_ORDER = new Order(
            EMPTY_HISTORY_ORDER_ID,
            "12347",
            OrderStatus.DELAYED,
            DelayReason.OUT_OF_STOCK,
            Instant.parse("2026-11-10T13:00:00Z"),
            Instant.parse("2026-11-11T14:00:00Z")
    );

    private static final OrderEvent CREATED_EVENT =
            new OrderEvent(
                    UUID.fromString(
                            "a1000000-0000-4000-8000-000000000001"
                    ),
                    MAIN_ORDER_ID,
                    "ORDER_CREATED",
                    "Synthetic order was created.",
                    Instant.parse("2026-11-10T09:00:00Z")
            );

    private static final OrderEvent PROCESSING_EVENT =
            new OrderEvent(
                    UUID.fromString(
                            "a1000000-0000-4000-8000-000000000002"
                    ),
                    MAIN_ORDER_ID,
                    "ORDER_PROCESSING",
                    "Order processing started.",
                    Instant.parse("2026-11-10T09:15:00Z")
            );

    private static final OrderEvent STOCK_UNAVAILABLE_EVENT =
            new OrderEvent(
                    UUID.fromString(
                            "a1000000-0000-4000-8000-000000000003"
                    ),
                    MAIN_ORDER_ID,
                    "STOCK_UNAVAILABLE",
                    "Required item is temporarily out of stock.",
                    Instant.parse("2026-11-11T10:30:00Z")
            );

    private OrderRepository orderRepository;

    private OrderQueryService orderQueryService;

    @BeforeEach
    void setUp() {

        orderRepository = mock(OrderRepository.class);

        orderQueryService = new OrderQueryService(
                orderRepository
        );
    }

    @Test
    void returnsExistingOrder() {

        when(orderRepository.findByDisplayNumber("12345"))
                .thenReturn(Optional.of(MAIN_ORDER));

        Order result = orderQueryService.getOrder("12345");

        assertThat(result).isEqualTo(MAIN_ORDER);

        verify(orderRepository)
                .findByDisplayNumber("12345");

        verifyNoMoreInteractions(orderRepository);
    }

    @Test
    void throwsOrderNotFoundForMissingOrder() {

        when(orderRepository.findByDisplayNumber("99999"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                orderQueryService.getOrder("99999")
        )
                .isInstanceOfSatisfying(
                        OrderNotFoundException.class,
                        exception -> {
                            assertThat(exception.getDisplayNumber())
                                    .isEqualTo("99999");

                            assertThat(exception.getMessage())
                                    .isEqualTo("Order was not found.");
                        }
                );

        verify(orderRepository)
                .findByDisplayNumber("99999");

        verifyNoMoreInteractions(orderRepository);
    }

    @Test
    void returnsEventsForExistingOrderInRepositoryOrder() {

        List<OrderEvent> expectedEvents = List.of(
                CREATED_EVENT,
                PROCESSING_EVENT,
                STOCK_UNAVAILABLE_EVENT
        );

        when(orderRepository.findByDisplayNumber("12345"))
                .thenReturn(Optional.of(MAIN_ORDER));

        when(orderRepository.findEventsByOrderId(MAIN_ORDER_ID))
                .thenReturn(expectedEvents);

        List<OrderEvent> result =
                orderQueryService.getOrderEvents("12345");

        assertThat(result)
                .containsExactlyElementsOf(expectedEvents);

        var repositoryCalls = inOrder(orderRepository);

        repositoryCalls.verify(orderRepository)
                .findByDisplayNumber("12345");

        repositoryCalls.verify(orderRepository)
                .findEventsByOrderId(MAIN_ORDER_ID);

        verifyNoMoreInteractions(orderRepository);
    }

    @Test
    void returnsEmptyEventsForExistingOrderWithoutHistory() {

        when(orderRepository.findByDisplayNumber("12347"))
                .thenReturn(Optional.of(EMPTY_HISTORY_ORDER));

        when(orderRepository.findEventsByOrderId(
                EMPTY_HISTORY_ORDER_ID
        ))
                .thenReturn(List.of());

        List<OrderEvent> result =
                orderQueryService.getOrderEvents("12347");

        assertThat(result).isEmpty();

        verify(orderRepository)
                .findByDisplayNumber("12347");

        verify(orderRepository)
                .findEventsByOrderId(EMPTY_HISTORY_ORDER_ID);

        verifyNoMoreInteractions(orderRepository);
    }

    @Test
    void doesNotQueryEventsWhenOrderDoesNotExist() {

        when(orderRepository.findByDisplayNumber("99999"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                orderQueryService.getOrderEvents("99999")
        )
                .isInstanceOfSatisfying(
                        OrderNotFoundException.class,
                        exception ->
                                assertThat(exception.getDisplayNumber())
                                        .isEqualTo("99999")
                );

        verify(orderRepository)
                .findByDisplayNumber("99999");

        verify(orderRepository, never())
                .findEventsByOrderId(any(UUID.class));

        verifyNoMoreInteractions(orderRepository);
    }

    @Test
    void preservesExactDisplayNumberWhenQueryingOrder() {

        String displayNumber = " 0012345 ";

        when(orderRepository.findByDisplayNumber(displayNumber))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                orderQueryService.getOrder(displayNumber)
        )
                .isInstanceOfSatisfying(
                        OrderNotFoundException.class,
                        exception ->
                                assertThat(exception.getDisplayNumber())
                                        .isEqualTo(displayNumber)
                );

        verify(orderRepository)
                .findByDisplayNumber(displayNumber);

        verifyNoMoreInteractions(orderRepository);
    }

    @Test
    void propagatesRepositoryFailureWhenFindingOrder() {

        var failure = new DataAccessResourceFailureException(
                "Synthetic database failure."
        );

        when(orderRepository.findByDisplayNumber("12345"))
                .thenThrow(failure);

        assertThatThrownBy(() ->
                orderQueryService.getOrder("12345")
        )
                .isSameAs(failure);

        verify(orderRepository)
                .findByDisplayNumber("12345");

        verifyNoMoreInteractions(orderRepository);
    }

    @Test
    void propagatesRepositoryFailureWhenReadingEvents() {

        var failure = new DataAccessResourceFailureException(
                "Synthetic database failure."
        );

        when(orderRepository.findByDisplayNumber("12345"))
                .thenReturn(Optional.of(MAIN_ORDER));

        when(orderRepository.findEventsByOrderId(MAIN_ORDER_ID))
                .thenThrow(failure);

        assertThatThrownBy(() ->
                orderQueryService.getOrderEvents("12345")
        )
                .isSameAs(failure);

        verify(orderRepository)
                .findByDisplayNumber("12345");

        verify(orderRepository)
                .findEventsByOrderId(MAIN_ORDER_ID);

        verifyNoMoreInteractions(orderRepository);
    }
}
