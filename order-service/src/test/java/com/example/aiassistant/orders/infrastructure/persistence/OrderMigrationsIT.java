package com.example.aiassistant.orders.infrastructure.persistence;

import com.example.aiassistant.orders.support.OrderFlywayTestSupport;
import com.example.aiassistant.orders.support.OrderPostgresTestSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderMigrationsIT {

    private static final UUID MAIN_ORDER_ID =
            UUID.fromString("09b98c5b-37a5-4fa3-97c7-a016d7517760");

    @Test
    void freshDatabaseMigratesAndRepeatedMigrationDoesNotDuplicateSeed()
            throws Exception {

        try (PostgreSQLContainer postgres =
                     OrderPostgresTestSupport.newContainer()) {

            postgres.start();
            OrderPostgresTestSupport.initializeDatabase(postgres);

            Flyway flyway =
                    OrderFlywayTestSupport.createFlyway(postgres);

            assertThat(flyway.migrate().migrationsExecuted)
                    .isEqualTo(3);

            flyway.validate();

            try (Connection connection =
                         OrderPostgresTestSupport.openOrderConnection(postgres)) {

                assertMigrationHistory(connection, "1", "2", "3");
                assertSchemaTables(connection);
                assertSeedData(connection);
                assertIndexes(connection);
            }

            assertThat(flyway.migrate().migrationsExecuted)
                    .isZero();

            flyway.validate();

            try (Connection connection =
                         OrderPostgresTestSupport.openOrderConnection(postgres)) {

                assertMigrationHistory(connection, "1", "2", "3");
                assertSeedData(connection);
            }
        }
    }

    @Test
    void existingFoundationV1UpgradesToV3WithoutReapplyingV1()
            throws Exception {

        try (PostgreSQLContainer postgres =
                     OrderPostgresTestSupport.newContainer()) {

            postgres.start();
            OrderPostgresTestSupport.initializeDatabase(postgres);

            Flyway foundationFlyway =
                    OrderFlywayTestSupport.createFlyway(postgres, "1");

            assertThat(foundationFlyway.migrate().migrationsExecuted)
                    .isEqualTo(1);

            MigrationHistoryEntry originalV1;

            try (Connection connection =
                         OrderPostgresTestSupport.openOrderConnection(postgres)) {

                assertMigrationHistory(connection, "1");

                assertThat(readSchemaTables(connection))
                        .containsExactly("flyway_schema_history");

                originalV1 = readMigrationHistory(connection).getFirst();
            }

            Flyway currentFlyway =
                    OrderFlywayTestSupport.createFlyway(postgres);

            assertThat(currentFlyway.migrate().migrationsExecuted)
                    .isEqualTo(2);

            currentFlyway.validate();

            try (Connection connection =
                         OrderPostgresTestSupport.openOrderConnection(postgres)) {

                assertMigrationHistory(connection, "1", "2", "3");

                MigrationHistoryEntry upgradedV1 =
                        readMigrationHistory(connection).getFirst();

                assertThat(upgradedV1).isEqualTo(originalV1);

                assertSchemaTables(connection);
                assertSeedData(connection);
                assertIndexes(connection);
            }
        }
    }

    private static void assertMigrationHistory(
            Connection connection,
            String... expectedVersions
    ) throws SQLException {

        List<MigrationHistoryEntry> history =
                readMigrationHistory(connection);

        assertThat(history)
                .extracting(MigrationHistoryEntry::version)
                .containsExactly(expectedVersions);

        assertThat(history).allSatisfy(entry -> {
            assertThat(entry.success()).isTrue();
            assertThat(entry.installedBy()).isEqualTo("orders_app");
            assertThat(entry.checksum()).isNotNull();
        });
    }

    private static List<MigrationHistoryEntry> readMigrationHistory(
            Connection connection
    ) throws SQLException {

        List<MigrationHistoryEntry> history = new ArrayList<>();

        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                     SELECT
                         installed_rank,
                         version,
                         checksum,
                         installed_by,
                         installed_on,
                         success
                     FROM orders.flyway_schema_history
                     WHERE version IS NOT NULL
                     ORDER BY installed_rank
                     """)) {

            while (result.next()) {
                history.add(new MigrationHistoryEntry(
                        result.getInt("installed_rank"),
                        result.getString("version"),
                        result.getObject("checksum", Integer.class),
                        result.getString("installed_by"),
                        result.getString("installed_on"),
                        result.getBoolean("success")
                ));
            }
        }

        return history;
    }

    private static List<String> readSchemaTables(
            Connection connection
    ) throws SQLException {

        List<String> tables = new ArrayList<>();

        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                     SELECT tablename
                     FROM pg_catalog.pg_tables
                     WHERE schemaname = 'orders'
                     ORDER BY tablename
                     """)) {

            while (result.next()) {
                tables.add(result.getString("tablename"));
            }
        }

        return tables;
    }

    private static void assertSchemaTables(
            Connection connection
    ) throws SQLException {

        assertThat(readSchemaTables(connection))
                .containsExactlyInAnyOrder(
                        "flyway_schema_history",
                        "orders",
                        "order_events",
                        "escalations"
                );
    }

    private static void assertSeedData(
            Connection connection
    ) throws SQLException {

        List<String> displayNumbers = new ArrayList<>();

        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                     SELECT display_number
                     FROM orders.orders
                     ORDER BY display_number
                     """)) {

            while (result.next()) {
                displayNumbers.add(result.getString("display_number"));
            }
        }

        assertThat(displayNumbers)
                .containsExactly("12345", "12346", "12347");

        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                     SELECT
                         id,
                         status,
                         delay_reason,
                         created_at,
                         updated_at
                     FROM orders.orders
                     WHERE display_number = '12345'
                     """)) {

            assertThat(result.next()).isTrue();

            assertThat(result.getObject("id", UUID.class))
                    .isEqualTo(MAIN_ORDER_ID);

            assertThat(result.getString("status"))
                    .isEqualTo("DELAYED");

            assertThat(result.getString("delay_reason"))
                    .isEqualTo("OUT_OF_STOCK");

            assertThat(result.getObject(
                    "created_at",
                    OffsetDateTime.class
            ).toInstant())
                    .isEqualTo(Instant.parse("2026-11-10T09:00:00Z"));

            assertThat(result.getObject(
                    "updated_at",
                    OffsetDateTime.class
            ).toInstant())
                    .isEqualTo(Instant.parse("2026-11-11T10:30:00Z"));

            assertThat(result.next()).isFalse();
        }

        List<String> mainOrderEventTypes = new ArrayList<>();

        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                     SELECT
                         e.type,
                         e.description,
                         e.created_at
                     FROM orders.order_events e
                     JOIN orders.orders o ON o.id = e.order_id
                     WHERE o.display_number = '12345'
                     ORDER BY e.created_at, e.id
                     """)) {

            while (result.next()) {

                String type = result.getString("type");

                mainOrderEventTypes.add(type);

                if ("STOCK_UNAVAILABLE".equals(type)) {

                    assertThat(result.getString("description"))
                            .isEqualTo(
                                    "Required item is temporarily out of stock."
                            );

                    assertThat(result.getObject(
                            "created_at",
                            OffsetDateTime.class
                    ).toInstant())
                            .isEqualTo(
                                    Instant.parse("2026-11-11T10:30:00Z")
                            );
                }
            }
        }

        assertThat(mainOrderEventTypes)
                .containsExactly(
                        "ORDER_CREATED",
                        "ORDER_PROCESSING",
                        "STOCK_UNAVAILABLE"
                );

        assertThat(readCount(connection, """
                SELECT count(*)
                FROM orders.order_events
                """))
                .isEqualTo(5);

        assertThat(readCount(connection, """
                SELECT count(*)
                FROM orders.order_events e
                JOIN orders.orders o ON o.id = e.order_id
                WHERE o.display_number = '12346'
                """))
                .isEqualTo(2);

        assertThat(readCount(connection, """
                SELECT count(*)
                FROM orders.order_events e
                JOIN orders.orders o ON o.id = e.order_id
                WHERE o.display_number = '12347'
                """))
                .isZero();

        assertThat(readCount(connection, """
                SELECT count(*)
                FROM orders.escalations
                """))
                .isZero();
    }

    private static void assertIndexes(
            Connection connection
    ) throws SQLException {

        List<String> indexes = new ArrayList<>();

        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                     SELECT indexname
                     FROM pg_catalog.pg_indexes
                     WHERE schemaname = 'orders'
                       AND tablename IN (
                           'orders',
                           'order_events',
                           'escalations'
                       )
                     """)) {

            while (result.next()) {
                indexes.add(result.getString("indexname"));
            }
        }

        assertThat(indexes)
                .containsExactlyInAnyOrder(
                        "pk_orders",
                        "uq_orders_display_number",
                        "pk_order_events",
                        "idx_order_events_order_created_id",
                        "pk_escalations",
                        "uq_escalations_idempotency_key",
                        "idx_escalations_order_id"
                );
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

    private record MigrationHistoryEntry(
            int installedRank,
            String version,
            Integer checksum,
            String installedBy,
            String installedOn,
            boolean success
    ) {
    }
}
