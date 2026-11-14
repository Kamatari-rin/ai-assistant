
package com.example.aiassistant.orders.api;

import com.example.aiassistant.orders.api.dto.CreateEscalationRequest;
import com.example.aiassistant.orders.api.dto.EscalationPriorityRequest;
import com.example.aiassistant.orders.api.dto.EscalationReasonRequest;
import com.example.aiassistant.orders.api.error.OrderRequestValidationException;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

public final class OrderRequestValidator {

    private static final Pattern UUID_PATTERN =
            Pattern.compile(
                    "[0-9a-fA-F]{8}-"
                            + "[0-9a-fA-F]{4}-"
                            + "[0-9a-fA-F]{4}-"
                            + "[0-9a-fA-F]{4}-"
                            + "[0-9a-fA-F]{12}"
            );

    private OrderRequestValidator() {
    }

    public static void validateDisplayNumber(
            String displayNumber
    ) {

        if (displayNumber == null || displayNumber.isEmpty()) {
            throw new OrderRequestValidationException();
        }
    }

    public static UUID parseIdempotencyKey(
            List<String> values
    ) {

        if (values == null || values.size() != 1) {
            throw new OrderRequestValidationException();
        }

        String value = values.getFirst();

        if (value == null
                || !UUID_PATTERN.matcher(value).matches()) {

            throw new OrderRequestValidationException();
        }

        return UUID.fromString(value);
    }

    public static CreateEscalationRequest parseEscalationRequest(
            JsonNode body
    ) {

        if (body == null || !body.isObject()) {
            throw new OrderRequestValidationException();
        }

        String reason = readRequiredString(body, "reason");

        String priority = readRequiredString(body, "priority");

        EscalationReasonRequest parsedReason = switch (reason) {

            case "OUT_OF_STOCK" ->
                    EscalationReasonRequest.OUT_OF_STOCK;

            default ->
                    throw new OrderRequestValidationException();
        };

        EscalationPriorityRequest parsedPriority = switch (priority) {

            case "HIGH" ->
                    EscalationPriorityRequest.HIGH;

            default ->
                    throw new OrderRequestValidationException();
        };

        return new CreateEscalationRequest(
                parsedReason,
                parsedPriority
        );
    }

    private static String readRequiredString(
            JsonNode body,
            String propertyName
    ) {

        JsonNode value = body.get(propertyName);

        if (value == null || !value.isString()) {
            throw new OrderRequestValidationException();
        }

        return value.stringValue();
    }
}
