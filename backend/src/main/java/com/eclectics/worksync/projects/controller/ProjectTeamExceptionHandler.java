package com.eclectics.worksync.projects.controller;

import com.eclectics.worksync.projects.dto.ProjectErrorResponse;
import com.eclectics.worksync.projects.exception.*;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.*;

@RestControllerAdvice(assignableTypes=ProjectTeamController.class)
public class ProjectTeamExceptionHandler {
    @ExceptionHandler(ProjectNotFoundException.class)
    public ResponseEntity<ProjectErrorResponse> missing() { return error(HttpStatus.NOT_FOUND,"PROJECT_NOT_FOUND","Project not found"); }
    @ExceptionHandler(ProjectAccessDeniedException.class)
    public ResponseEntity<ProjectErrorResponse> denied() { return error(HttpStatus.FORBIDDEN,"PROJECT_ACCESS_DENIED","Project access denied"); }
    @ExceptionHandler(ProjectArchivedException.class)
    public ResponseEntity<ProjectErrorResponse> archived() { return error(HttpStatus.CONFLICT,"PROJECT_ARCHIVED","Project is archived"); }
    @ExceptionHandler({ProjectMembershipConflictException.class,DataIntegrityViolationException.class})
    public ResponseEntity<ProjectErrorResponse> conflict() { return error(HttpStatus.CONFLICT,"PROJECT_MEMBERSHIP_CONFLICT","Membership conflicts with current eligibility"); }
    @ExceptionHandler({MethodArgumentNotValidException.class,HttpMessageNotReadableException.class,
            HandlerMethodValidationException.class,MethodArgumentTypeMismatchException.class,
            ConstraintViolationException.class,IllegalArgumentException.class,InvalidProjectMembershipException.class})
    public ResponseEntity<ProjectErrorResponse> invalid() { return error(HttpStatus.BAD_REQUEST,"INVALID_PROJECT_REQUEST","Invalid project request"); }
    private ResponseEntity<ProjectErrorResponse> error(HttpStatus status,String code,String message) {
        return ResponseEntity.status(status).body(new ProjectErrorResponse(code,message,Instant.now()));
    }
}
