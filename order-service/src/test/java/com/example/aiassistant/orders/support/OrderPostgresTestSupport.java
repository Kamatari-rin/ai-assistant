package com.example.aiassistant.orders.support;

import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class OrderPostgresTestSupport {

    private static final DockerImageName POSTGRES_IMAGE =
            DockerImageName.parse(
                    "pgvector/pgvector:0.8.7-pg16-bookworm"
                            + "@sha256:0dd3149a06e4579a0d70d8fef9945636cd514005e7237deb27f9b67886f8dc4f"
            ).asCompatibleSubstituteFor("postgres");


    public static final String DATABASE_NAME = "orders_it";

    public static final String ADMIN_USERNAME = "postgres";
    public static final String ADMIN_PASSWORD = "test-admin-password";

    public static final String ORDER_USERNAME = "orders_app";
    public static final String ORDER_PASSWORD = "test-orders-password";

    private OrderPostgresTestSupport() {
    }

    public static PostgreSQLContainer newContainer() {
        return new PostgreSQLContainer(POSTGRES_IMAGE)
                .withDatabaseName(DATABASE_NAME)
                .withUsername(ADMIN_USERNAME)
                .withPassword(ADMIN_PASSWORD);
    }

    public static Connection openAdminConnection(
            PostgreSQLContainer postgres
    ) throws SQLException {

        return DriverManager.getConnection(
                postgres.getJdbcUrl(),
                ADMIN_USERNAME,
                ADMIN_PASSWORD
        );
    }

    public static Connection openOrderConnection(
            PostgreSQLContainer postgres
    ) throws SQLException {

        return DriverManager.getConnection(
                postgres.getJdbcUrl(),
                ORDER_USERNAME,
                ORDER_PASSWORD
        );
    }

    public static void initializeDatabase(
            PostgreSQLContainer postgres
    ) throws SQLException {

        try (Connection connection = openAdminConnection(postgres)) {
            connection.setAutoCommit(false);

            try (Statement statement = connection.createStatement()) {

                statement.execute("""
                        CREATE EXTENSION IF NOT EXISTS vector
                        WITH SCHEMA public
                        """);

                statement.execute("""
                        REVOKE CREATE ON SCHEMA public
                        FROM PUBLIC
                        """);

                statement.execute("""
                        REVOKE ALL PRIVILEGES ON DATABASE orders_it
                        FROM PUBLIC
                        """);

                statement.execute("""
                        CREATE ROLE orders_app
                        WITH LOGIN
                        PASSWORD 'test-orders-password'
                        NOSUPERUSER
                        NOCREATEDB
                        NOCREATEROLE
                        NOREPLICATION
                        """);

                statement.execute("""
                        GRANT CONNECT ON DATABASE orders_it
                        TO orders_app
                        """);

                statement.execute("""
                        GRANT USAGE ON SCHEMA public
                        TO orders_app
                        """);

                statement.execute("""
                        CREATE SCHEMA orders
                        AUTHORIZATION orders_app
                        """);

                statement.execute("""
                        REVOKE ALL ON SCHEMA orders
                        FROM PUBLIC
                        """);

                statement.execute("""
                        ALTER ROLE orders_app
                        IN DATABASE orders_it
                        SET search_path TO orders, public
                        """);

                connection.commit();

            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }
}
