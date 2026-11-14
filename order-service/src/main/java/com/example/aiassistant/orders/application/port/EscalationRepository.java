package com.example.aiassistant.orders.application.port;

import com.example.aiassistant.orders.domain.Escalation;

import java.util.Optional;
import java.util.UUID;

public interface EscalationRepository {

    /**
     * Finds a persisted escalation by its globally unique
     * idempotency key.
     *
     * @param idempotencyKey the logical operation key
     * @return the persisted escalation, or an empty Optional
     */
    Optional<Escalation> findByIdempotencyKey(
            UUID idempotencyKey
    );

    /**
     * Inserts an escalation if its idempotency key is not
     * already present.
     *
     * <p>The operation must atomically enforce global
     * idempotency key uniqueness.
     *
     * <p>An empty result means that insertion was skipped
     * because the idempotency key already exists.
     *
     * <p>Other persistence failures must be propagated.
     *
     * @param escalation the candidate escalation
     * @return the inserted persisted escalation, or an
     *         empty Optional if the key already exists
     */
    Optional<Escalation> insertIfAbsent(
            Escalation escalation
    );
}