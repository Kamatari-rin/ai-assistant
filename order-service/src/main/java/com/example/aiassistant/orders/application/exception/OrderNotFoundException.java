package com.example.aiassistant.orders.application.exception;

public class OrderNotFoundException extends RuntimeException {

    private final String displayNumber;

    public OrderNotFoundException(String displayNumber) {
        super("Order was not found.");
        this.displayNumber = displayNumber;
    }

    public String getDisplayNumber() {
        return displayNumber;
    }
}
