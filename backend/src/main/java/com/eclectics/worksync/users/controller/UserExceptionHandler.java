package com.eclectics.worksync.users.controller;

import com.eclectics.worksync.users.dto.UserErrorResponse;
import com.eclectics.worksync.users.exception.InvalidPermissionRequestException;
import com.eclectics.worksync.users.exception.InvalidPermissionTargetException;
import com.eclectics.worksync.users.exception.OrganizationPermissionDeniedException;
import com.eclectics.worksync.users.exception.UserNotFoundException;
import com.eclectics.worksync.users.exception.InvalidAccountStatusTransitionException;
import com.eclectics.worksync.users.exception.ProtectedAdministratorOperationException;
import com.eclectics.worksync.users.exception.AdministratorAccessDeniedException;
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

    @ExceptionHandler(InvalidAccountStatusTransitionException.class)
    public ResponseEntity<UserErrorResponse> invalidTransition() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new UserErrorResponse("INVALID_ACCOUNT_STATUS_TRANSITION",
                        "Operation is not valid for the account status", Instant.now()));
    }

    @ExceptionHandler(ProtectedAdministratorOperationException.class)
    public ResponseEntity<UserErrorResponse> protectedAdministrator() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new UserErrorResponse("PROTECTED_ADMINISTRATOR_OPERATION",
                        "Administrator account operation is protected", Instant.now()));
    }

    @ExceptionHandler(AdministratorAccessDeniedException.class)
    public ResponseEntity<UserErrorResponse> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new UserErrorResponse("ADMINISTRATOR_ACCESS_REQUIRED",
                        "Administrator access is required", Instant.now()));
    }

    @ExceptionHandler(InvalidPermissionTargetException.class)
    public ResponseEntity<UserErrorResponse> invalidPermissionTarget() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new UserErrorResponse("INVALID_PERMISSION_TARGET", "Only ACTIVE users may receive new grants", Instant.now()));
    }

    @ExceptionHandler(OrganizationPermissionDeniedException.class)
    public ResponseEntity<UserErrorResponse> permissionDenied() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new UserErrorResponse("ORGANIZATION_PERMISSION_REQUIRED", "Organization permission required", Instant.now()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            HandlerMethodValidationException.class, MethodArgumentTypeMismatchException.class,
            ConstraintViolationException.class, InvalidPermissionRequestException.class})
    public ResponseEntity<UserErrorResponse> invalidInput() {
        // Never echo parser errors, rejected field values or service exception objects.
        return ResponseEntity.badRequest()
                .body(new UserErrorResponse("INVALID_USER_REQUEST", "Invalid user request", Instant.now()));
    }
}
