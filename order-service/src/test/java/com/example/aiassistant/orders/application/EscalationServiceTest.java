
package com.example.aiassistant.orders.application;

import com.example.aiassistant.orders.application.exception.IdempotencyConflictException;
import com.example.aiassistant.orders.application.exception.OrderNotEligibleException;
import com.example.aiassistant.orders.application.exception.OrderNotFoundException;
import com.example.aiassistant.orders.application.port.EscalationRepository;
import com.example.aiassistant.orders.application.port.OrderRepository;
import com.example.aiassistant.orders.domain.DelayReason;
import com.example.aiassistant.orders.domain.Escalation;
import com.example.aiassistant.orders.domain.EscalationEligibilityPolicy;
import com.example.aiassistant.orders.domain.EscalationIdentity;
import com.example.aiassistant.orders.domain.EscalationPriority;
import com.example.aiassistant.orders.domain.EscalationReason;
import com.example.aiassistant.orders.domain.Order;
import com.example.aiassistant.orders.domain.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class EscalationServiceTest {

    private static final UUID MAIN_ORDER_ID =
            UUID.fromString(
                    "09b98c5b-37a5-4fa3-97c7-a016d7517760"
            );

    private static final UUID SECOND_ORDER_ID =
            UUID.fromString(
                    "09b98c5b-37a5-4fa3-97c7-a016d7517761"
            );

    private static final UUID KEY =
            UUID.fromString(
                    "f90fd699-154c-4ddb-9b9a-316ea31b2b07"
            );

    private static final Instant NOW =
            Instant.parse("2026-11-14T07:00:00.123456789Z");

    private static final EscalationIdentity IDENTITY =
            identity("12345");

    private static final CreateEscalationCommand COMMAND =
            new CreateEscalationCommand(KEY, IDENTITY);

    private static final Order MAIN_ORDER =
            new Order(
                    MAIN_ORDER_ID,
                    "12345",
                    OrderStatus.DELAYED,
                    DelayReason.OUT_OF_STOCK,
                    Instant.parse("2026-11-10T09:00:00Z"),
                    Instant.parse("2026-11-11T10:30:00Z")
            );

    private static final Escalation EXISTING =
            new Escalation(
                    UUID.fromString(
                            "5a3bb692-d257-491c-9212-d49ea1518ed1"
                    ),
                    MAIN_ORDER_ID,
                    IDENTITY,
                    KEY,
                    Instant.parse("2026-11-11T11:00:00Z")
            );

    private static final Escalation OTHER_ORDER_WINNER =
            new Escalation(
                    UUID.fromString(
                            "5a3bb692-d257-491c-9212-d49ea1518ed2"
                    ),
                    SECOND_ORDER_ID,
                    identity("12346"),
                    KEY,
                    Instant.parse("2026-11-11T12:00:00Z")
            );

    private OrderRepository orderRepository;

    private EscalationRepository escalationRepository;

    private EscalationEligibilityPolicy eligibilityPolicy;

    private EscalationService service;

    @BeforeEach
    void setUp() {

        orderRepository = mock(OrderRepository.class);

        escalationRepository =
                mock(EscalationRepository.class);

        eligibilityPolicy =
                mock(EscalationEligibilityPolicy.class);

        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

        service = new EscalationService(
                orderRepository,
                escalationRepository,
                eligibilityPolicy,
                clock
        );
    }

    @Test
    void createsNewEscalationAndReturnsPersistedResult() {

        stubEligibleOrderWithoutExistingKey();

        Instant persistedTime =
                Instant.parse(
                        "2026-11-14T07:00:00.123457Z"
                );

        when(escalationRepository.insertIfAbsent(
                any(Escalation.class)
        )).thenAnswer(invocation -> {

            Escalation candidate =
                    invocation.getArgument(0);

            return Optional.of(new Escalation(
                    candidate.id(),
                    candidate.orderId(),
                    candidate.identity(),
                    candidate.idempotencyKey(),
                    persistedTime
            ));
        });

        CreateEscalationResult result =
                service.createEscalation(COMMAND);

        ArgumentCaptor<Escalation> captor =
                ArgumentCaptor.forClass(Escalation.class);

        var calls = inOrder(
                escalationRepository,
                orderRepository,
                eligibilityPolicy
        );

        calls.verify(escalationRepository)
                .findByIdempotencyKey(KEY);

        calls.verify(orderRepository)
                .findByDisplayNumberForUpdate("12345");

        calls.verify(escalationRepository)
                .findByIdempotencyKey(KEY);

        calls.verify(eligibilityPolicy)
                .isEligible(MAIN_ORDER, IDENTITY);

        calls.verify(escalationRepository)
                .insertIfAbsent(captor.capture());

        calls.verifyNoMoreInteractions();

        Escalation candidate = captor.getValue();

        assertThat(candidate.id()).isNotNull();

        assertThat(candidate.id().version())
                .isEqualTo(4);

        assertThat(candidate.orderId())
                .isEqualTo(MAIN_ORDER_ID);

        assertThat(candidate.identity())
                .isEqualTo(IDENTITY);

        assertThat(candidate.idempotencyKey())
                .isEqualTo(KEY);

        assertThat(candidate.createdAt())
                .isEqualTo(NOW);

        assertThat(result.created()).isTrue();

        assertThat(result.escalation().id())
                .isEqualTo(candidate.id());

        assertThat(result.escalation().orderId())
                .isEqualTo(MAIN_ORDER_ID);

        assertThat(result.escalation().identity())
                .isEqualTo(IDENTITY);

        assertThat(result.escalation().idempotencyKey())
                .isEqualTo(KEY);

        assertThat(result.escalation().createdAt())
                .isEqualTo(persistedTime);
    }

    @Test
    void replaysExistingResultWithoutCheckingOrderState() {

        when(escalationRepository.findByIdempotencyKey(KEY))
                .thenReturn(Optional.of(EXISTING));

        CreateEscalationResult result =
                service.createEscalation(COMMAND);

        assertThat(result.created()).isFalse();

        assertThat(result.escalation())
                .isSameAs(EXISTING);

        verify(escalationRepository)
                .findByIdempotencyKey(KEY);

        verifyNoMoreInteractions(escalationRepository);

        verifyNoInteractions(
                orderRepository,
                eligibilityPolicy
        );
    }

    @Test
    void rejectsExistingKeyForDifferentOrderBeforeOrderLookup() {

        when(escalationRepository.findByIdempotencyKey(KEY))
                .thenReturn(Optional.of(EXISTING));

        CreateEscalationCommand conflictingCommand =
                new CreateEscalationCommand(
                        KEY,
                        identity("12346")
                );

        assertThatThrownBy(() ->
                service.createEscalation(
                        conflictingCommand
                )
        )
                .isInstanceOf(
                        IdempotencyConflictException.class
                )
                .hasMessage(
                        "Idempotency key conflicts "
                                + "with an existing request."
                );

        verify(escalationRepository)
                .findByIdempotencyKey(KEY);

        verifyNoMoreInteractions(escalationRepository);

        verifyNoInteractions(
                orderRepository,
                eligibilityPolicy
        );
    }

    @Test
    void existingKeyConflictTakesPrecedenceOverMissingOrder() {

        when(escalationRepository.findByIdempotencyKey(KEY))
                .thenReturn(Optional.of(EXISTING));

        CreateEscalationCommand conflictingCommand =
                new CreateEscalationCommand(
                        KEY,
                        identity("99999")
                );

        assertThatThrownBy(() ->
                service.createEscalation(
                        conflictingCommand
                )
        ).isInstanceOf(
                IdempotencyConflictException.class
        );

        verify(escalationRepository)
                .findByIdempotencyKey(KEY);

        verifyNoMoreInteractions(escalationRepository);

        verifyNoInteractions(
                orderRepository,
                eligibilityPolicy
        );
    }

    @Test
    void replaysResultFoundAfterAcquiringOrderLock() {

        when(escalationRepository.findByIdempotencyKey(KEY))
                .thenReturn(
                        Optional.empty(),
                        Optional.of(EXISTING)
                );

        when(orderRepository
                .findByDisplayNumberForUpdate("12345"))
                .thenReturn(Optional.of(MAIN_ORDER));

        CreateEscalationResult result =
                service.createEscalation(COMMAND);

        assertThat(result.created()).isFalse();

        assertThat(result.escalation())
                .isSameAs(EXISTING);

        verifyLookups("12345");

        verifyNoInteractions(eligibilityPolicy);
    }

    @Test
    void detectsConflictFoundAfterAcquiringOrderLock() {

        when(escalationRepository.findByIdempotencyKey(KEY))
                .thenReturn(
                        Optional.empty(),
                        Optional.of(OTHER_ORDER_WINNER)
                );

        when(orderRepository
                .findByDisplayNumberForUpdate("12345"))
                .thenReturn(Optional.of(MAIN_ORDER));

        assertThatThrownBy(() ->
                service.createEscalation(COMMAND)
        ).isInstanceOf(
                IdempotencyConflictException.class
        );

        verifyLookups("12345");

        verifyNoInteractions(eligibilityPolicy);
    }

    @Test
    void rechecksKeyBeforeRejectingMissingOrder() {

        when(escalationRepository.findByIdempotencyKey(KEY))
                .thenReturn(
                        Optional.empty(),
                        Optional.of(EXISTING)
                );

        when(orderRepository
                .findByDisplayNumberForUpdate("99999"))
                .thenReturn(Optional.empty());

        CreateEscalationCommand conflictingCommand =
                new CreateEscalationCommand(
                        KEY,
                        identity("99999")
                );

        assertThatThrownBy(() ->
                service.createEscalation(
                        conflictingCommand
                )
        ).isInstanceOf(
                IdempotencyConflictException.class
        );

        verifyLookups("99999");

        verifyNoInteractions(eligibilityPolicy);
    }

    @Test
    void rejectsMissingOrderWhenKeyDoesNotExist() {

        when(escalationRepository.findByIdempotencyKey(KEY))
                .thenReturn(Optional.empty());

        when(orderRepository
                .findByDisplayNumberForUpdate("99999"))
                .thenReturn(Optional.empty());

        CreateEscalationCommand command =
                new CreateEscalationCommand(
                        KEY,
                        identity("99999")
                );

        assertThatThrownBy(() ->
                service.createEscalation(command)
        )
                .isInstanceOfSatisfying(
                        OrderNotFoundException.class,
                        exception ->
                                assertThat(
                                        exception.getDisplayNumber()
                                ).isEqualTo("99999")
                );

        verifyLookups("99999");

        verifyNoInteractions(eligibilityPolicy);
    }

    @Test
    void rejectsIneligibleOrderWithoutInsertingEscalation() {

        stubOrderWithoutExistingKey();

        when(eligibilityPolicy.isEligible(
                MAIN_ORDER,
                IDENTITY
        )).thenReturn(false);

        assertThatThrownBy(() ->
                service.createEscalation(COMMAND)
        )
                .isInstanceOf(
                        OrderNotEligibleException.class
                )
                .hasMessage(
                        "Order is not eligible for escalation."
                );

        verifyLookups("12345");

        verify(eligibilityPolicy)
                .isEligible(MAIN_ORDER, IDENTITY);

        verifyNoMoreInteractions(eligibilityPolicy);
    }

    @Test
    void replaysPersistedWinnerAfterLosingInsertRace() {

        stubEligibleOrderWithoutExistingKey();

        when(escalationRepository.findByIdempotencyKey(KEY))
                .thenReturn(
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(EXISTING)
                );

        when(escalationRepository.insertIfAbsent(
                any(Escalation.class)
        )).thenReturn(Optional.empty());

        CreateEscalationResult result =
                service.createEscalation(COMMAND);

        assertThat(result.created()).isFalse();

        assertThat(result.escalation())
                .isSameAs(EXISTING);

        verifyInsertRaceSequence();
    }

    @Test
    void detectsDifferentIdentityAfterLosingInsertRace() {

        stubEligibleOrderWithoutExistingKey();

        when(escalationRepository.findByIdempotencyKey(KEY))
                .thenReturn(
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(OTHER_ORDER_WINNER)
                );

        when(escalationRepository.insertIfAbsent(
                any(Escalation.class)
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.createEscalation(COMMAND)
        ).isInstanceOf(
                IdempotencyConflictException.class
        );

        verifyInsertRaceSequence();
    }

    @Test
    void failsWhenConflictingInsertHasNoPersistedWinner() {

        stubEligibleOrderWithoutExistingKey();

        when(escalationRepository.insertIfAbsent(
                any(Escalation.class)
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.createEscalation(COMMAND)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(
                        "Escalation conflict occurred "
                                + "without a persisted result."
                );

        verifyInsertRaceSequence();
    }

    @Test
    void propagatesInitialKeyLookupFailure() {

        var failure =
                new DataAccessResourceFailureException(
                        "Synthetic lookup failure."
                );

        when(escalationRepository.findByIdempotencyKey(KEY))
                .thenThrow(failure);

        assertThatThrownBy(() ->
                service.createEscalation(COMMAND)
        ).isSameAs(failure);

        verify(escalationRepository)
                .findByIdempotencyKey(KEY);

        verifyNoMoreInteractions(escalationRepository);

        verifyNoInteractions(
                orderRepository,
                eligibilityPolicy
        );
    }

    @Test
    void propagatesOrderLockFailure() {

        var failure =
                new DataAccessResourceFailureException(
                        "Synthetic row lock failure."
                );

        when(escalationRepository.findByIdempotencyKey(KEY))
                .thenReturn(Optional.empty());

        when(orderRepository
                .findByDisplayNumberForUpdate("12345"))
                .thenThrow(failure);

        assertThatThrownBy(() ->
                service.createEscalation(COMMAND)
        ).isSameAs(failure);

        var calls = inOrder(
                escalationRepository,
                orderRepository
        );

        calls.verify(escalationRepository)
                .findByIdempotencyKey(KEY);

        calls.verify(orderRepository)
                .findByDisplayNumberForUpdate("12345");

        calls.verifyNoMoreInteractions();

        verifyNoInteractions(eligibilityPolicy);
    }

    @Test
    void propagatesInsertFailureWithoutAttemptingReplay() {

        stubEligibleOrderWithoutExistingKey();

        var failure =
                new DataAccessResourceFailureException(
                        "Synthetic insert failure."
                );

        when(escalationRepository.insertIfAbsent(
                any(Escalation.class)
        )).thenThrow(failure);

        assertThatThrownBy(() ->
                service.createEscalation(COMMAND)
        ).isSameAs(failure);

        var calls = inOrder(
                escalationRepository,
                orderRepository,
                eligibilityPolicy
        );

        calls.verify(escalationRepository)
                .findByIdempotencyKey(KEY);

        calls.verify(orderRepository)
                .findByDisplayNumberForUpdate("12345");

        calls.verify(escalationRepository)
                .findByIdempotencyKey(KEY);

        calls.verify(eligibilityPolicy)
                .isEligible(MAIN_ORDER, IDENTITY);

        calls.verify(escalationRepository)
                .insertIfAbsent(any(Escalation.class));

        calls.verifyNoMoreInteractions();
    }

    @Test
    void rejectsNullCommandWithoutRepositoryCalls() {

        assertThatThrownBy(() ->
                service.createEscalation(null)
        )
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");

        verifyNoInteractions(
                orderRepository,
                escalationRepository,
                eligibilityPolicy
        );
    }

    private void stubOrderWithoutExistingKey() {

        when(escalationRepository.findByIdempotencyKey(KEY))
                .thenReturn(Optional.empty());

        when(orderRepository
                .findByDisplayNumberForUpdate("12345"))
                .thenReturn(Optional.of(MAIN_ORDER));
    }

    private void stubEligibleOrderWithoutExistingKey() {

        stubOrderWithoutExistingKey();

        when(eligibilityPolicy.isEligible(
                MAIN_ORDER,
                IDENTITY
        )).thenReturn(true);
    }

    private void verifyLookups(String displayNumber) {

        var calls = inOrder(
                escalationRepository,
                orderRepository
        );

        calls.verify(escalationRepository)
                .findByIdempotencyKey(KEY);

        calls.verify(orderRepository)
                .findByDisplayNumberForUpdate(displayNumber);

        calls.verify(escalationRepository)
                .findByIdempotencyKey(KEY);

        calls.verifyNoMoreInteractions();
    }

    private void verifyInsertRaceSequence() {

        var calls = inOrder(
                escalationRepository,
                orderRepository,
                eligibilityPolicy
        );

        calls.verify(escalationRepository)
                .findByIdempotencyKey(KEY);

        calls.verify(orderRepository)
                .findByDisplayNumberForUpdate("12345");

        calls.verify(escalationRepository)
                .findByIdempotencyKey(KEY);

        calls.verify(eligibilityPolicy)
                .isEligible(MAIN_ORDER, IDENTITY);

        calls.verify(escalationRepository)
                .insertIfAbsent(any(Escalation.class));

        calls.verify(escalationRepository)
                .findByIdempotencyKey(KEY);

        calls.verifyNoMoreInteractions();
    }

    private static EscalationIdentity identity(
            String displayNumber
    ) {

        return new EscalationIdentity(
                displayNumber,
                EscalationReason.OUT_OF_STOCK,
                EscalationPriority.HIGH
        );
    }
}
