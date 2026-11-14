package com.example.aiassistant.orders.application.port;

import com.example.aiassistant.orders.domain.Order;
import com.example.aiassistant.orders.domain.OrderEvent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {

    /**
     * Finds an order by its exact display number.
     *
     * @param displayNumber the order display number
     * @return the matching order, or an empty Optional
     *         if no order exists
     */
    Optional<Order> findByDisplayNumber(String displayNumber);

    /**
     * Finds an order by its exact display number and acquires
     * a database row lock.
     *
     * <p>Must be called inside an active write transaction.
     * The lock is held until that transaction completes.
     *
     * @param displayNumber the order display number
     * @return the locked order, or an empty Optional
     *         if no order exists
     */
    Optional<Order> findByDisplayNumberForUpdate(
            String displayNumber
    );

    /**
     * Returns events belonging to the specified order.
     *
     * <p>Events must be sorted by creation time ascending,
     * with event ID ascending as the deterministic tie-breaker.
     *
     * @param orderId the internal order identifier
     * @return ordered events, or an empty list if no events exist
     */
    List<OrderEvent> findEventsByOrderId(UUID orderId);
}
