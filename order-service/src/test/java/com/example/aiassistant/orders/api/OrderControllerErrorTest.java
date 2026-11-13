package com.example.aiassistant.orders.api;

import com.example.aiassistant.orders.api.error.OrderRequestValidationException;
import com.example.aiassistant.orders.application.OrderQueryService;
import com.example.aiassistant.orders.application.port.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderControllerErrorTest {

    @MockitoBean(enforceOverride = true)
    private OrderRepository orderRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderExceptionHandler exceptionHandler;

    @Test
    void returnsContractProblemForMissingOrder()
            throws Exception {

        when(orderRepository.findByDisplayNumber("99999"))
                .thenReturn(Optional.empty());

        ResultActions response = mockMvc.perform(
                get("/api/v1/orders/99999")
                        .accept(MediaType.APPLICATION_JSON)
        );

        expectOrderNotFound(
                response,
                "/api/v1/orders/99999"
        );

        verify(orderRepository)
                .findByDisplayNumber("99999");

        verifyNoMoreInteractions(orderRepository);
    }

    @Test
    void returnsContractProblemForMissingOrderEvents()
            throws Exception {

        when(orderRepository.findByDisplayNumber("99999"))
                .thenReturn(Optional.empty());

        ResultActions response = mockMvc.perform(
                get("/api/v1/orders/99999/events")
                        .accept(
                                MediaType.APPLICATION_JSON,
                                MediaType.APPLICATION_PROBLEM_JSON
                        )
        );

        expectOrderNotFound(
                response,
                "/api/v1/orders/99999/events"
        );

        verify(orderRepository)
                .findByDisplayNumber("99999");

        verifyNoMoreInteractions(orderRepository);
    }

    @Test
    void treatsUnknownNumberWithLeadingZerosAsNotFound()
            throws Exception {

        when(orderRepository.findByDisplayNumber("0012345"))
                .thenReturn(Optional.empty());

        expectOrderNotFound(
                mockMvc.perform(
                        get("/api/v1/orders/0012345")
                ),
                "/api/v1/orders/0012345"
        );

        verify(orderRepository)
                .findByDisplayNumber("0012345");

        verifyNoMoreInteractions(orderRepository);
    }

    @Test
    void treatsWhitespaceNumberAsValidButUnknown()
            throws Exception {

        when(orderRepository.findByDisplayNumber(" "))
                .thenReturn(Optional.empty());

        expectOrderNotFound(
                mockMvc.perform(
                        get(URI.create("/api/v1/orders/%20"))
                ),
                "/api/v1/orders/%20"
        );

        verify(orderRepository)
                .findByDisplayNumber(" ");

        verifyNoMoreInteractions(orderRepository);
    }

    @Test
    void generatesDistinctServerRequestIdsForSeparateErrors()
            throws Exception {

        String suppliedRequestId =
                "00000000-0000-4000-8000-000000000001";

        when(orderRepository.findByDisplayNumber("99999"))
                .thenReturn(Optional.empty());

        MvcResult first = expectOrderNotFound(
                mockMvc.perform(
                        get("/api/v1/orders/99999")
                                .header(
                                        "X-Request-Id",
                                        suppliedRequestId
                                )
                ),
                "/api/v1/orders/99999"
        );

        MvcResult second = expectOrderNotFound(
                mockMvc.perform(
                        get("/api/v1/orders/99999")
                                .header(
                                        "X-Request-Id",
                                        suppliedRequestId
                                )
                ),
                "/api/v1/orders/99999"
        );

        String firstId = readRequestId(first);
        String secondId = readRequestId(second);

        assertThat(firstId).isNotEqualTo(secondId);
        assertThat(firstId).isNotEqualTo(suppliedRequestId);
        assertThat(secondId).isNotEqualTo(suppliedRequestId);
    }

    @Test
    void returnsSanitizedServerErrorForDatabaseFailure()
            throws Exception {

        when(orderRepository.findByDisplayNumber("12345"))
                .thenThrow(
                        new DataAccessResourceFailureException(
                                "Synthetic SQL diagnostic: orders.orders"
                        )
                );

        ResultActions response = mockMvc.perform(
                get("/api/v1/orders/12345")
                        .accept(MediaType.APPLICATION_JSON)
        );

        expectServerError(response);
    }

    @Test
    void doesNotMisclassifyInternalIllegalArgumentAsBadRequest()
            throws Exception {

        when(orderRepository.findByDisplayNumber("12345"))
                .thenThrow(
                        new IllegalArgumentException(
                                "Synthetic internal mapping failure."
                        )
                );

        expectServerError(
                mockMvc.perform(
                        get("/api/v1/orders/12345")
                )
        );
    }

    @Test
    void createsContractValidationProblem()
            throws Exception {

        MockHttpServletRequest request =
                new MockHttpServletRequest(
                        "GET",
                        "/api/v1/orders/12345"
                );

        ResponseEntity<ProblemDetail> response =
                exceptionHandler.handleValidationError(
                        new OrderRequestValidationException(),
                        request
                );

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);

        ProblemDetail problem = response.getBody();

        assertThat(problem).isNotNull();

        assertThat(problem.getType())
                .isEqualTo(URI.create("about:blank"));

        assertThat(problem.getTitle())
                .isEqualTo("Bad Request");

        assertThat(problem.getStatus())
                .isEqualTo(400);

        assertThat(problem.getDetail())
                .isEqualTo("Request validation failed.");

        assertThat(problem.getInstance())
                .isEqualTo(URI.create("/api/v1/orders/12345"));

        assertThat(problem.getProperties())
                .containsEntry("code", "VALIDATION_ERROR");

        String requestId = (String) problem.getProperties()
                .get("requestId");

        assertCanonicalUuid(requestId);
    }

    @Test
    void serializesValidationExceptionAsContractProblem()
            throws Exception {

        OrderQueryService service =
                mock(OrderQueryService.class);

        when(service.getOrder("12345"))
                .thenThrow(
                        new OrderRequestValidationException()
                );

        MockMvc validationMvc = MockMvcBuilders
                .standaloneSetup(
                        new OrderController(
                                service,
                                new OrderApiMapper()
                        )
                )
                .setControllerAdvice(exceptionHandler)
                .build();

        MvcResult result = validationMvc.perform(
                        get("/api/v1/orders/12345")
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON
                ))
                .andExpect(jsonPath("$.type")
                        .value("about:blank"))
                .andExpect(jsonPath("$.title")
                        .value("Bad Request"))
                .andExpect(jsonPath("$.status")
                        .value(400))
                .andExpect(jsonPath("$.detail")
                        .value("Request validation failed."))
                .andExpect(jsonPath("$.instance")
                        .value("/api/v1/orders/12345"))
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.requestId").isString())
                .andExpect(jsonPath("$.properties")
                        .doesNotHaveJsonPath())
                .andReturn();

        assertCanonicalUuid(readRequestId(result));
    }

    private static MvcResult expectOrderNotFound(
            ResultActions response,
            String expectedInstance
    ) throws Exception {

        MvcResult result = response
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON
                ))
                .andExpect(jsonPath("$.type")
                        .value("about:blank"))
                .andExpect(jsonPath("$.title")
                        .value("Not Found"))
                .andExpect(jsonPath("$.status")
                        .value(404))
                .andExpect(jsonPath("$.detail")
                        .value("Order was not found."))
                .andExpect(jsonPath("$.instance")
                        .value(expectedInstance))
                .andExpect(jsonPath("$.code")
                        .value("ORDER_NOT_FOUND"))
                .andExpect(jsonPath("$.requestId").isString())
                .andExpect(jsonPath("$.properties")
                        .doesNotHaveJsonPath())
                .andReturn();

        assertCanonicalUuid(readRequestId(result));

        return result;
    }

    private static void expectServerError(
            ResultActions response
    ) throws Exception {

        MvcResult result = response
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON
                ))
                .andExpect(jsonPath("$.type")
                        .value("about:blank"))
                .andExpect(jsonPath("$.title")
                        .value("Internal Server Error"))
                .andExpect(jsonPath("$.status")
                        .value(500))
                .andExpect(jsonPath("$.detail")
                        .value("An unexpected server error occurred."))
                .andExpect(jsonPath("$.instance")
                        .value("/api/v1/orders/12345"))
                .andExpect(jsonPath("$.requestId").isString())
                .andExpect(jsonPath("$.code")
                        .doesNotHaveJsonPath())
                .andExpect(jsonPath("$.trace")
                        .doesNotHaveJsonPath())
                .andExpect(jsonPath("$.exception")
                        .doesNotHaveJsonPath())
                .andExpect(jsonPath("$.message")
                        .doesNotHaveJsonPath())
                .andReturn();

        assertThat(
                result.getResponse().getContentAsString()
        ).doesNotContain(
                "Synthetic SQL diagnostic",
                "orders.orders",
                "Synthetic internal mapping failure"
        );

        assertCanonicalUuid(readRequestId(result));
    }

    private static String readRequestId(
            MvcResult result
    ) throws Exception {

        return com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.requestId"
        );
    }

    private static void assertCanonicalUuid(String value) {

        assertThat(value).isNotNull();

        UUID uuid = UUID.fromString(value);

        assertThat(uuid.toString()).isEqualTo(value);
        assertThat(uuid.version()).isEqualTo(4);
    }
}