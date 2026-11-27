package com.eclectics.worksync.projects.controller;

import com.eclectics.worksync.projects.dto.ProjectErrorResponse;
import com.eclectics.worksync.users.exception.OrganizationPermissionDeniedException;
import com.eclectics.worksync.users.exception.UserNotFoundException;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes = ProjectController.class)
public class ProjectExceptionHandler {
    @ExceptionHandler({OrganizationPermissionDeniedException.class, UserNotFoundException.class})
    public ResponseEntity<ProjectErrorResponse> forbidden() {
        return error(HttpStatus.FORBIDDEN, "ORGANIZATION_PERMISSION_REQUIRED", "Organization permission required");
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            ConstraintViolationException.class})
    public ResponseEntity<ProjectErrorResponse> invalid() {
        return error(HttpStatus.BAD_REQUEST, "INVALID_PROJECT_REQUEST", "Invalid project request");
    }

    // Runs outside ProjectService's transaction, after a failing creation has rolled back.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProjectErrorResponse> conflict() {
        return error(HttpStatus.CONFLICT, "PROJECT_CREATION_CONFLICT", "Project creation conflicted with current data");
    }

    private ResponseEntity<ProjectErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ProjectErrorResponse(code, message, Instant.now()));
    }
}
