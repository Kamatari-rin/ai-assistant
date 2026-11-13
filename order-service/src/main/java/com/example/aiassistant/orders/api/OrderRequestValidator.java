package com.example.aiassistant.orders.api;

import com.example.aiassistant.orders.api.error.OrderRequestValidationException;

public final class OrderRequestValidator {

    private OrderRequestValidator() {
    }

    public static void validateDisplayNumber(
            String displayNumber
    ) {

        if (displayNumber == null || displayNumber.isEmpty()) {
            throw new OrderRequestValidationException();
        }
    }
}