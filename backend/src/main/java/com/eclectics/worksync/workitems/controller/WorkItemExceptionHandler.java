package com.eclectics.worksync.workitems.controller;

import com.eclectics.worksync.workitems.dto.WorkItemErrorResponse;
import com.eclectics.worksync.workitems.exception.InvalidAssigneeException;
import com.eclectics.worksync.workitems.exception.WorkItemNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.eclectics.worksync.workitems.controller")
public class WorkItemExceptionHandler {

    @ExceptionHandler(WorkItemNotFoundException.class)
    public ResponseEntity<WorkItemErrorResponse> handleNotFound(
            WorkItemNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new WorkItemErrorResponse(
                        "WORK_ITEM_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(InvalidAssigneeException.class)
    public ResponseEntity<WorkItemErrorResponse> handleInvalidAssignee(
            InvalidAssigneeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new WorkItemErrorResponse(
                        "INVALID_ASSIGNEE", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<WorkItemErrorResponse> handleValidation(
            MethodArgumentNotValidException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new WorkItemErrorResponse(
                        "INVALID_WORK_ITEM_REQUEST", 
                        "Validation failed for one or more fields."));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<WorkItemErrorResponse> handleMalformed(
            HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new WorkItemErrorResponse(
                        "MALFORMED_REQUEST", 
                        "Request body is missing or unreadable."));
    }
}