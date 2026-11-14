package com.example.aiassistant.orders.application.exception;

public class OrderNotEligibleException
        extends RuntimeException {

    public OrderNotEligibleException() {
        super("Order is not eligible for escalation.");
    }
}