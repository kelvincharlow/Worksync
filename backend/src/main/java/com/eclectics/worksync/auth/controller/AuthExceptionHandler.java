package com.eclectics.worksync.auth.controller;

import com.eclectics.worksync.auth.dto.ApiError;
import com.eclectics.worksync.auth.dto.FieldError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

/**
 * Scoped strictly to AuthController so it never conflicts with the Users module.
 */
@RestControllerAdvice(assignableTypes = {AuthController.class})
public class AuthExceptionHandler {

    /**
     * Handles all business exceptions thrown by AuthService (401, 403, 409, 429).
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatusException(ResponseStatusException ex) {
        String code = switch ((HttpStatus) ex.getStatusCode()) {
            case UNAUTHORIZED -> "INVALID_CREDENTIALS";
            case FORBIDDEN -> "ACCESS_DENIED";
            case CONFLICT -> "DUPLICATE_EMAIL";
            case TOO_MANY_REQUESTS -> "TOO_MANY_REQUESTS";
            case BAD_REQUEST -> "BAD_REQUEST";
            default -> "AUTH_ERROR";
        };

        String message = ex.getReason() != null ? ex.getReason() : "Authentication error occurred.";
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ApiError(code, message, Instant.now(), null));
    }

    /**
     * Handles @Valid field validation errors (e.g. invalid phone format, short password).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationException(MethodArgumentNotValidException ex) {
        List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> new FieldError(err.getField(), err.getDefaultMessage()))
                .toList();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("VALIDATION_FAILED", "Validation failed for one or more fields.", Instant.now(), fieldErrors));
    }

    /**
     * Handles malformed JSON payloads.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleMalformedJson(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("MALFORMED_REQUEST", "Request body is missing or unreadable.", Instant.now(), null));
    }
}