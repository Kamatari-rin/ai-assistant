
package com.example.aiassistant.orders.api;

import com.example.aiassistant.orders.api.error.OrderErrorCode;
import com.example.aiassistant.orders.api.error.OrderRequestValidationException;
import com.example.aiassistant.orders.application.exception.IdempotencyConflictException;
import com.example.aiassistant.orders.application.exception.OrderNotEligibleException;
import com.example.aiassistant.orders.application.exception.OrderNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.UUID;

@RestControllerAdvice(
        assignableTypes = {
                OrderController.class,
                EscalationController.class
        }
)
public class OrderExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(OrderExceptionHandler.class);

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleOrderNotFound(
            OrderNotFoundException exception,
            HttpServletRequest request
    ) {

        return createProblem(
                HttpStatus.NOT_FOUND,
                "Order was not found.",
                OrderErrorCode.ORDER_NOT_FOUND,
                request,
                UUID.randomUUID()
        );
    }

    @ExceptionHandler(OrderRequestValidationException.class)
    public ResponseEntity<ProblemDetail> handleValidationError(
            OrderRequestValidationException exception,
            HttpServletRequest request
    ) {

        return validationProblem(request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadableBody(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {

        return validationProblem(request);
    }

    @ExceptionHandler(OrderNotEligibleException.class)
    public ResponseEntity<ProblemDetail> handleOrderNotEligible(
            OrderNotEligibleException exception,
            HttpServletRequest request
    ) {

        return createProblem(
                HttpStatus.CONFLICT,
                "Order is not eligible for escalation.",
                OrderErrorCode.ORDER_NOT_ELIGIBLE,
                request,
                UUID.randomUUID()
        );
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ProblemDetail> handleIdempotencyConflict(
            IdempotencyConflictException exception,
            HttpServletRequest request
    ) {

        return createProblem(
                HttpStatus.CONFLICT,
                "Idempotency key conflicts with an existing request.",
                OrderErrorCode.IDEMPOTENCY_CONFLICT,
                request,
                UUID.randomUUID()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpectedError(
            Exception exception,
            HttpServletRequest request
    ) {

        // Preserve framework-defined HTTP errors instead of
        // converting client-side errors into HTTP 500.
        if (exception instanceof ErrorResponse errorResponse
                && errorResponse.getStatusCode()
                .is4xxClientError()) {

            if (errorResponse.getStatusCode().value() == 400) {

                return validationProblem(request);
            }

            return ResponseEntity
                    .status(errorResponse.getStatusCode())
                    .headers(errorResponse.getHeaders())
                    .build();
        }

        UUID requestId = UUID.randomUUID();

        log.error(
                "Unexpected order API failure. requestId={}",
                requestId,
                exception
        );

        return createProblem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected server error occurred.",
                null,
                request,
                requestId
        );
    }

    private ResponseEntity<ProblemDetail> validationProblem(
            HttpServletRequest request
    ) {

        return createProblem(
                HttpStatus.BAD_REQUEST,
                "Request validation failed.",
                OrderErrorCode.VALIDATION_ERROR,
                request,
                UUID.randomUUID()
        );
    }

    private ResponseEntity<ProblemDetail> createProblem(
            HttpStatus status,
            String detail,
            OrderErrorCode code,
            HttpServletRequest request,
            UUID requestId
    ) {

        ProblemDetail problem = ProblemDetail.forStatus(status);

        problem.setType(URI.create("about:blank"));
        problem.setTitle(status.getReasonPhrase());
        problem.setDetail(detail);
        problem.setInstance(resolveInstance(request));

        if (code != null) {

            problem.setProperty("code", code.name());
        }

        problem.setProperty(
                "requestId",
                requestId.toString()
        );

        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private URI resolveInstance(
            HttpServletRequest request
    ) {

        try {

            return URI.create(request.getRequestURI());

        } catch (IllegalArgumentException exception) {

            return URI.create("/api/v1/orders");
        }
    }
}
