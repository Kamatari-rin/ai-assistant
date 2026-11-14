package com.example.aiassistant.orders.api;

import com.example.aiassistant.orders.api.dto.OrderEventsResponse;
import com.example.aiassistant.orders.api.dto.OrderResponse;
import com.example.aiassistant.orders.application.OrderQueryService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
        path = "/api/v1/orders",
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class OrderController {

    private final OrderQueryService orderQueryService;

    private final OrderApiMapper orderApiMapper;

    public OrderController(
            OrderQueryService orderQueryService,
            OrderApiMapper orderApiMapper
    ) {
        this.orderQueryService = orderQueryService;
        this.orderApiMapper = orderApiMapper;
    }

    @GetMapping("/{displayNumber}")
    public OrderResponse getOrder(
            @PathVariable("displayNumber")
            String displayNumber
    ) {

        OrderRequestValidator.validateDisplayNumber(
                displayNumber
        );

        return orderApiMapper.toOrderResponse(
                orderQueryService.getOrder(displayNumber)
        );
    }

    @GetMapping("/{displayNumber}/events")
    public OrderEventsResponse getOrderEvents(
            @PathVariable("displayNumber")
            String displayNumber
    ) {

        OrderRequestValidator.validateDisplayNumber(
                displayNumber
        );

        return orderApiMapper.toOrderEventsResponse(
                orderQueryService.getOrderEvents(displayNumber)
        );
    }
}