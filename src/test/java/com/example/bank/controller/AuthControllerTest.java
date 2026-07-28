package com.example.bank.controller;

import com.example.bank.dto.AuthDto;
import com.example.bank.entity.User;
import com.example.bank.service.AuthService;
import com.example.bank.service.OtpService;
import com.example.bank.service.RateLimitService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    private AuthService authService;
    private OtpService otpService;
    private AuthController controller;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        otpService = mock(OtpService.class);
        controller = new AuthController(
                authService,
                otpService,
                mock(AuthenticationManager.class),
                mock(RateLimitService.class),
                "test"
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void registrationTellsClientToOpenKycOnboarding() {
        AuthDto.RegisterRequest request = new AuthDto.RegisterRequest(
                "newuser",
                "new@example.com",
                "password123",
                "New User"
        );
        User user = user(41L, "newuser", User.KycStatus.NOT_SUBMITTED);
        when(authService.register(request)).thenReturn(user);

        ResponseEntity<?> response = controller.register(request);

        AuthDto.RegistrationResponse body =
                assertInstanceOf(AuthDto.RegistrationResponse.class, response.getBody());
        assertEquals(User.KycStatus.NOT_SUBMITTED.name(), body.kycStatus());
        assertEquals(true, body.kycRequired());
        assertEquals("COMPLETE_KYC", body.nextAction());
        assertEquals("/kyc", body.redirectTo());
    }

    @Test
    void successfulOtpVerificationRoutesUnverifiedUserToKyc() {
        User user = user(42L, "existinguser", User.KycStatus.REJECTED);
        Authentication pendingAuthentication =
                new UsernamePasswordAuthenticationToken(user, null, List.of());
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        MockHttpSession session = (MockHttpSession) httpRequest.getSession(true);
        session.setAttribute("pendingAuth", pendingAuthentication);
        session.setAttribute("pendingAuthTime", LocalDateTime.now());
        when(otpService.verifyOtp(user, "123456", "LOGIN")).thenReturn(true);

        ResponseEntity<?> response = controller.verifyOtp(
                new AuthDto.OtpVerificationRequest("123456", "LOGIN"),
                httpRequest
        );

        AuthDto.AuthenticationResponse body =
                assertInstanceOf(AuthDto.AuthenticationResponse.class, response.getBody());
        assertEquals(true, body.authenticated());
        assertEquals(User.KycStatus.REJECTED.name(), body.kycStatus());
        assertEquals(true, body.kycRequired());
        assertEquals("COMPLETE_KYC", body.nextAction());
        assertEquals("/kyc", body.redirectTo());
        assertNull(session.getAttribute("pendingAuth"));
        assertNull(session.getAttribute("pendingAuthTime"));
        assertSame(pendingAuthentication, SecurityContextHolder.getContext().getAuthentication());
    }

    private User user(Long id, String username, User.KycStatus status) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setKycStatus(status);
        return user;
    }
}
