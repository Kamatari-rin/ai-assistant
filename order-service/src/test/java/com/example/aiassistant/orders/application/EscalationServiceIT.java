
package com.example.aiassistant.orders.application;

import com.example.aiassistant.orders.application.exception.IdempotencyConflictException;
import com.example.aiassistant.orders.application.exception.OrderNotEligibleException;
import com.example.aiassistant.orders.application.exception.OrderNotFoundException;
import com.example.aiassistant.orders.application.port.EscalationRepository;
import com.example.aiassistant.orders.application.port.OrderRepository;
import com.example.aiassistant.orders.domain.Escalation;
import com.example.aiassistant.orders.domain.EscalationEligibilityPolicy;
import com.example.aiassistant.orders.domain.EscalationIdentity;
import com.example.aiassistant.orders.domain.EscalationPriority;
import com.example.aiassistant.orders.domain.EscalationReason;
import com.example.aiassistant.orders.domain.Order;
import com.example.aiassistant.orders.domain.OrderEvent;
import com.example.aiassistant.orders.support.OrderPostgresTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "management.tracing.export.otlp.enabled=false",
                "spring.datasource.hikari.connection-init-sql="
                        + "SET statement_timeout = '20s'"
        }
)
@ActiveProfiles("integration")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class EscalationServiceIT {

    private static final Duration BLOCKING_TIMEOUT =
            Duration.ofSeconds(8);

    private static final long FUTURE_TIMEOUT_SECONDS = 25;

    private static PostgreSQLContainer postgres;

    @Autowired
    private EscalationService service;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private EscalationRepository escalationRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoSpyBean
    private EscalationEligibilityPolicy eligibilityPolicy;

    @DynamicPropertySource
    static void registerDatabaseProperties(
            DynamicPropertyRegistry registry
    ) {

        postgres = OrderPostgresTestSupport.newContainer();

        try {
            postgres.start();

            OrderPostgresTestSupport.initializeDatabase(
                    postgres
            );

            registry.add(
                    "spring.datasource.url",
                    postgres::getJdbcUrl
            );

            registry.add(
                    "spring.datasource.username",
                    () -> OrderPostgresTestSupport.ORDER_USERNAME
            );

            registry.add(
                    "spring.datasource.password",
                    () -> OrderPostgresTestSupport.ORDER_PASSWORD
            );

        } catch (Exception | Error exception) {

            try {
                postgres.close();
            } catch (Exception cleanupException) {
                exception.addSuppressed(cleanupException);
            }

            postgres = null;

            throw new IllegalStateException(
                    "Could not initialize integration database.",
                    exception
            );
        }
    }

    @AfterAll
    static void tearDownDatabase() {

        if (postgres != null) {
            postgres.close();
        }
    }

    @BeforeEach
    void clearEscalations() {

        jdbcClient.sql("""
                DELETE FROM orders.escalations
                """).update();

        assertThat(countEscalations()).isZero();
    }

    @Test
    void createsEscalationInReadCommittedTransaction() {

        CreateEscalationCommand command =
                command("12345", UUID.randomUUID());

        Order originalOrder = findOrder("12345");

        List<OrderEvent> originalEvents =
                orderRepository.findEventsByOrderId(
                        originalOrder.id()
                );

        assertThat(AopUtils.isAopProxy(service))
                .isTrue();

        assertThat(
                TransactionSynchronizationManager
                        .isActualTransactionActive()
        ).isFalse();

        doAnswer(invocation -> {

            assertThat(
                    TransactionSynchronizationManager
                            .isActualTransactionActive()
            ).isTrue();

            assertThat(
                    TransactionSynchronizationManager
                            .isCurrentTransactionReadOnly()
            ).isFalse();

            assertThat(
                    jdbcClient.sql("SHOW transaction_isolation")
                            .query(String.class)
                            .single()
            ).isEqualTo("read committed");

            return invocation.callRealMethod();

        }).when(eligibilityPolicy)
                .isEligible(
                        originalOrder,
                        command.identity()
                );

        CreateEscalationResult result =
                service.createEscalation(command);

        assertThat(result.created()).isTrue();

        assertThat(result.escalation().orderId())
                .isEqualTo(originalOrder.id());

        assertThat(result.escalation().identity())
                .isEqualTo(command.identity());

        assertThat(result.escalation().idempotencyKey())
                .isEqualTo(command.idempotencyKey());

        assertThat(
                TransactionSynchronizationManager
                        .isActualTransactionActive()
        ).isFalse();

        assertThat(
                escalationRepository.findByIdempotencyKey(
                        command.idempotencyKey()
                )
        ).contains(result.escalation());

        assertThat(countEscalations()).isEqualTo(1L);

        assertThat(findOrder("12345"))
                .isEqualTo(originalOrder);

        assertThat(
                orderRepository.findEventsByOrderId(
                        originalOrder.id()
                )
        ).containsExactlyElementsOf(originalEvents);
    }

    @Test
    void replaysPreviouslyCommittedEscalation() {

        CreateEscalationCommand command =
                command("12345", UUID.randomUUID());

        CreateEscalationResult first =
                service.createEscalation(command);

        CreateEscalationResult replay =
                service.createEscalation(command);

        assertThat(first.created()).isTrue();

        assertThat(replay.created()).isFalse();

        assertThat(replay.escalation())
                .isEqualTo(first.escalation());

        assertThat(countEscalations()).isEqualTo(1L);

        assertThat(
                escalationRepository.findByIdempotencyKey(
                        command.idempotencyKey()
                )
        ).contains(first.escalation());
    }

    @Test
    void rejectsCommittedKeyForDifferentOrder() {

        UUID key = UUID.randomUUID();

        CreateEscalationResult first =
                service.createEscalation(
                        command("12345", key)
                );

        assertThatThrownBy(() ->
                service.createEscalation(
                        command("12346", key)
                )
        ).isInstanceOf(
                IdempotencyConflictException.class
        );

        // An already committed key takes precedence
        // over a missing target order.
        assertThatThrownBy(() ->
                service.createEscalation(
                        command("99999", key)
                )
        ).isInstanceOf(
                IdempotencyConflictException.class
        );

        assertThat(countEscalations()).isEqualTo(1L);

        assertThat(
                escalationRepository.findByIdempotencyKey(key)
        ).contains(first.escalation());
    }

    @Test
    void rejectsMissingOrderWithoutPersistingEscalation() {

        CreateEscalationCommand command =
                command("99999", UUID.randomUUID());

        assertThatThrownBy(() ->
                service.createEscalation(command)
        ).isInstanceOf(
                OrderNotFoundException.class
        );

        assertThat(countEscalations()).isZero();

        assertThat(
                escalationRepository.findByIdempotencyKey(
                        command.idempotencyKey()
                )
        ).isEmpty();
    }

    @Test
    void createsIndependentEscalationsForDistinctKeys() {

        CreateEscalationResult first =
                service.createEscalation(
                        command("12345", UUID.randomUUID())
                );

        CreateEscalationResult second =
                service.createEscalation(
                        command("12345", UUID.randomUUID())
                );

        assertThat(first.created()).isTrue();

        assertThat(second.created()).isTrue();

        assertThat(first.escalation().id())
                .isNotEqualTo(second.escalation().id());

        assertThat(first.escalation().orderId())
                .isEqualTo(second.escalation().orderId());

        assertThat(countEscalations()).isEqualTo(2L);
    }

    @Test
    void replaysCommittedResultEvenWhenPolicyWouldRejectOrder() {

        CreateEscalationCommand command =
                command("12345", UUID.randomUUID());

        Order order = findOrder("12345");

        CreateEscalationResult first =
                service.createEscalation(command);

        doReturn(false)
                .when(eligibilityPolicy)
                .isEligible(order, command.identity());

        clearInvocations(eligibilityPolicy);

        CreateEscalationResult replay =
                service.createEscalation(command);

        assertThat(replay.created()).isFalse();

        assertThat(replay.escalation())
                .isEqualTo(first.escalation());

        verifyNoInteractions(eligibilityPolicy);

        assertThat(countEscalations()).isEqualTo(1L);
    }

    @Test
    void rejectsIneligibleOrderWithoutPersistingEscalation() {

        CreateEscalationCommand command =
                command("12345", UUID.randomUUID());

        Order order = findOrder("12345");

        doReturn(false)
                .when(eligibilityPolicy)
                .isEligible(order, command.identity());

        assertThatThrownBy(() ->
                service.createEscalation(command)
        ).isInstanceOf(
                OrderNotEligibleException.class
        );

        assertThat(countEscalations()).isZero();

        assertThat(
                escalationRepository.findByIdempotencyKey(
                        command.idempotencyKey()
                )
        ).isEmpty();
    }

    @Test
    void rollsBackInsertedEscalationWhenCommitIsAborted() {

        CreateEscalationCommand command =
                command("12345", UUID.randomUUID());

        Order order = findOrder("12345");

        IllegalStateException failure =
                new IllegalStateException(
                        "Synthetic failure before commit."
                );

        doAnswer(invocation -> {

            TransactionSynchronizationManager
                    .registerSynchronization(
                            new TransactionSynchronization() {

                                @Override
                                public void beforeCommit(
                                        boolean readOnly
                                ) {

                                    // INSERT has already happened
                                    // inside this transaction.
                                    assertThat(
                                            escalationRepository
                                                    .findByIdempotencyKey(
                                                            command.idempotencyKey()
                                                    )
                                    ).isPresent();

                                    throw failure;
                                }
                            }
                    );

            return invocation.callRealMethod();

        }).when(eligibilityPolicy)
                .isEligible(order, command.identity());

        assertThatThrownBy(() ->
                service.createEscalation(command)
        ).isSameAs(failure);

        assertThat(
                TransactionSynchronizationManager
                        .isActualTransactionActive()
        ).isFalse();

        assertThat(countEscalations()).isZero();

        assertThat(
                escalationRepository.findByIdempotencyKey(
                        command.idempotencyKey()
                )
        ).isEmpty();
    }

    @Test
    void concurrentSameOrderRequestsCreateOneEscalationAndReplay()
            throws Exception {

        UUID key = UUID.randomUUID();

        CreateEscalationCommand command =
                command("12345", key);

        ExecutorService executor =
                Executors.newSingleThreadExecutor();

        try {
            PendingRace race = startRace(
                    executor,
                    command,
                    command,
                    "%FOR UPDATE%"
            );

            CreateEscalationResult replay =
                    race.competitor().get(
                            FUTURE_TIMEOUT_SECONDS,
                            TimeUnit.SECONDS
                    );

            assertThat(race.first().created())
                    .isTrue();

            assertThat(replay.created())
                    .isFalse();

            assertThat(replay.escalation())
                    .isEqualTo(
                            race.first().escalation()
                    );

            assertThat(countEscalations())
                    .isEqualTo(1L);

            assertThat(
                    escalationRepository.findByIdempotencyKey(key)
            ).contains(race.first().escalation());

        } finally {
            stopExecutor(executor);
        }
    }

    @Test
    void concurrentDifferentOrdersWithSameKeyCreateOneEscalation()
            throws Exception {

        UUID key = UUID.randomUUID();

        CreateEscalationCommand firstCommand =
                command("12345", key);

        CreateEscalationCommand competingCommand =
                command("12346", key);

        ExecutorService executor =
                Executors.newSingleThreadExecutor();

        try {
            PendingRace race = startRace(
                    executor,
                    firstCommand,
                    competingCommand,
                    "%INSERT INTO orders.escalations%"
            );

            assertThat(race.first().created())
                    .isTrue();

            assertThatThrownBy(() ->
                    race.competitor().get(
                            FUTURE_TIMEOUT_SECONDS,
                            TimeUnit.SECONDS
                    )
            )
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(
                            IdempotencyConflictException.class
                    );

            assertThat(countEscalations())
                    .isEqualTo(1L);

            assertThat(
                    escalationRepository.findByIdempotencyKey(key)
            ).contains(race.first().escalation());

            assertThat(
                    race.first().escalation()
                            .identity()
                            .displayNumber()
            ).isEqualTo("12345");

        } finally {
            stopExecutor(executor);
        }
    }

    private PendingRace startRace(
            ExecutorService executor,
            CreateEscalationCommand firstCommand,
            CreateEscalationCommand competingCommand,
            String expectedBlockedQuery
    ) {

        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);

        transaction.setIsolationLevel(
                TransactionDefinition.ISOLATION_READ_COMMITTED
        );

        return Objects.requireNonNull(
                transaction.execute(status -> {

                    int blockerPid =
                            jdbcClient.sql("""
                                    SELECT pg_backend_pid()
                                    """)
                                    .query(Integer.class)
                                    .single();

                    CreateEscalationResult first =
                            service.createEscalation(
                                    firstCommand
                            );

                    assertThat(first.created())
                            .isTrue();

                    Future<CreateEscalationResult> competitor =
                            executor.submit(() ->
                                    service.createEscalation(
                                            competingCommand
                                    )
                            );

                    awaitBlockedQuery(
                            blockerPid,
                            expectedBlockedQuery
                    );

                    assertThat(competitor.isDone())
                            .isFalse();

                    // Returning from this callback commits
                    // the outer transaction, releasing the
                    // competing PostgreSQL session.
                    return new PendingRace(
                            first,
                            competitor
                    );
                })
        );
    }

    private static void awaitBlockedQuery(
            int blockerPid,
            String queryPattern
    ) {

        long deadline = System.nanoTime()
                + BLOCKING_TIMEOUT.toNanos();

        try (
                Connection observer =
                        OrderPostgresTestSupport
                                .openOrderConnection(postgres);

                PreparedStatement statement =
                        observer.prepareStatement("""
                                SELECT count(*)
                                FROM pg_stat_activity
                                WHERE datname = current_database()
                                  AND pid <> pg_backend_pid()
                                  AND ? = ANY(pg_blocking_pids(pid))
                                  AND query ILIKE ?
                                """)
        ) {

            observer.setAutoCommit(true);

            statement.setQueryTimeout(3);

            statement.setInt(1, blockerPid);

            statement.setString(2, queryPattern);

            while (System.nanoTime() < deadline) {

                try (
                        ResultSet resultSet =
                                statement.executeQuery()
                ) {

                    assertThat(resultSet.next())
                            .isTrue();

                    if (resultSet.getLong(1) > 0) {
                        return;
                    }
                }

                TimeUnit.MILLISECONDS.sleep(50);
            }

        } catch (SQLException exception) {

            throw new AssertionError(
                    "Could not inspect PostgreSQL lock state.",
                    exception
            );

        } catch (InterruptedException exception) {

            Thread.currentThread().interrupt();

            throw new AssertionError(
                    "Interrupted while observing PostgreSQL locks.",
                    exception
            );
        }

        throw new AssertionError(
                "Expected PostgreSQL blocking was not observed: "
                        + queryPattern
                        + ", blockerPid="
                        + blockerPid
        );
    }

    private static void stopExecutor(
            ExecutorService executor
    ) throws InterruptedException {

        executor.shutdownNow();

        if (!executor.awaitTermination(
                FUTURE_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
        )) {

            throw new AssertionError(
                    "Concurrent test worker did not terminate."
            );
        }
    }

    private Order findOrder(String displayNumber) {

        return orderRepository
                .findByDisplayNumber(displayNumber)
                .orElseThrow();
    }

    private long countEscalations() {

        return jdbcClient.sql("""
                        SELECT count(*)
                        FROM orders.escalations
                        """)
                .query(Long.class)
                .single();
    }

    private static CreateEscalationCommand command(
            String displayNumber,
            UUID key
    ) {

        return new CreateEscalationCommand(
                key,
                new EscalationIdentity(
                        displayNumber,
                        EscalationReason.OUT_OF_STOCK,
                        EscalationPriority.HIGH
                )
        );
    }

    private record PendingRace(
            CreateEscalationResult first,
            Future<CreateEscalationResult> competitor
    ) {
    }
}
