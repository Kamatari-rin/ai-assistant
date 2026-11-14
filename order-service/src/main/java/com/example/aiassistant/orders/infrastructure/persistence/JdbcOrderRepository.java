package com.example.aiassistant.orders.infrastructure.persistence;

import com.example.aiassistant.orders.application.port.OrderRepository;
import com.example.aiassistant.orders.domain.DelayReason;
import com.example.aiassistant.orders.domain.Order;
import com.example.aiassistant.orders.domain.OrderEvent;
import com.example.aiassistant.orders.domain.OrderStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcOrderRepository implements OrderRepository {

    private static final String FIND_ORDER_BY_DISPLAY_NUMBER = """
            SELECT
                id,
                display_number,
                status,
                delay_reason,
                created_at,
                updated_at
            FROM orders.orders
            WHERE display_number = :displayNumber
            """;

    private static final String FIND_ORDER_BY_DISPLAY_NUMBER_FOR_UPDATE =
            FIND_ORDER_BY_DISPLAY_NUMBER + "FOR UPDATE\n";

    private static final String FIND_EVENTS_BY_ORDER_ID = """
            SELECT
                id,
                order_id,
                type,
                description,
                created_at
            FROM orders.order_events
            WHERE order_id = :orderId
            ORDER BY created_at ASC, id ASC
            """;

    private static final RowMapper<Order> ORDER_ROW_MAPPER =
            (resultSet, rowNum) -> new Order(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getString("display_number"),
                    OrderStatus.valueOf(
                            resultSet.getString("status")
                    ),
                    DelayReason.valueOf(
                            resultSet.getString("delay_reason")
                    ),
                    readInstant(resultSet, "created_at"),
                    readInstant(resultSet, "updated_at")
            );

    private static final RowMapper<OrderEvent> EVENT_ROW_MAPPER =
            (resultSet, rowNum) -> new OrderEvent(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getObject("order_id", UUID.class),
                    resultSet.getString("type"),
                    resultSet.getString("description"),
                    readInstant(resultSet, "created_at")
            );

    private final JdbcClient jdbcClient;

    public JdbcOrderRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<Order> findByDisplayNumber(
            String displayNumber
    ) {

        return jdbcClient.sql(FIND_ORDER_BY_DISPLAY_NUMBER)
                .param("displayNumber", displayNumber)
                .query(ORDER_ROW_MAPPER)
                .optional();
    }

    @Override
    public Optional<Order> findByDisplayNumberForUpdate(
            String displayNumber
    ) {

        return jdbcClient
                .sql(FIND_ORDER_BY_DISPLAY_NUMBER_FOR_UPDATE)
                .param("displayNumber", displayNumber)
                .query(ORDER_ROW_MAPPER)
                .optional();
    }

    @Override
    public List<OrderEvent> findEventsByOrderId(UUID orderId) {

        return jdbcClient.sql(FIND_EVENTS_BY_ORDER_ID)
                .param("orderId", orderId)
                .query(EVENT_ROW_MAPPER)
                .list();
    }

    private static Instant readInstant(
            ResultSet resultSet,
            String columnName
    ) throws SQLException {

        return resultSet.getObject(
                columnName,
                OffsetDateTime.class
        ).toInstant();
    }
}
