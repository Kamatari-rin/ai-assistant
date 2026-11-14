package com.example.aiassistant.orders.domain;

import java.util.Objects;

public class EscalationEligibilityPolicy {

    public boolean isEligible(
            Order order,
            EscalationIdentity identity
    ) {

        Objects.requireNonNull(
                order,
                "order must not be null"
        );

        Objects.requireNonNull(
                identity,
                "identity must not be null"
        );

        return order.status() == OrderStatus.DELAYED
                && order.delayReason() == DelayReason.OUT_OF_STOCK
                && identity.reason() == EscalationReason.OUT_OF_STOCK
                && identity.priority() == EscalationPriority.HIGH;
    }
}