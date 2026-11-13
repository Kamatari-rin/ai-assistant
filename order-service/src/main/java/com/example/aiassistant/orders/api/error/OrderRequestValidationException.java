package com.example.aiassistant.orders.api.error;

public class OrderRequestValidationException
        extends RuntimeException {

    public OrderRequestValidationException() {
        super("Request validation failed.");
    }
}