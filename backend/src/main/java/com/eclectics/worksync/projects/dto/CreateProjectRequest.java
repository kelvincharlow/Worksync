package com.eclectics.worksync.projects.dto;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import tools.jackson.databind.annotation.JsonDeserialize;
import java.time.LocalDate;
public record CreateProjectRequest(
        @NotBlank @Size(max = 200) @JsonDeserialize(using = StrictProjectStringDeserializer.class)
        String name,
        @JsonDeserialize(using = StrictProjectStringDeserializer.class) String description,
        @JsonDeserialize(using = StrictProjectStringDeserializer.class) String requirements,
        @JsonDeserialize(using = StrictProjectStringDeserializer.class) String scope,
        @JsonDeserialize(using = StrictProjectStringDeserializer.class) String deliverables,
        @JsonDeserialize(using = StrictProjectStringDeserializer.class) String successCriteria,
        LocalDate startDate, LocalDate dueDate) {
    @JsonAnySetter
    public void rejectUnknownField(String field, Object value) {
        throw new IllegalArgumentException("Unsupported project request field");
    }
    @AssertTrue(message = "startDate must not be after dueDate")
    public boolean isDateRangeValid() {
        return startDate == null || dueDate == null || !startDate.isAfter(dueDate);
    }
}
