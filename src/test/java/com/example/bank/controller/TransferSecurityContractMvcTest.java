package com.example.bank.controller;

import com.example.bank.config.GlobalExceptionHandler;
import com.example.bank.config.SecurityConfig;
import com.example.bank.dto.BankMapper;
import com.example.bank.entity.Transaction;
import com.example.bank.entity.User;
import com.example.bank.exception.KycRequiredException;
import com.example.bank.service.AuthService;
import com.example.bank.service.OtpService;
import com.example.bank.service.RateLimitService;
import com.example.bank.service.TransactionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AuthController.class, TransactionController.class})
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:5173")
class TransferSecurityContractMvcTest {

    private static final String TRANSFER_BODY = """
            {
              "fromAccountId": 123,
              "toIdentifier": "recipientUsername",
              "amount": 25.00
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private OtpService otpService;

    @MockBean
    private AuthenticationManager authenticationManager;

    @MockBean
    private RateLimitService rateLimitService;

    @MockBean
    private TransactionService transactionService;

    @MockBean
    private BankMapper bankMapper;

    @Test
    void csrfEndpointReturnsHeaderMetadataAndReadableRootCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf")
                        .session(new MockHttpSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
                .andExpect(jsonPath("$.parameterName").value("_csrf"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();

        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(cookie);
        assertFalse(cookie.isHttpOnly());
        assertEquals("/", cookie.getPath());
    }

    @Test
    void corsPreflightAllowsCredentialedTransferHeadersForConfiguredOrigin() throws Exception {
        MvcResult result = mockMvc.perform(options("/api/v1/transactions/transfer")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(
                                HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                "Content-Type,X-XSRF-TOKEN,X-CSRF-TOKEN,Idempotency-Key"
                        ))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andReturn();

        String allowedHeaders = result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS);
        assertNotNull(allowedHeaders);
        String normalizedHeaders = allowedHeaders.toLowerCase();
        for (String header : List.of(
                "content-type",
                "x-xsrf-token",
                "x-csrf-token",
                "idempotency-key"
        )) {
            assertEquals(true, normalizedHeaders.contains(header));
        }
    }

    @Test
    void missingCsrfTokenReturnsCsrfSpecificForbiddenResponse() throws Exception {
        mockMvc.perform(post("/api/v1/transactions/transfer")
                        .with(authenticatedUser(new User()))
                        .contentType(APPLICATION_JSON)
                        .content(TRANSFER_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_MISSING"))
                .andExpect(jsonPath("$.message").value("Missing CSRF token"));

        verifyNoInteractions(transactionService);
    }

    @Test
    void invalidCsrfTokenReturnsCsrfSpecificForbiddenResponse() throws Exception {
        CsrfCredentials csrf = fetchCsrf();

        mockMvc.perform(post("/api/v1/transactions/transfer")
                        .with(authenticatedUser(new User()))
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), "invalid-token")
                        .contentType(APPLICATION_JSON)
                        .content(TRANSFER_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"))
                .andExpect(jsonPath("$.message").value("Invalid CSRF token"));

        verifyNoInteractions(transactionService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/transactions/transfer",
            "/transactions/transfer"
    })
    void authenticatedNonAdminCanTransferWithFrontendPayloadAndHeaders(String path) throws Exception {
        User user = user(42L, "sender", User.KycStatus.APPROVED);
        CsrfCredentials csrf = fetchCsrf();
        Transaction transaction = new Transaction();
        when(transactionService.transfer(
                anyLong(),
                anyString(),
                any(BigDecimal.class),
                any(User.class),
                anyString(),
                nullable(String.class),
                nullable(String.class)
        )).thenReturn(transaction);

        mockMvc.perform(post(path)
                        .with(authenticatedUser(user))
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .header("Idempotency-Key", "transfer-submission-123")
                        .contentType(APPLICATION_JSON)
                        .content(TRANSFER_BODY))
                .andExpect(status().isOk());

        verify(transactionService).transfer(
                eq(123L),
                eq("recipientUsername"),
                argThat(amount -> amount.compareTo(new BigDecimal("25.00")) == 0),
                same(user),
                eq("transfer-submission-123"),
                isNull(),
                isNull()
        );
    }

    @Test
    void kycFailureRemainsDistinctFromCsrfFailure() throws Exception {
        User user = user(42L, "sender", User.KycStatus.PENDING);
        CsrfCredentials csrf = fetchCsrf();
        when(transactionService.transfer(
                anyLong(),
                anyString(),
                any(BigDecimal.class),
                any(User.class),
                anyString(),
                nullable(String.class),
                nullable(String.class)
        )).thenThrow(new KycRequiredException(User.KycStatus.PENDING));

        mockMvc.perform(post("/api/v1/transactions/transfer")
                        .with(authenticatedUser(user))
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .header("Idempotency-Key", "kyc-check")
                        .contentType(APPLICATION_JSON)
                        .content(TRANSFER_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("KYC_REQUIRED"));
    }

    @Test
    void nonOwnerFailureRemainsAccessDenied() throws Exception {
        User user = user(42L, "sender", User.KycStatus.APPROVED);
        CsrfCredentials csrf = fetchCsrf();
        when(transactionService.transfer(
                anyLong(),
                anyString(),
                any(BigDecimal.class),
                any(User.class),
                anyString(),
                nullable(String.class),
                nullable(String.class)
        )).thenThrow(new AccessDeniedException("Not authorized to use this account"));

        mockMvc.perform(post("/api/v1/transactions/transfer")
                        .with(authenticatedUser(user))
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .header("Idempotency-Key", "owner-check")
                        .contentType(APPLICATION_JSON)
                        .content(TRANSFER_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value("Not authorized to use this account"));
    }

    private CsrfCredentials fetchCsrf() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(cookie);
        return new CsrfCredentials(
                body.get("headerName").asText(),
                body.get("token").asText(),
                cookie
        );
    }

    private RequestPostProcessor authenticatedUser(User user) {
        return authentication(
                UsernamePasswordAuthenticationToken.authenticated(user, null, List.of())
        );
    }

    private User user(Long id, String username, User.KycStatus kycStatus) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setKycStatus(kycStatus);
        return user;
    }

    private record CsrfCredentials(String headerName, String token, Cookie cookie) {
    }
}
