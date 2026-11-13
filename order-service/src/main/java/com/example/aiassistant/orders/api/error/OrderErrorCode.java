package com.example.aiassistant.orders.api.error;

public enum OrderErrorCode {
    VALIDATION_ERROR,
    ORDER_NOT_FOUND,
    ORDER_NOT_ELIGIBLE,
    IDEMPOTENCY_CONFLICT
}