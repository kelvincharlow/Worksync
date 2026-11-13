package com.eclectics.worksync.users.controller;

import com.eclectics.worksync.users.dto.UserErrorResponse;
import com.eclectics.worksync.users.exception.UserNotFoundException;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Scoped to Users controllers; Auth's exception and security handling remain unchanged. */
@RestControllerAdvice(assignableTypes = {UserController.class, AdminUserController.class})
public class UserExceptionHandler {
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<UserErrorResponse> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new UserErrorResponse("USER_NOT_FOUND", "User not found", Instant.now()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            HandlerMethodValidationException.class, MethodArgumentTypeMismatchException.class,
            ConstraintViolationException.class})
    public ResponseEntity<UserErrorResponse> invalidInput() {
        // Never echo parser errors, rejected field values or service exception objects.
        return ResponseEntity.badRequest()
                .body(new UserErrorResponse("INVALID_USER_REQUEST", "Invalid user request", Instant.now()));
    }
}
