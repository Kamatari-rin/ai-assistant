
package com.example.aiassistant.orders.infrastructure.persistence;

import com.example.aiassistant.orders.application.port.EscalationRepository;
import com.example.aiassistant.orders.domain.Escalation;
import com.example.aiassistant.orders.domain.EscalationIdentity;
import com.example.aiassistant.orders.domain.EscalationPriority;
import com.example.aiassistant.orders.domain.EscalationReason;
import com.example.aiassistant.orders.support.OrderFlywayTestSupport;
import com.example.aiassistant.orders.support.OrderPostgresTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcEscalationRepositoryIT {

    private static final UUID MAIN_ORDER_ID =
            UUID.fromString(
                    "09b98c5b-37a5-4fa3-97c7-a016d7517760"
            );

    private static final UUID SECOND_ORDER_ID =
            UUID.fromString(
                    "09b98c5b-37a5-4fa3-97c7-a016d7517761"
            );

    private static final Instant CREATED_AT =
            Instant.parse("2026-11-11T11:00:00.123456Z");

    private static PostgreSQLContainer postgres;

    private static JdbcClient jdbcClient;

    private static EscalationRepository repository;

    private static TransactionTemplate transactionTemplate;

    @BeforeAll
    static void setUpDatabase() throws Exception {

        postgres = OrderPostgresTestSupport.newContainer();

        try {
            postgres.start();

            OrderPostgresTestSupport.initializeDatabase(
                    postgres
            );

            OrderFlywayTestSupport.createFlyway(postgres)
                    .migrate();

            DriverManagerDataSource dataSource =
                    new DriverManagerDataSource(
                            postgres.getJdbcUrl(),
                            OrderPostgresTestSupport.ORDER_USERNAME,
                            OrderPostgresTestSupport.ORDER_PASSWORD
                    );

            jdbcClient = JdbcClient.create(dataSource);

            repository = new JdbcEscalationRepository(
                    jdbcClient
            );

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
    void returnsEmptyForUnknownIdempotencyKey() {

        assertThat(
                repository.findByIdempotencyKey(
                        UUID.randomUUID()
                )
        ).isEmpty();
    }

    @Test
    void insertsEscalationAndMapsAllPersistedFields() {

        UUID key = UUID.randomUUID();

        Escalation candidate = candidate(
                MAIN_ORDER_ID,
                "12345",
                key
        );

        transactionTemplate.executeWithoutResult(status -> {

            status.setRollbackOnly();

            assertThat(
                    repository.insertIfAbsent(candidate)
            ).contains(candidate);

            assertThat(
                    repository.findByIdempotencyKey(key)
            ).contains(candidate);

            assertThat(countByKey(key))
                    .isEqualTo(1L);
        });

        assertThat(
                repository.findByIdempotencyKey(key)
        ).isEmpty();
    }

    @Test
    void skipsDuplicateKeyForSameOrderWithoutAbortingTransaction() {

        UUID key = UUID.randomUUID();

        Escalation first = candidate(
                MAIN_ORDER_ID,
                "12345",
                key
        );

        Escalation duplicate = candidate(
                MAIN_ORDER_ID,
                "12345",
                key
        );

        transactionTemplate.executeWithoutResult(status -> {

            status.setRollbackOnly();

            assertThat(
                    repository.insertIfAbsent(first)
            ).contains(first);

            assertThat(
                    repository.insertIfAbsent(duplicate)
            ).isEmpty();

            assertThat(
                    repository.findByIdempotencyKey(key)
            ).contains(first);

            assertThat(countByKey(key))
                    .isEqualTo(1L);
        });
    }

    @Test
    void enforcesIdempotencyKeyGloballyAcrossOrders() {

        UUID key = UUID.randomUUID();

        Escalation first = candidate(
                MAIN_ORDER_ID,
                "12345",
                key
        );

        Escalation competing = candidate(
                SECOND_ORDER_ID,
                "12346",
                key
        );

        transactionTemplate.executeWithoutResult(status -> {

            status.setRollbackOnly();

            assertThat(
                    repository.insertIfAbsent(first)
            ).contains(first);

            assertThat(
                    repository.insertIfAbsent(competing)
            ).isEmpty();

            assertThat(
                    repository.findByIdempotencyKey(key)
            ).contains(first);

            assertThat(countByKey(key))
                    .isEqualTo(1L);
        });
    }

    @Test
    void allowsDistinctKeysForSameOrder() {

        Escalation first = candidate(
                MAIN_ORDER_ID,
                "12345",
                UUID.randomUUID()
        );

        Escalation second = candidate(
                MAIN_ORDER_ID,
                "12345",
                UUID.randomUUID()
        );

        transactionTemplate.executeWithoutResult(status -> {

            status.setRollbackOnly();

            assertThat(
                    repository.insertIfAbsent(first)
            ).contains(first);

            assertThat(
                    repository.insertIfAbsent(second)
            ).contains(second);

            assertThat(
                    repository.findByIdempotencyKey(
                            first.idempotencyKey()
                    )
            ).contains(first);

            assertThat(
                    repository.findByIdempotencyKey(
                            second.idempotencyKey()
                    )
            ).contains(second);

            assertThat(
                    countByKey(first.idempotencyKey())
            ).isEqualTo(1L);

            assertThat(
                    countByKey(second.idempotencyKey())
            ).isEqualTo(1L);
        });
    }

    @Test
    void propagatesPrimaryKeyViolationAndRollsBackTransaction() {

        Escalation first = candidate(
                MAIN_ORDER_ID,
                "12345",
                UUID.randomUUID()
        );

        Escalation conflicting = new Escalation(
                first.id(),
                MAIN_ORDER_ID,
                first.identity(),
                UUID.randomUUID(),
                CREATED_AT
        );

        assertThatThrownBy(() ->
                transactionTemplate.executeWithoutResult(
                        status -> {

                            assertThat(
                                    repository.insertIfAbsent(
                                            first
                                    )
                            ).contains(first);

                            repository.insertIfAbsent(
                                    conflicting
                            );
                        }
                )
        ).isInstanceOf(DuplicateKeyException.class);

        // The first insert must also have been rolled back.
        assertThat(
                repository.findByIdempotencyKey(
                        first.idempotencyKey()
                )
        ).isEmpty();

        assertThat(
                repository.findByIdempotencyKey(
                        conflicting.idempotencyKey()
                )
        ).isEmpty();
    }

    @Test
    void propagatesForeignKeyViolation() {

        UUID missingOrderId = UUID.fromString(
                "ffffffff-ffff-4fff-8fff-ffffffffffff"
        );

        Escalation invalid = candidate(
                missingOrderId,
                "99999",
                UUID.randomUUID()
        );

        assertThatThrownBy(() ->
                transactionTemplate.executeWithoutResult(
                        status ->
                                repository.insertIfAbsent(
                                        invalid
                                )
                )
        ).isInstanceOf(
                DataIntegrityViolationException.class
        );

        assertThat(
                repository.findByIdempotencyKey(
                        invalid.idempotencyKey()
                )
        ).isEmpty();
    }

    private static Escalation candidate(
            UUID orderId,
            String displayNumber,
            UUID key
    ) {

        return new Escalation(
                UUID.randomUUID(),
                orderId,
                new EscalationIdentity(
                        displayNumber,
                        EscalationReason.OUT_OF_STOCK,
                        EscalationPriority.HIGH
                ),
                key,
                CREATED_AT
        );
    }

    private static long countByKey(UUID key) {

        return jdbcClient.sql("""
                        SELECT count(*)
                        FROM orders.escalations
                        WHERE idempotency_key = :key
                        """)
                .param("key", key)
                .query(Long.class)
                .single();
    }
}
