
package com.example.aiassistant.orders.api;

import com.example.aiassistant.orders.api.dto.CreateEscalationRequest;
import com.example.aiassistant.orders.api.dto.EscalationResponse;
import com.example.aiassistant.orders.application.CreateEscalationCommand;
import com.example.aiassistant.orders.application.CreateEscalationResult;
import com.example.aiassistant.orders.application.EscalationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.util.Collections;
import java.util.UUID;

@RestController
@RequestMapping(
        path = "/api/v1/orders",
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class EscalationController {

    private static final String IDEMPOTENCY_KEY_HEADER =
            "Idempotency-Key";

    private final EscalationService escalationService;

    private final EscalationApiMapper escalationApiMapper;

    public EscalationController(
            EscalationService escalationService,
            EscalationApiMapper escalationApiMapper
    ) {
        this.escalationService = escalationService;
        this.escalationApiMapper = escalationApiMapper;
    }

    @PostMapping(
            path = "/{displayNumber}/escalations",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<EscalationResponse> createEscalation(
            @PathVariable("displayNumber")
            String displayNumber,

            @RequestBody
            JsonNode body,

            HttpServletRequest httpRequest
    ) {

        OrderRequestValidator.validateDisplayNumber(
                displayNumber
        );

        UUID key =
                OrderRequestValidator.parseIdempotencyKey(
                        Collections.list(
                                httpRequest.getHeaders(
                                        IDEMPOTENCY_KEY_HEADER
                                )
                        )
                );

        CreateEscalationRequest request =
                OrderRequestValidator.parseEscalationRequest(
                        body
                );

        CreateEscalationCommand command =
                escalationApiMapper.toCommand(
                        displayNumber,
                        key,
                        request
                );

        CreateEscalationResult result =
                escalationService.createEscalation(command);

        EscalationResponse response =
                escalationApiMapper.toResponse(
                        result.escalation()
                );

        HttpStatus status = result.created()
                ? HttpStatus.CREATED
                : HttpStatus.OK;

        return ResponseEntity.status(status)
                .body(response);
    }
}
