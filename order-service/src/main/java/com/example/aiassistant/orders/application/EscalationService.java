
package com.example.aiassistant.orders.application;

import com.example.aiassistant.orders.application.exception.IdempotencyConflictException;
import com.example.aiassistant.orders.application.exception.OrderNotEligibleException;
import com.example.aiassistant.orders.application.exception.OrderNotFoundException;
import com.example.aiassistant.orders.application.port.EscalationRepository;
import com.example.aiassistant.orders.application.port.OrderRepository;
import com.example.aiassistant.orders.domain.Escalation;
import com.example.aiassistant.orders.domain.EscalationEligibilityPolicy;
import com.example.aiassistant.orders.domain.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class EscalationService {

    private final OrderRepository orderRepository;

    private final EscalationRepository escalationRepository;

    private final EscalationEligibilityPolicy eligibilityPolicy;

    private final Clock clock;

    public EscalationService(
            OrderRepository orderRepository,
            EscalationRepository escalationRepository,
            EscalationEligibilityPolicy eligibilityPolicy,
            Clock clock
    ) {
        this.orderRepository = orderRepository;
        this.escalationRepository = escalationRepository;
        this.eligibilityPolicy = eligibilityPolicy;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CreateEscalationResult createEscalation(
            CreateEscalationCommand command
    ) {

        Objects.requireNonNull(
                command,
                "command must not be null"
        );

        UUID key = command.idempotencyKey();

        Optional<Escalation> existing =
                escalationRepository.findByIdempotencyKey(key);

        if (existing.isPresent()) {

            return resolveExisting(
                    command,
                    existing.get()
            );
        }

        Optional<Order> lockedOrder =
                orderRepository.findByDisplayNumberForUpdate(
                        command.identity().displayNumber()
                );

        existing =
                escalationRepository.findByIdempotencyKey(key);

        if (existing.isPresent()) {

            return resolveExisting(
                    command,
                    existing.get()
            );
        }

        Order order = lockedOrder.orElseThrow(() ->
                new OrderNotFoundException(
                        command.identity().displayNumber()
                )
        );

        if (!eligibilityPolicy.isEligible(
                order,
                command.identity()
        )) {

            throw new OrderNotEligibleException();
        }

        Escalation candidate = new Escalation(
                UUID.randomUUID(),
                order.id(),
                command.identity(),
                key,
                clock.instant()
        );

        Optional<Escalation> inserted =
                escalationRepository.insertIfAbsent(candidate);

        if (inserted.isPresent()) {

            return CreateEscalationResult.newlyCreated(
                    inserted.get()
            );
        }

        Escalation winner =
                escalationRepository.findByIdempotencyKey(key)
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Escalation conflict occurred "
                                                + "without a persisted result."
                                )
                        );

        return resolveExisting(command, winner);
    }

    private CreateEscalationResult resolveExisting(
            CreateEscalationCommand command,
            Escalation existing
    ) {

        if (!existing.identity().equals(
                command.identity()
        )) {

            throw new IdempotencyConflictException();
        }

        return CreateEscalationResult.replayed(existing);
    }
}
