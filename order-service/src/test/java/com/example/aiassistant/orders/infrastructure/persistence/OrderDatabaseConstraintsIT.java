package com.example.aiassistant.orders.infrastructure.persistence;

import com.example.aiassistant.orders.support.OrderFlywayTestSupport;
import com.example.aiassistant.orders.support.OrderPostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderDatabaseConstraintsIT {

    @Test
    void databaseEnforcesOrderAndEscalationInvariants()
            throws Exception {

        try (PostgreSQLContainer postgres =
                     OrderPostgresTestSupport.newContainer()) {

            postgres.start();
            OrderPostgresTestSupport.initializeDatabase(postgres);

            OrderFlywayTestSupport.createFlyway(postgres).migrate();

            try (Connection connection =
                         OrderPostgresTestSupport.openOrderConnection(postgres)) {

                assertThat(connection.getAutoCommit()).isTrue();

                assertOrderConstraints(connection);
                assertEventConstraints(connection);
                assertEscalationConstraints(connection);
            }
        }
    }

    private static void assertOrderConstraints(
            Connection connection
    ) throws SQLException {

        assertSqlState(connection, "23505", """
                INSERT INTO orders.orders (
                    id, display_number, status, delay_reason,
                    created_at, updated_at
                )
                VALUES (
                    '09b98c5b-37a5-4fa3-97c7-a016d7517760',
                    'TEST-PK',
                    'DELAYED',
                    'OUT_OF_STOCK',
                    '2026-11-10T09:00:00Z',
                    '2026-11-11T10:30:00Z'
                )
                """);

        assertSqlState(connection, "23505", """
                INSERT INTO orders.orders (
                    id, display_number, status, delay_reason,
                    created_at, updated_at
                )
                VALUES (
                    'b1000000-0000-4000-8000-000000000001',
                    '12345',
                    'DELAYED',
                    'OUT_OF_STOCK',
                    '2026-11-10T09:00:00Z',
                    '2026-11-11T10:30:00Z'
                )
                """);

        assertSqlState(connection, "23502", """
                UPDATE orders.orders
                SET display_number = NULL
                WHERE display_number = '12345'
                """);

        assertSqlState(connection, "23514", """
                UPDATE orders.orders
                SET display_number = ''
                WHERE display_number = '12345'
                """);

        assertSqlState(connection, "23514", """
                UPDATE orders.orders
                SET status = 'UNSUPPORTED'
                WHERE display_number = '12345'
                """);

        assertSqlState(connection, "23514", """
                UPDATE orders.orders
                SET delay_reason = 'UNSUPPORTED'
                WHERE display_number = '12345'
                """);

        assertSqlState(connection, "23514", """
                UPDATE orders.orders
                SET updated_at = created_at - INTERVAL '1 second'
                WHERE display_number = '12345'
                """);

        assertThat(readCount(connection, """
                SELECT count(*) FROM orders.orders
                """))
                .isEqualTo(3);
    }

    private static void assertEventConstraints(
            Connection connection
    ) throws SQLException {

        assertSqlState(connection, "23503", """
                INSERT INTO orders.order_events (
                    id, order_id, type, description, created_at
                )
                VALUES (
                    'b2000000-0000-4000-8000-000000000001',
                    'ffffffff-ffff-4fff-8fff-ffffffffffff',
                    'TEST_EVENT',
                    'Synthetic test event.',
                    '2026-11-11T10:30:00Z'
                )
                """);

        assertSqlState(connection, "23502", """
                UPDATE orders.order_events
                SET description = NULL
                WHERE id = 'a1000000-0000-4000-8000-000000000001'
                """);

        assertSqlState(connection, "23503", """
                DELETE FROM orders.orders
                WHERE display_number = '12345'
                """);

        assertThat(readCount(connection, """
                SELECT count(*) FROM orders.order_events
                """))
                .isEqualTo(5);
    }

    private static void assertEscalationConstraints(
            Connection connection
    ) throws SQLException {

        executeUpdate(connection, """
                INSERT INTO orders.escalations (
                    id,
                    order_id,
                    order_display_number,
                    reason,
                    priority,
                    idempotency_key,
                    created_at
                )
                VALUES (
                    'b3000000-0000-4000-8000-000000000001',
                    '09b98c5b-37a5-4fa3-97c7-a016d7517760',
                    '12345',
                    'OUT_OF_STOCK',
                    'HIGH',
                    'c1000000-0000-4000-8000-000000000001',
                    '2026-11-11T11:00:00Z'
                )
                """);

        // Same global idempotency key, different order.
        assertSqlState(connection, "23505", """
                INSERT INTO orders.escalations (
                    id,
                    order_id,
                    order_display_number,
                    reason,
                    priority,
                    idempotency_key,
                    created_at
                )
                VALUES (
                    'b3000000-0000-4000-8000-000000000002',
                    '09b98c5b-37a5-4fa3-97c7-a016d7517761',
                    '12346',
                    'OUT_OF_STOCK',
                    'HIGH',
                    'c1000000-0000-4000-8000-000000000001',
                    '2026-11-11T12:30:00Z'
                )
                """);

        assertSqlState(connection, "23503", """
                INSERT INTO orders.escalations (
                    id,
                    order_id,
                    order_display_number,
                    reason,
                    priority,
                    idempotency_key,
                    created_at
                )
                VALUES (
                    'b3000000-0000-4000-8000-000000000003',
                    'ffffffff-ffff-4fff-8fff-ffffffffffff',
                    '99999',
                    'OUT_OF_STOCK',
                    'HIGH',
                    'c1000000-0000-4000-8000-000000000003',
                    '2026-11-11T11:00:00Z'
                )
                """);

        assertSqlState(connection, "23502", """
                UPDATE orders.escalations
                SET idempotency_key = NULL
                WHERE id = 'b3000000-0000-4000-8000-000000000001'
                """);

        assertSqlState(connection, "23514", """
                UPDATE orders.escalations
                SET reason = 'UNSUPPORTED'
                WHERE id = 'b3000000-0000-4000-8000-000000000001'
                """);

        assertSqlState(connection, "23514", """
                UPDATE orders.escalations
                SET priority = 'UNSUPPORTED'
                WHERE id = 'b3000000-0000-4000-8000-000000000001'
                """);

        assertSqlState(connection, "23514", """
                UPDATE orders.escalations
                SET order_display_number = ''
                WHERE id = 'b3000000-0000-4000-8000-000000000001'
                """);

        // Same order, different idempotency key is allowed.
        executeUpdate(connection, """
                INSERT INTO orders.escalations (
                    id,
                    order_id,
                    order_display_number,
                    reason,
                    priority,
                    idempotency_key,
                    created_at
                )
                VALUES (
                    'b3000000-0000-4000-8000-000000000004',
                    '09b98c5b-37a5-4fa3-97c7-a016d7517760',
                    '12345',
                    'OUT_OF_STOCK',
                    'HIGH',
                    'c1000000-0000-4000-8000-000000000004',
                    '2026-11-11T11:05:00Z'
                )
                """);

        // Order 12347 has no events. Test escalation FK separately.
        executeUpdate(connection, """
                INSERT INTO orders.escalations (
                    id,
                    order_id,
                    order_display_number,
                    reason,
                    priority,
                    idempotency_key,
                    created_at
                )
                VALUES (
                    'b3000000-0000-4000-8000-000000000005',
                    '09b98c5b-37a5-4fa3-97c7-a016d7517762',
                    '12347',
                    'OUT_OF_STOCK',
                    'HIGH',
                    'c1000000-0000-4000-8000-000000000005',
                    '2026-11-11T15:00:00Z'
                )
                """);

        assertSqlState(connection, "23503", """
                DELETE FROM orders.orders
                WHERE display_number = '12347'
                """);

        assertThat(readCount(connection, """
                SELECT count(*) FROM orders.escalations
                """))
                .isEqualTo(3);

        assertThat(readCount(connection, """
                SELECT count(*)
                FROM orders.escalations
                WHERE order_display_number = '12345'
                """))
                .isEqualTo(2);
    }

    private static void assertSqlState(
            Connection connection,
            String expectedSqlState,
            String sql
    ) {

        SQLException exception = assertThrows(
                SQLException.class,
                () -> executeUpdate(connection, sql)
        );

        assertThat(exception.getSQLState())
                .as("SQLSTATE for statement:%n%s", sql)
                .isEqualTo(expectedSqlState);
    }

    private static void executeUpdate(
            Connection connection,
            String sql
    ) throws SQLException {

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private static long readCount(
            Connection connection,
            String sql
    ) throws SQLException {

        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {

            assertThat(result.next()).isTrue();

            return result.getLong(1);
        }
    }
}
