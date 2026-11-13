package com.example.aiassistant.orders.infrastructure.persistence;

import com.example.aiassistant.orders.application.port.OrderRepository;
import com.example.aiassistant.orders.domain.DelayReason;
import com.example.aiassistant.orders.domain.Order;
import com.example.aiassistant.orders.domain.OrderEvent;
import com.example.aiassistant.orders.domain.OrderStatus;
import com.example.aiassistant.orders.support.OrderFlywayTestSupport;
import com.example.aiassistant.orders.support.OrderPostgresTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcOrderRepositoryIT {

    private static final UUID MAIN_ORDER_ID =
            UUID.fromString(
                    "09b98c5b-37a5-4fa3-97c7-a016d7517760"
            );

    private static final UUID EMPTY_HISTORY_ORDER_ID =
            UUID.fromString(
                    "09b98c5b-37a5-4fa3-97c7-a016d7517762"
            );

    private static PostgreSQLContainer postgres;

    private static JdbcClient jdbcClient;

    private static OrderRepository repository;

    private static TransactionTemplate transactionTemplate;

    @BeforeAll
    static void setUpDatabase() throws Exception {

        postgres = OrderPostgresTestSupport.newContainer();

        try {
            postgres.start();

            OrderPostgresTestSupport.initializeDatabase(postgres);

            OrderFlywayTestSupport.createFlyway(postgres)
                    .migrate();

            DriverManagerDataSource dataSource =
                    new DriverManagerDataSource(
                            postgres.getJdbcUrl(),
                            OrderPostgresTestSupport.ORDER_USERNAME,
                            OrderPostgresTestSupport.ORDER_PASSWORD
                    );

            jdbcClient = JdbcClient.create(dataSource);

            repository = new JdbcOrderRepository(jdbcClient);

            transactionTemplate = new TransactionTemplate(
                    new DataSourceTransactionManager(dataSource)
            );

        } catch (Exception | Error exception) {

            try {
                postgres.close();
            } catch (Exception cleanupException) {
                exception.addSuppressed(cleanupException);
            }

            postgres = null;

            throw exception;
        }
    }

    @AfterAll
    static void tearDownDatabase() {

        if (postgres != null) {
            postgres.close();
        }
    }

    @Test
    void findsOrderByDisplayNumberAndMapsAllFields() {

        Order expectedOrder = new Order(
                MAIN_ORDER_ID,
                "12345",
                OrderStatus.DELAYED,
                DelayReason.OUT_OF_STOCK,
                Instant.parse("2026-11-10T09:00:00Z"),
                Instant.parse("2026-11-11T10:30:00Z")
        );

        assertThat(repository.findByDisplayNumber("12345"))
                .contains(expectedOrder);
    }

    @Test
    void returnsEmptyForMissingOrdersAndDoesNotNormalizeInput() {

        assertThat(repository.findByDisplayNumber("99999"))
                .isEmpty();

        assertThat(repository.findByDisplayNumber("0012345"))
                .isEmpty();

        assertThat(repository.findByDisplayNumber(" 12345 "))
                .isEmpty();

        assertThat(repository.findByDisplayNumber("ORDER-A"))
                .isEmpty();

        assertThat(repository.findByDisplayNumber(" "))
                .isEmpty();

        assertThat(repository.findByDisplayNumber(
                "12345' OR '1'='1"
        ))
                .isEmpty();

        assertThat(repository.findByDisplayNumber("12345"))
                .isPresent();
    }

    @Test
    void findsOrderEventsAndMapsThemInChronologicalOrder() {

        List<OrderEvent> expectedEvents = List.of(
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

        assertThat(repository.findEventsByOrderId(MAIN_ORDER_ID))
                .containsExactlyElementsOf(expectedEvents);
    }

    @Test
    void returnsEmptyEventsForOrdersWithoutHistory() {

        assertThat(repository.findByDisplayNumber("12347"))
                .isPresent();

        assertThat(repository.findEventsByOrderId(
                EMPTY_HISTORY_ORDER_ID
        ))
                .isEmpty();

        assertThat(repository.findEventsByOrderId(
                UUID.fromString(
                        "ffffffff-ffff-4fff-8fff-ffffffffffff"
                )
        ))
                .isEmpty();
    }

    @Test
    void sortsEventsByIdWhenCreationTimestampsAreEqual() {

        UUID earlierId = UUID.fromString(
                "10000000-0000-4000-8000-000000000001"
        );

        UUID laterId = UUID.fromString(
                "80000000-0000-4000-8000-000000000002"
        );

        OffsetDateTime createdAt =
                OffsetDateTime.parse("2026-11-11T15:00:00Z");

        transactionTemplate.executeWithoutResult(status -> {

            status.setRollbackOnly();

            // Insert the larger ID first.
            insertEvent(
                    laterId,
                    EMPTY_HISTORY_ORDER_ID,
                    "SECOND_EVENT",
                    createdAt
            );

            insertEvent(
                    earlierId,
                    EMPTY_HISTORY_ORDER_ID,
                    "FIRST_EVENT",
                    createdAt
            );

            List<OrderEvent> events =
                    repository.findEventsByOrderId(
                            EMPTY_HISTORY_ORDER_ID
                    );

            assertThat(events)
                    .extracting(OrderEvent::id)
                    .containsExactly(earlierId, laterId);

            assertThat(events)
                    .extracting(OrderEvent::createdAt)
                    .containsExactly(
                            createdAt.toInstant(),
                            createdAt.toInstant()
                    );
        });

        assertThat(repository.findEventsByOrderId(
                EMPTY_HISTORY_ORDER_ID
        ))
                .isEmpty();
    }

    @Test
    void treatsDisplayNumbersAsDistinctExactStrings() {

        UUID leadingZerosId = UUID.fromString(
                "d1000000-0000-4000-8000-000000000001"
        );

        UUID whitespaceId = UUID.fromString(
                "d1000000-0000-4000-8000-000000000002"
        );

        transactionTemplate.executeWithoutResult(status -> {

            status.setRollbackOnly();

            insertOrder(leadingZerosId, "0012345");

            insertOrder(whitespaceId, " 12345 ");

            assertThat(repository.findByDisplayNumber("12345"))
                    .hasValueSatisfying(order ->
                            assertThat(order.id())
                                    .isEqualTo(MAIN_ORDER_ID)
                    );

            assertThat(repository.findByDisplayNumber("0012345"))
                    .hasValueSatisfying(order -> {
                        assertThat(order.id())
                                .isEqualTo(leadingZerosId);
                        assertThat(order.displayNumber())
                                .isEqualTo("0012345");
                    });

            assertThat(repository.findByDisplayNumber(" 12345 "))
                    .hasValueSatisfying(order -> {
                        assertThat(order.id())
                                .isEqualTo(whitespaceId);
                        assertThat(order.displayNumber())
                                .isEqualTo(" 12345 ");
                    });
        });

        assertThat(repository.findByDisplayNumber("0012345"))
                .isEmpty();

        assertThat(repository.findByDisplayNumber(" 12345 "))
                .isEmpty();
    }

    private static void insertEvent(
            UUID id,
            UUID orderId,
            String type,
            OffsetDateTime createdAt
    ) {

        int rows = jdbcClient.sql("""
                        INSERT INTO orders.order_events (
                            id,
                            order_id,
                            type,
                            description,
                            created_at
                        )
                        VALUES (
                            :id,
                            :orderId,
                            :type,
                            :description,
                            :createdAt
                        )
                        """)
                .param("id", id)
                .param("orderId", orderId)
                .param("type", type)
                .param("description", "Synthetic integration test event.")
                .param("createdAt", createdAt)
                .update();

        assertThat(rows).isEqualTo(1);
    }

    private static void insertOrder(
            UUID id,
            String displayNumber
    ) {

        OffsetDateTime createdAt =
                OffsetDateTime.parse("2026-11-11T15:00:00Z");

        int rows = jdbcClient.sql("""
                        INSERT INTO orders.orders (
                            id,
                            display_number,
                            status,
                            delay_reason,
                            created_at,
                            updated_at
                        )
                        VALUES (
                            :id,
                            :displayNumber,
                            :status,
                            :delayReason,
                            :createdAt,
                            :updatedAt
                        )
                        """)
                .param("id", id)
                .param("displayNumber", displayNumber)
                .param("status", "DELAYED")
                .param("delayReason", "OUT_OF_STOCK")
                .param("createdAt", createdAt)
                .param("updatedAt", createdAt)
                .update();

        assertThat(rows).isEqualTo(1);
    }
}
