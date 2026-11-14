package com.example.aiassistant.orders.application.exception;

public class IdempotencyConflictException
        extends RuntimeException {

    public IdempotencyConflictException() {
        super(
                "Idempotency key conflicts with an existing request."
        );
    }
}