package com.example.aiassistant.orders.api;

import com.example.aiassistant.orders.api.error.OrderRequestValidationException;
import com.example.aiassistant.orders.application.OrderQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class OrderRequestValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "12345",
            "0012345",
            "ORDER-A",
            " 12345 ",
            " "
    })
    void acceptsNonEmptyDisplayNumbers(
            String displayNumber
    ) {

        assertThatCode(() ->
                OrderRequestValidator.validateDisplayNumber(
                        displayNumber
                )
        ).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void rejectsMissingOrEmptyDisplayNumber(
            String displayNumber
    ) {

        assertThatThrownBy(() ->
                OrderRequestValidator.validateDisplayNumber(
                        displayNumber
                )
        )
                .isInstanceOf(
                        OrderRequestValidationException.class
                )
                .hasMessage("Request validation failed.");
    }

    @Test
    void orderControllerRejectsInvalidNumberBeforeCallingService() {

        OrderQueryService service =
                mock(OrderQueryService.class);

        OrderController controller = new OrderController(
                service,
                new OrderApiMapper()
        );

        assertThatThrownBy(() ->
                controller.getOrder("")
        )
                .isInstanceOf(
                        OrderRequestValidationException.class
                );

        verifyNoInteractions(service);
    }

    @Test
    void eventsControllerRejectsInvalidNumberBeforeCallingService() {

        OrderQueryService service =
                mock(OrderQueryService.class);

        OrderController controller = new OrderController(
                service,
                new OrderApiMapper()
        );

        assertThatThrownBy(() ->
                controller.getOrderEvents("")
        )
                .isInstanceOf(
                        OrderRequestValidationException.class
                );

        verifyNoInteractions(service);
    }
}