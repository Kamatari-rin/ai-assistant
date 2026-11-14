package com.example.aiassistant.orders.application;

import com.example.aiassistant.orders.application.exception.OrderNotFoundException;
import com.example.aiassistant.orders.application.port.OrderRepository;
import com.example.aiassistant.orders.domain.Order;
import com.example.aiassistant.orders.domain.OrderEvent;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderQueryService {

    private final OrderRepository orderRepository;

    public OrderQueryService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public Order getOrder(String displayNumber) {

        return orderRepository.findByDisplayNumber(displayNumber)
                .orElseThrow(() ->
                        new OrderNotFoundException(displayNumber)
                );
    }

    public List<OrderEvent> getOrderEvents(String displayNumber) {

        Order order = getOrder(displayNumber);

        return orderRepository.findEventsByOrderId(order.id());
    }
}
