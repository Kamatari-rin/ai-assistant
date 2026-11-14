package com.example.aiassistant.orders.api.dto;

import java.util.List;

public record OrderEventsResponse(
        List<OrderEventResponse> items
) {

    public OrderEventsResponse {
        items = List.copyOf(items);
    }
}
