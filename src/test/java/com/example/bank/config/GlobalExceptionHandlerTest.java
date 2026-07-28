package com.example.bank.config;

import com.example.bank.entity.User;
import com.example.bank.exception.KycRequiredException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void returnsActionableKycErrorForBlockedTransfers() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/transactions/transfer");

        ResponseEntity<GlobalExceptionHandler.ApiError> response = handler.handleKycRequired(
                new KycRequiredException(User.KycStatus.NOT_SUBMITTED),
                request
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        GlobalExceptionHandler.ApiError body = response.getBody();
        assertEquals("KYC_REQUIRED", body.code());
        assertEquals(User.KycStatus.NOT_SUBMITTED.name(), body.details().get("kycStatus"));
        assertEquals(true, body.details().get("kycRequired"));
        assertEquals("COMPLETE_KYC", body.details().get("nextAction"));
        assertEquals("/kyc", body.details().get("redirectTo"));
    }
}
