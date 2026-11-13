package com.example.aiassistant.orders.support;

import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class OrderPostgresInfrastructureIT {

    @Test
    void postgresProvidesIsolatedOrderDatabase() throws Exception {

        try (PostgreSQLContainer postgres =
                     OrderPostgresTestSupport.newContainer()) {

            postgres.start();

            OrderPostgresTestSupport.initializeDatabase(postgres);

            try (Connection connection =
                         OrderPostgresTestSupport.openOrderConnection(postgres);

                 Statement statement = connection.createStatement();

                 ResultSet result = statement.executeQuery("""
                         SELECT
                             current_database() AS database_name,
                             current_user AS role_name,
                             current_schema() AS schema_name,
                             current_setting('server_version_num')
                                 AS server_version_num,
                             pg_get_userbyid(n.nspowner)
                                 AS schema_owner,
                             r.rolsuper AS is_superuser,
                             r.rolcreatedb AS can_create_database,
                             r.rolcreaterole AS can_create_role,
                             r.rolreplication AS can_replicate,
                             e.extversion AS vector_version
                         FROM pg_namespace n
                         JOIN pg_roles r
                             ON r.rolname = current_user
                         JOIN pg_extension e
                             ON e.extname = 'vector'
                         WHERE n.nspname = 'orders'
                         """)) {

                assertThat(result.next()).isTrue();

                assertThat(result.getString("database_name"))
                        .isEqualTo("orders_it");

                assertThat(result.getString("role_name"))
                        .isEqualTo("orders_app");

                assertThat(result.getString("schema_name"))
                        .isEqualTo("orders");

                assertThat(result.getString("schema_owner"))
                        .isEqualTo("orders_app");

                assertThat(result.getInt("server_version_num"))
                        .isEqualTo(160015);

                assertThat(result.getBoolean("is_superuser"))
                        .isFalse();

                assertThat(result.getBoolean("can_create_database"))
                        .isFalse();

                assertThat(result.getBoolean("can_create_role"))
                        .isFalse();

                assertThat(result.getBoolean("can_replicate"))
                        .isFalse();

                assertThat(result.getString("vector_version"))
                        .isEqualTo("0.8.7");

                assertThat(result.next()).isFalse();
            }
        }
    }
}
