package com.example.aiassistant.orders.api;

import com.example.aiassistant.orders.application.port.OrderRepository;
import com.example.aiassistant.orders.application.port.EscalationRepository;
import com.example.aiassistant.orders.domain.DelayReason;
import com.example.aiassistant.orders.domain.Order;
import com.example.aiassistant.orders.domain.OrderEvent;
import com.example.aiassistant.orders.domain.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderControllerTest {

    private static final UUID MAIN_ORDER_ID =
            UUID.fromString(
                    "09b98c5b-37a5-4fa3-97c7-a016d7517760"
            );

    private static final UUID EMPTY_HISTORY_ORDER_ID =
            UUID.fromString(
                    "09b98c5b-37a5-4fa3-97c7-a016d7517762"
            );

    private static final Order MAIN_ORDER =
            new Order(
                    MAIN_ORDER_ID,
                    "12345",
                    OrderStatus.DELAYED,
                    DelayReason.OUT_OF_STOCK,
                    Instant.parse("2026-11-10T09:00:00Z"),
                    Instant.parse("2026-11-11T10:30:00Z")
            );

    private static final Order EMPTY_HISTORY_ORDER =
            new Order(
                    EMPTY_HISTORY_ORDER_ID,
                    "12347",
                    OrderStatus.DELAYED,
                    DelayReason.OUT_OF_STOCK,
                    Instant.parse("2026-11-10T13:00:00Z"),
                    Instant.parse("2026-11-11T14:00:00Z")
            );

    @MockitoBean(enforceOverride = true)
    private OrderRepository orderRepository;

    @MockitoBean(enforceOverride = true)
    private EscalationRepository escalationRepository;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsOrderMatchingPublicContract() throws Exception {

        when(orderRepository.findByDisplayNumber("12345"))
                .thenReturn(Optional.of(MAIN_ORDER));

        mockMvc.perform(
                        get("/api/v1/orders/{displayNumber}", "12345")
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.id")
                        .value(MAIN_ORDER_ID.toString()))
                .andExpect(jsonPath("$.displayNumber")
                        .value("12345"))
                .andExpect(jsonPath("$.status")
                        .value("DELAYED"))
                .andExpect(jsonPath("$.delayReason")
                        .value("OUT_OF_STOCK"))
                .andExpect(jsonPath("$.updatedAt")
                        .value("2026-11-11T10:30:00Z"))
                .andExpect(jsonPath("$.createdAt")
                        .doesNotHaveJsonPath());

        verify(orderRepository)
                .findByDisplayNumber("12345");

        verifyNoMoreInteractions(orderRepository);
    }

    @Test
    void returnsEventsMatchingPublicContract()
            throws Exception {

        List<OrderEvent> events = List.of(
                new OrderEvent(
                        UUID.fromString(
                                "a1000000-0000-4000-8000-000000000001"
                        ),
                        MAIN_ORDER_ID,
                        "ORDER_CREATED",
                        "Synthetic order was created.",
                        Instant.parse("2026-11-10T09:00:00Z")
                ),
                new OrderEvent(
                        UUID.fromString(
                                "a1000000-0000-4000-8000-000000000002"
                        ),
                        MAIN_ORDER_ID,
                        "ORDER_PROCESSING",
                        "Order processing started.",
                        Instant.parse("2026-11-10T09:15:00Z")
                ),
                new OrderEvent(
                        UUID.fromString(
                                "a1000000-0000-4000-8000-000000000003"
                        ),
                        MAIN_ORDER_ID,
                        "STOCK_UNAVAILABLE",
                        "Required item is temporarily out of stock.",
                        Instant.parse("2026-11-11T10:30:00Z")
                )
        );

        when(orderRepository.findByDisplayNumber("12345"))
                .thenReturn(Optional.of(MAIN_ORDER));

        when(orderRepository.findEventsByOrderId(MAIN_ORDER_ID))
                .thenReturn(events);

        mockMvc.perform(
                        get(
                                "/api/v1/orders/{displayNumber}/events",
                                "12345"
                        )
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.items.length()")
                        .value(3))
                .andExpect(jsonPath("$.items[0].type")
                        .value("ORDER_CREATED"))
                .andExpect(jsonPath("$.items[0].description")
                        .value("Synthetic order was created."))
                .andExpect(jsonPath("$.items[0].createdAt")
                        .value("2026-11-10T09:00:00Z"))
                .andExpect(jsonPath("$.items[1].type")
                        .value("ORDER_PROCESSING"))
                .andExpect(jsonPath("$.items[1].description")
                        .value("Order processing started."))
                .andExpect(jsonPath("$.items[1].createdAt")
                        .value("2026-11-10T09:15:00Z"))
                .andExpect(jsonPath("$.items[2].type")
                        .value("STOCK_UNAVAILABLE"))
                .andExpect(jsonPath("$.items[2].description")
                        .value(
                                "Required item is temporarily out of stock."
                        ))
                .andExpect(jsonPath("$.items[2].createdAt")
                        .value("2026-11-11T10:30:00Z"))
                .andExpect(jsonPath("$.items[0].id")
                        .doesNotHaveJsonPath())
                .andExpect(jsonPath("$.items[0].orderId")
                        .doesNotHaveJsonPath());

        verify(orderRepository)
                .findByDisplayNumber("12345");

        verify(orderRepository)
                .findEventsByOrderId(MAIN_ORDER_ID);

        verifyNoMoreInteractions(orderRepository);
    }

    @Test
    void returnsEmptyItemsForExistingOrderWithoutEvents()
            throws Exception {

        when(orderRepository.findByDisplayNumber("12347"))
                .thenReturn(Optional.of(EMPTY_HISTORY_ORDER));

        when(orderRepository.findEventsByOrderId(
                EMPTY_HISTORY_ORDER_ID
        ))
                .thenReturn(List.of());

        mockMvc.perform(
                        get(
                                "/api/v1/orders/{displayNumber}/events",
                                "12347"
                        )
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()")
                        .value(0));

        verify(orderRepository)
                .findByDisplayNumber("12347");

        verify(orderRepository)
                .findEventsByOrderId(EMPTY_HISTORY_ORDER_ID);

        verifyNoMoreInteractions(orderRepository);
    }

    @Test
    void preservesDisplayNumberWithLeadingZeros()
            throws Exception {

        String displayNumber = "0012345";

        Order order = new Order(
                MAIN_ORDER_ID,
                displayNumber,
                OrderStatus.DELAYED,
                DelayReason.OUT_OF_STOCK,
                Instant.parse("2026-11-10T09:00:00Z"),
                Instant.parse("2026-11-11T10:30:00Z")
        );

        when(orderRepository.findByDisplayNumber(displayNumber))
                .thenReturn(Optional.of(order));

        mockMvc.perform(
                        get(
                                "/api/v1/orders/{displayNumber}",
                                displayNumber
                        )
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayNumber")
                        .value(displayNumber));

        verify(orderRepository)
                .findByDisplayNumber(displayNumber);

        verifyNoMoreInteractions(orderRepository);
    }
}
