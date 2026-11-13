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
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderApiMapper {

    public OrderResponse toOrderResponse(Order order) {

        return new OrderResponse(
                order.id(),
                order.displayNumber(),
                toOrderStatusResponse(order.status()),
                toDelayReasonResponse(order.delayReason()),
                order.updatedAt()
        );
    }

    public OrderEventsResponse toOrderEventsResponse(
            List<OrderEvent> events
    ) {

        List<OrderEventResponse> items = events.stream()
                .map(this::toOrderEventResponse)
                .toList();

        return new OrderEventsResponse(items);
    }

    private OrderEventResponse toOrderEventResponse(
            OrderEvent event
    ) {

        return new OrderEventResponse(
                event.type(),
                event.description(),
                event.createdAt()
        );
    }

    private OrderStatusResponse toOrderStatusResponse(
            OrderStatus status
    ) {

        return switch (status) {
            case DELAYED -> OrderStatusResponse.DELAYED;
        };
    }

    private DelayReasonResponse toDelayReasonResponse(
            DelayReason reason
    ) {

        return switch (reason) {
            case OUT_OF_STOCK ->
                    DelayReasonResponse.OUT_OF_STOCK;
        };
    }
}
