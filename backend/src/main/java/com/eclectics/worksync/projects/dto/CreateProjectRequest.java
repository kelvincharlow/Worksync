package com.eclectics.worksync.projects.dto;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
public record CreateProjectRequest(
        @NotBlank @Size(max = 200) String name, String description, String requirements,
        String scope, String deliverables, String successCriteria, LocalDate startDate, LocalDate dueDate) {
    @AssertTrue(message = "startDate must not be after dueDate")
    public boolean isDateRangeValid() {
        return startDate == null || dueDate == null || !startDate.isAfter(dueDate);
    }
}
