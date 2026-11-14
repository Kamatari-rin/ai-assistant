
package com.example.aiassistant.orders.api;

import com.example.aiassistant.orders.application.CreateEscalationCommand;
import com.example.aiassistant.orders.application.CreateEscalationResult;
import com.example.aiassistant.orders.application.EscalationService;
import com.example.aiassistant.orders.application.exception.IdempotencyConflictException;
import com.example.aiassistant.orders.application.exception.OrderNotEligibleException;
import com.example.aiassistant.orders.application.exception.OrderNotFoundException;
import com.example.aiassistant.orders.application.port.EscalationRepository;
import com.example.aiassistant.orders.application.port.OrderRepository;
import com.example.aiassistant.orders.domain.Escalation;
import com.example.aiassistant.orders.domain.EscalationIdentity;
import com.example.aiassistant.orders.domain.EscalationPriority;
import com.example.aiassistant.orders.domain.EscalationReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EscalationControllerTest {

    private static final String PATH =
            "/api/v1/orders/12345/escalations";

    private static final String KEY_HEADER =
            "Idempotency-Key";

    private static final UUID KEY =
            UUID.fromString(
                    "f90fd699-154c-4ddb-9b9a-316ea31b2b07"
            );

    private static final UUID ESCALATION_ID =
            UUID.fromString(
                    "5a3bb692-d257-491c-9212-d49ea1518ed1"
            );

    private static final UUID ORDER_ID =
            UUID.fromString(
                    "09b98c5b-37a5-4fa3-97c7-a016d7517760"
            );

    private static final String VALID_BODY = """
            {
              "reason": "OUT_OF_STOCK",
              "priority": "HIGH"
            }
            """;

    private static final EscalationIdentity IDENTITY =
            new EscalationIdentity(
                    "12345",
                    EscalationReason.OUT_OF_STOCK,
                    EscalationPriority.HIGH
            );

    private static final CreateEscalationCommand COMMAND =
            new CreateEscalationCommand(KEY, IDENTITY);

    private static final Escalation PERSISTED =
            new Escalation(
                    ESCALATION_ID,
                    ORDER_ID,
                    IDENTITY,
                    KEY,
                    Instant.parse("2026-11-14T07:00:00Z")
            );

    @MockitoBean(enforceOverride = true)
    private OrderRepository orderRepository;

    @MockitoBean(enforceOverride = true)
    private EscalationRepository escalationRepository;

    @MockitoBean(enforceOverride = true)
    private EscalationService escalationService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsCreatedForNewEscalation() throws Exception {

        when(escalationService.createEscalation(COMMAND))
                .thenReturn(
                        CreateEscalationResult.newlyCreated(
                                PERSISTED
                        )
                );

        expectEscalation(
                mockMvc.perform(validRequest()),
                201
        );

        verifyCommand();
    }

    @Test
    void returnsOkForPersistedReplay() throws Exception {

        when(escalationService.createEscalation(COMMAND))
                .thenReturn(
                        CreateEscalationResult.replayed(
                                PERSISTED
                        )
                );

        expectEscalation(
                mockMvc.perform(validRequest()),
                200
        );

        verifyCommand();
    }

    @Test
    void acceptsUppercaseUuidRepresentation() throws Exception {

        when(escalationService.createEscalation(COMMAND))
                .thenReturn(
                        CreateEscalationResult.replayed(
                                PERSISTED
                        )
                );

        expectEscalation(
                mockMvc.perform(
                        request(VALID_BODY)
                                .header(
                                        KEY_HEADER,
                                        KEY.toString()
                                                .toUpperCase(
                                                        Locale.ROOT
                                                )
                                )
                ),
                200
        );

        verifyCommand();
    }

    @Test
    void acceptsUuidWithoutRequiringVersionFour()
            throws Exception {

        UUID key = UUID.fromString(
                "00000000-0000-0000-0000-000000000000"
        );

        CreateEscalationCommand command =
                new CreateEscalationCommand(
                        key,
                        IDENTITY
                );

        Escalation escalation =
                new Escalation(
                        ESCALATION_ID,
                        ORDER_ID,
                        IDENTITY,
                        key,
                        PERSISTED.createdAt()
                );

        when(escalationService.createEscalation(command))
                .thenReturn(
                        CreateEscalationResult.newlyCreated(
                                escalation
                        )
                );

        expectEscalation(
                mockMvc.perform(
                        request(VALID_BODY)
                                .header(
                                        KEY_HEADER,
                                        key.toString()
                                )
                ),
                201
        );

        verify(escalationService)
                .createEscalation(command);

        verifyNoMoreInteractions(escalationService);
    }

    @Test
    void acceptsAdditionalJsonProperties()
            throws Exception {

        when(escalationService.createEscalation(COMMAND))
                .thenReturn(
                        CreateEscalationResult.newlyCreated(
                                PERSISTED
                        )
                );

        expectEscalation(
                mockMvc.perform(
                        request("""
                                {
                                  "reason": "OUT_OF_STOCK",
                                  "priority": "HIGH",
                                  "extra": "ignored"
                                }
                                """)
                                .header(
                                        KEY_HEADER,
                                        KEY.toString()
                                )
                ),
                201
        );

        verifyCommand();
    }

    @Test
    void preservesExactDisplayNumber() throws Exception {

        CreateEscalationCommand command =
                new CreateEscalationCommand(
                        KEY,
                        new EscalationIdentity(
                                "0012345",
                                EscalationReason.OUT_OF_STOCK,
                                EscalationPriority.HIGH
                        )
                );

        when(escalationService.createEscalation(command))
                .thenThrow(
                        new OrderNotFoundException(
                                "0012345"
                        )
                );

        String path =
                "/api/v1/orders/0012345/escalations";

        expectProblem(
                mockMvc.perform(
                        post(path)
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .header(
                                        KEY_HEADER,
                                        KEY.toString()
                                )
                                .content(VALID_BODY)
                ),
                404,
                "Not Found",
                "ORDER_NOT_FOUND",
                "Order was not found.",
                path
        );

        verify(escalationService)
                .createEscalation(command);

        verifyNoMoreInteractions(escalationService);
    }

    @Test
    void rejectsMissingIdempotencyKey() throws Exception {

        expectValidationError(
                mockMvc.perform(request(VALID_BODY))
        );

        verifyNoInteractions(escalationService);
    }

    @Test
    void rejectsRepeatedIdempotencyKeyHeader()
            throws Exception {

        expectValidationError(
                mockMvc.perform(
                        request(VALID_BODY)
                                .header(
                                        KEY_HEADER,
                                        KEY.toString(),
                                        KEY.toString()
                                )
                )
        );

        verifyNoInteractions(escalationService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            " ",
            "not-a-uuid",
            "1-1-1-1-1",
            "f90fd699154c4ddb9b9a316ea31b2b07",
            "f90fd699-154c-4ddb-9b9a-316ea31b2b0z",
            " f90fd699-154c-4ddb-9b9a-316ea31b2b07 ",
            "f90fd699-154c-4ddb-9b9a-316ea31b2b07,"
                    + "f90fd699-154c-4ddb-9b9a-316ea31b2b07"
    })
    void rejectsMalformedIdempotencyKeys(
            String key
    ) throws Exception {

        expectValidationError(
                mockMvc.perform(
                        request(VALID_BODY)
                                .header(KEY_HEADER, key)
                )
        );

        verifyNoInteractions(escalationService);
    }

    @Test
    void rejectsMissingRequestBody() throws Exception {

        expectValidationError(
                mockMvc.perform(
                        post(PATH)
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .header(
                                        KEY_HEADER,
                                        KEY.toString()
                                )
                )
        );

        verifyNoInteractions(escalationService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{",
            "{}",
            "null",
            "[]",
            "\"OUT_OF_STOCK\"",
            "{\"priority\":\"HIGH\"}",
            "{\"reason\":\"OUT_OF_STOCK\"}",
            "{\"reason\":null,\"priority\":\"HIGH\"}",
            "{\"reason\":\"OUT_OF_STOCK\",\"priority\":null}",
            "{\"reason\":\"UNKNOWN\",\"priority\":\"HIGH\"}",
            "{\"reason\":\"OUT_OF_STOCK\",\"priority\":\"LOW\"}",
            "{\"reason\":\"out_of_stock\",\"priority\":\"HIGH\"}",
            "{\"reason\":\"OUT_OF_STOCK\",\"priority\":\"high\"}",
            "{\"reason\":\" OUT_OF_STOCK \",\"priority\":\"HIGH\"}",
            "{\"reason\":\"OUT_OF_STOCK\",\"priority\":\" HIGH \"}",
            "{\"reason\":\"\",\"priority\":\"HIGH\"}",
            "{\"reason\":0,\"priority\":\"HIGH\"}",
            "{\"reason\":\"OUT_OF_STOCK\",\"priority\":0}",
            "{\"reason\":true,\"priority\":\"HIGH\"}",
            "{\"reason\":[\"OUT_OF_STOCK\"],\"priority\":\"HIGH\"}",
            "{\"reason\":\"OUT_OF_STOCK\",\"priority\":{}}"
    })
    void rejectsInvalidRequestBodies(
            String body
    ) throws Exception {

        expectValidationError(
                mockMvc.perform(
                        request(body)
                                .header(
                                        KEY_HEADER,
                                        KEY.toString()
                                )
                )
        );

        verifyNoInteractions(escalationService);
    }

    @Test
    void returnsNotFoundForMissingOrder() throws Exception {

        when(escalationService.createEscalation(COMMAND))
                .thenThrow(
                        new OrderNotFoundException(
                                "12345"
                        )
                );

        expectProblem(
                mockMvc.perform(validRequest()),
                404,
                "Not Found",
                "ORDER_NOT_FOUND",
                "Order was not found.",
                PATH
        );

        verifyCommand();
    }

    @Test
    void returnsConflictForIneligibleOrder() throws Exception {

        when(escalationService.createEscalation(COMMAND))
                .thenThrow(
                        new OrderNotEligibleException()
                );

        expectProblem(
                mockMvc.perform(validRequest()),
                409,
                "Conflict",
                "ORDER_NOT_ELIGIBLE",
                "Order is not eligible for escalation.",
                PATH
        );

        verifyCommand();
    }

    @Test
    void returnsConflictForReusedKeyWithDifferentIdentity()
            throws Exception {

        when(escalationService.createEscalation(COMMAND))
                .thenThrow(
                        new IdempotencyConflictException()
                );

        expectProblem(
                mockMvc.perform(validRequest()),
                409,
                "Conflict",
                "IDEMPOTENCY_CONFLICT",
                "Idempotency key conflicts with an existing request.",
                PATH
        );

        verifyCommand();
    }

    @Test
    void returnsSanitizedServerErrorForPersistenceFailure()
            throws Exception {

        when(escalationService.createEscalation(COMMAND))
                .thenThrow(
                        new DataAccessResourceFailureException(
                                "Synthetic SQL failure: orders.escalations"
                        )
                );

        MvcResult result =
                mockMvc.perform(validRequest())
                        .andExpect(
                                status().isInternalServerError()
                        )
                        .andExpect(
                                content().contentTypeCompatibleWith(
                                        MediaType.APPLICATION_PROBLEM_JSON
                                )
                        )
                        .andExpect(
                                jsonPath("$.type")
                                        .value("about:blank")
                        )
                        .andExpect(
                                jsonPath("$.title")
                                        .value("Internal Server Error")
                        )
                        .andExpect(
                                jsonPath("$.status")
                                        .value(500)
                        )
                        .andExpect(
                                jsonPath("$.detail").value(
                                        "An unexpected server error occurred."
                                )
                        )
                        .andExpect(
                                jsonPath("$.instance").value(PATH)
                        )
                        .andExpect(
                                jsonPath("$.requestId").isString()
                        )
                        .andExpect(
                                jsonPath("$.code")
                                        .doesNotHaveJsonPath()
                        )
                        .andExpect(
                                jsonPath("$.trace")
                                        .doesNotHaveJsonPath()
                        )
                        .andExpect(
                                jsonPath("$.exception")
                                        .doesNotHaveJsonPath()
                        )
                        .andReturn();

        assertThat(
                result.getResponse().getContentAsString()
        ).doesNotContain(
                "Synthetic SQL failure",
                "orders.escalations"
        );

        assertRequestId(result);

        verifyCommand();
    }

    @Test
    void generatesDifferentRequestIdsForInvalidRequests()
            throws Exception {

        MvcResult first =
                expectValidationError(
                        mockMvc.perform(
                                request(VALID_BODY)
                        )
                );

        MvcResult second =
                expectValidationError(
                        mockMvc.perform(
                                request(VALID_BODY)
                        )
                );

        assertThat(readRequestId(first))
                .isNotEqualTo(readRequestId(second));

        verifyNoInteractions(escalationService);
    }

    @Test
    void rejectsUnsupportedContentTypeWithoutExecutingService()
            throws Exception {

        mockMvc.perform(
                        post(PATH)
                                .contentType(MediaType.TEXT_PLAIN)
                                .header(
                                        KEY_HEADER,
                                        KEY.toString()
                                )
                                .content(VALID_BODY)
                )
                .andExpect(
                        status().isUnsupportedMediaType()
                );

        verifyNoInteractions(escalationService);
    }

    @Test
    void rejectsInvalidSyntaxBeforeCallingApplicationService()
            throws Exception {

        // Even an already-known key does not bypass
        // syntactic validation.
        expectValidationError(
                mockMvc.perform(
                        request("""
                                {
                                  "reason": "UNKNOWN",
                                  "priority": "HIGH"
                                }
                                """)
                                .header(
                                        KEY_HEADER,
                                        KEY.toString()
                                )
                )
        );

        verifyNoInteractions(
                escalationService,
                orderRepository,
                escalationRepository
        );
    }

    private static MockHttpServletRequestBuilder request(
            String body
    ) {

        return post(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private static MockHttpServletRequestBuilder validRequest() {

        return request(VALID_BODY)
                .header(
                        KEY_HEADER,
                        KEY.toString()
                );
    }

    private static void expectEscalation(
            ResultActions response,
            int expectedStatus
    ) throws Exception {

        response
                .andExpect(status().is(expectedStatus))
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(
                        jsonPath("$.escalationId")
                                .value(ESCALATION_ID.toString())
                )
                .andExpect(
                        jsonPath("$.orderNumber").value("12345")
                )
                .andExpect(
                        jsonPath("$.status").value("CREATED")
                )
                .andExpect(
                        jsonPath("$.id")
                                .doesNotHaveJsonPath()
                )
                .andExpect(
                        jsonPath("$.orderId")
                                .doesNotHaveJsonPath()
                )
                .andExpect(
                        jsonPath("$.idempotencyKey")
                                .doesNotHaveJsonPath()
                )
                .andExpect(
                        jsonPath("$.createdAt")
                                .doesNotHaveJsonPath()
                )
                .andExpect(
                        jsonPath("$.created")
                                .doesNotHaveJsonPath()
                )
                .andExpect(
                        jsonPath("$.reason")
                                .doesNotHaveJsonPath()
                )
                .andExpect(
                        jsonPath("$.priority")
                                .doesNotHaveJsonPath()
                );
    }

    private static MvcResult expectValidationError(
            ResultActions response
    ) throws Exception {

        return expectProblem(
                response,
                400,
                "Bad Request",
                "VALIDATION_ERROR",
                "Request validation failed.",
                PATH
        );
    }

    private static MvcResult expectProblem(
            ResultActions response,
            int expectedStatus,
            String expectedTitle,
            String expectedCode,
            String expectedDetail,
            String expectedInstance
    ) throws Exception {

        MvcResult result = response
                .andExpect(status().is(expectedStatus))
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_PROBLEM_JSON
                        )
                )
                .andExpect(
                        jsonPath("$.type").value("about:blank")
                )
                .andExpect(
                        jsonPath("$.title").value(expectedTitle)
                )
                .andExpect(
                        jsonPath("$.status").value(expectedStatus)
                )
                .andExpect(
                        jsonPath("$.detail").value(expectedDetail)
                )
                .andExpect(
                        jsonPath("$.instance").value(expectedInstance)
                )
                .andExpect(
                        jsonPath("$.code").value(expectedCode)
                )
                .andExpect(
                        jsonPath("$.requestId").isString()
                )
                .andExpect(
                        jsonPath("$.properties")
                                .doesNotHaveJsonPath()
                )
                .andReturn();

        assertRequestId(result);

        return result;
    }

    private static String readRequestId(
            MvcResult result
    ) throws Exception {

        return com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.requestId"
        );
    }

    private static void assertRequestId(
            MvcResult result
    ) throws Exception {

        String value = readRequestId(result);

        UUID requestId = UUID.fromString(value);

        assertThat(requestId.toString())
                .isEqualTo(value);

        assertThat(requestId.version())
                .isEqualTo(4);
    }

    private void verifyCommand() {

        verify(escalationService)
                .createEscalation(COMMAND);

        verifyNoMoreInteractions(escalationService);

        verifyNoInteractions(
                orderRepository,
                escalationRepository
        );
    }
}
