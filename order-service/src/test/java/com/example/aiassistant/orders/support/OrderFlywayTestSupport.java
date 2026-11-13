package com.example.aiassistant.orders.support;

import org.flywaydb.core.Flyway;
import org.testcontainers.postgresql.PostgreSQLContainer;

public final class OrderFlywayTestSupport {

    private OrderFlywayTestSupport() {
    }

    public static Flyway createFlyway(PostgreSQLContainer postgres) {
        return createFlyway(postgres, "latest");
    }

    public static Flyway createFlyway(
            PostgreSQLContainer postgres,
            String target
    ) {
        return Flyway.configure()
                .dataSource(
                        postgres.getJdbcUrl(),
                        OrderPostgresTestSupport.ORDER_USERNAME,
                        OrderPostgresTestSupport.ORDER_PASSWORD
                )
                .locations("classpath:db/migration")
                .defaultSchema("orders")
                .schemas("orders")
                .table("flyway_schema_history")
                .createSchemas(false)
                .baselineOnMigrate(false)
                .validateOnMigrate(true)
                .cleanDisabled(true)
                .target(target)
                .load();
    }
}
