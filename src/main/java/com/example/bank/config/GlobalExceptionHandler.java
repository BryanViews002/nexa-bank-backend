package com.example.bank.config;

import com.example.bank.exception.InsufficientFundsException;
import com.example.bank.dto.KycState;
import com.example.bank.exception.KycRequiredException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.LockedException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ApiError> handleValidation(Exception exception, HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (exception instanceof MethodArgumentNotValidException validationException) {
            validationException.getBindingResult().getFieldErrors().forEach(error ->
                    fields.putIfAbsent(error.getField(), error.getDefaultMessage())
            );
        } else if (exception instanceof BindException bindException) {
            bindException.getBindingResult().getFieldErrors().forEach(error ->
                    fields.putIfAbsent(error.getField(), error.getDefaultMessage())
            );
        }
        return response(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "One or more request fields are invalid",
                fields,
                request
        );
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ApiError> handleInsufficientFunds(
            InsufficientFundsException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "INSUFFICIENT_FUNDS",
                exception.getMessage(),
                Map.of(),
                request
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(
            AccessDeniedException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED",
                exception.getMessage(),
                Map.of(),
                request
        );
    }

    @ExceptionHandler(KycRequiredException.class)
    public ResponseEntity<ApiError> handleKycRequired(
            KycRequiredException exception,
            HttpServletRequest request
    ) {
        KycState state = KycState.from(exception.getKycStatus());
        return response(
                HttpStatus.FORBIDDEN,
                "KYC_REQUIRED",
                exception.getMessage(),
                Map.of(),
                Map.of(
                        "kycStatus", state.kycStatus(),
                        "kycRequired", state.kycRequired(),
                        "nextAction", state.nextAction(),
                        "redirectTo", state.redirectTo()
                ),
                request
        );
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ApiError> handleLocked(LockedException exception, HttpServletRequest request) {
        return response(
                HttpStatus.LOCKED,
                "ACCOUNT_LOCKED",
                exception.getMessage(),
                Map.of(),
                request
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadRequest(
            IllegalArgumentException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.BAD_REQUEST,
                "BAD_REQUEST",
                exception.getMessage(),
                Map.of(),
                request
        );
    }

    @ExceptionHandler({
            IllegalStateException.class,
            DataIntegrityViolationException.class,
            ObjectOptimisticLockingFailureException.class
    })
    public ResponseEntity<ApiError> handleConflict(Exception exception, HttpServletRequest request) {
        String message = exception instanceof IllegalStateException
                ? exception.getMessage()
                : "The request conflicted with the current resource state";
        return response(HttpStatus.CONFLICT, "CONFLICT", message, Map.of(), request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleUploadLimit(
            MaxUploadSizeExceededException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "UPLOAD_TOO_LARGE",
                "The uploaded file exceeds the configured size limit",
                Map.of(),
                request
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.METHOD_NOT_ALLOWED,
                "METHOD_NOT_ALLOWED",
                "This HTTP method is not supported for the requested endpoint",
                Map.of(),
                request
        );
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "UNSUPPORTED_MEDIA_TYPE",
                "KYC documents must be submitted as multipart/form-data",
                Map.of(),
                request
        );
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiError> handleMissingRequestPart(
            MissingServletRequestPartException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.BAD_REQUEST,
                "MISSING_REQUEST_PART",
                "The KYC document file is required",
                Map.of(),
                request
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneral(Exception exception, HttpServletRequest request) {
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "An unexpected error occurred",
                Map.of(),
                request
        );
    }

    private ResponseEntity<ApiError> response(
            HttpStatus status,
            String code,
            String message,
            Map<String, String> fieldErrors,
            HttpServletRequest request
    ) {
        return response(status, code, message, fieldErrors, Map.of(), request);
    }

    private ResponseEntity<ApiError> response(
            HttpStatus status,
            String code,
            String message,
            Map<String, String> fieldErrors,
            Map<String, Object> details,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(status).body(new ApiError(
                code,
                message,
                status.value(),
                request.getRequestURI(),
                Instant.now(),
                fieldErrors,
                details
        ));
    }

    public record ApiError(
            String code,
            String message,
            int status,
            String path,
            Instant timestamp,
            Map<String, String> fieldErrors,
            Map<String, Object> details
    ) {
    }
}
