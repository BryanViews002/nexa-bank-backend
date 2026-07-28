package com.example.bank.controller;

import com.example.bank.config.GlobalExceptionHandler;
import com.example.bank.config.SecurityConfig;
import com.example.bank.dto.BankMapper;
import com.example.bank.entity.KycDocument;
import com.example.bank.entity.User;
import com.example.bank.repository.KycRepository;
import com.example.bank.repository.UserRepository;
import com.example.bank.service.KycGuardService;
import com.example.bank.service.KycService;
import com.example.bank.service.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.util.unit.DataSize;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(KycController.class)
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class,
        BankMapper.class,
        KycControllerMvcTest.KycTestConfiguration.class
})
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:5173")
class KycControllerMvcTest {

    private static final Path UPLOAD_ROOT =
            Path.of("target", "test-kyc-uploads").toAbsolutePath().normalize();

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KycRepository kycRepository;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private NotificationService notificationService;

    @MockBean
    private KycGuardService kycGuardService;

    @BeforeEach
    void setUp() {
        when(kycRepository.save(any(KycDocument.class))).thenAnswer(invocation -> {
            KycDocument document = invocation.getArgument(0);
            document.setId(91L);
            document.setUploadedAt(Instant.parse("2026-07-28T00:00:00Z"));
            return document;
        });
    }

    @AfterEach
    void cleanUploads() throws IOException {
        if (!Files.exists(UPLOAD_ROOT)) {
            return;
        }
        List<Path> paths;
        try (var stream = Files.walk(UPLOAD_ROOT)) {
            paths = stream.sorted(Comparator.reverseOrder()).toList();
        }
        for (Path path : paths) {
            Files.deleteIfExists(path);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "application/pdf,identity.pdf",
            "image/jpeg,identity.jpg",
            "image/png,identity.png"
    })
    void acceptsSupportedDocumentTypes(String contentType, String filename) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                filename,
                contentType,
                "valid-document".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/kyc/documents")
                        .file(file)
                        .with(authenticatedUser())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(91))
                .andExpect(jsonPath("$.contentType").value(contentType))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void acceptsMultipartUploadAtCompatibilityRoot() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "identity.pdf",
                "application/pdf",
                "valid-document".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/kyc")
                        .file(file)
                        .with(authenticatedUser())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentType").value("application/pdf"));
    }

    @Test
    void rejectsMissingFilePart() throws Exception {
        mockMvc.perform(multipart("/api/v1/kyc/documents")
                        .with(authenticatedUser())
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_REQUEST_PART"))
                .andExpect(jsonPath("$.message").value("The KYC document file is required"));
    }

    @Test
    void rejectsJsonAtCompatibilityRootAsUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/api/v1/kyc")
                        .contentType(APPLICATION_JSON)
                        .content("{}")
                        .with(authenticatedUser())
                        .with(csrf()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(jsonPath("$.message")
                        .value("KYC documents must be submitted as multipart/form-data"));
    }

    @Test
    void rejectsUnsupportedMethodWithoutReturningInternalError() throws Exception {
        mockMvc.perform(put("/api/v1/kyc")
                        .contentType(APPLICATION_JSON)
                        .content("{}")
                        .with(authenticatedUser())
                        .with(csrf()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void rejectsEmptyDocument() throws Exception {
        MockMultipartFile file =
                new MockMultipartFile("file", "identity.pdf", "application/pdf", new byte[0]);

        mockMvc.perform(multipart("/api/v1/kyc/documents")
                        .file(file)
                        .with(authenticatedUser())
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("A non-empty KYC document is required"));
    }

    @Test
    void rejectsOversizedDocument() throws Exception {
        int oversizedLength = Math.toIntExact(DataSize.ofMegabytes(10).toBytes() + 1);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "identity.pdf",
                "application/pdf",
                new byte[oversizedLength]
        );

        mockMvc.perform(multipart("/api/v1/kyc/documents")
                        .file(file)
                        .with(authenticatedUser())
                        .with(csrf()))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("UPLOAD_TOO_LARGE"));
    }

    @Test
    void rejectsUnsupportedDocumentType() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "identity.txt",
                "text/plain",
                "not-an-identity-document".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/kyc/documents")
                        .file(file)
                        .with(authenticatedUser())
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message")
                        .value("Only PDF, JPEG, and PNG KYC documents are accepted"));
    }

    private RequestPostProcessor authenticatedUser() {
        User user = new User();
        user.setId(42L);
        user.setUsername("kyc-user");
        return authentication(
                UsernamePasswordAuthenticationToken.authenticated(user, null, List.of())
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class KycTestConfiguration {

        @Bean
        KycService kycService(
                KycRepository kycRepository,
                UserRepository userRepository,
                NotificationService notificationService
        ) {
            return new KycService(
                    kycRepository,
                    userRepository,
                    notificationService,
                    UPLOAD_ROOT.toString(),
                    "10MB"
            );
        }
    }
}
