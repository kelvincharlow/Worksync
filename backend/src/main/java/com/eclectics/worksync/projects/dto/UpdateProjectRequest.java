package com.eclectics.worksync.projects.dto;

import com.fasterxml.jackson.annotation.*;
import jakarta.validation.constraints.AssertTrue;
import java.time.LocalDate;
import java.util.EnumSet;
import tools.jackson.databind.annotation.JsonDeserialize;

/** Presence-aware PATCH: omission preserves a field; explicit null may clear dates only. */
public class UpdateProjectRequest {
    public enum Field { NAME, DESCRIPTION, REQUIREMENTS, SCOPE, DELIVERABLES, SUCCESS_CRITERIA, START_DATE, DUE_DATE }
    private final EnumSet<Field> supplied=EnumSet.noneOf(Field.class);
    @JsonDeserialize(using=StrictProjectStringDeserializer.class) private String name;
    @JsonDeserialize(using=StrictProjectStringDeserializer.class) private String description;
    @JsonDeserialize(using=StrictProjectStringDeserializer.class) private String requirements;
    @JsonDeserialize(using=StrictProjectStringDeserializer.class) private String scope;
    @JsonDeserialize(using=StrictProjectStringDeserializer.class) private String deliverables;
    @JsonDeserialize(using=StrictProjectStringDeserializer.class) private String successCriteria;
    @JsonDeserialize(using=StrictProjectDateDeserializer.class) private LocalDate startDate;
    @JsonDeserialize(using=StrictProjectDateDeserializer.class) private LocalDate dueDate;

    @JsonSetter public void setName(String v) { supplied.add(Field.NAME);name=v; }
    @JsonSetter public void setDescription(String v) { supplied.add(Field.DESCRIPTION);description=v; }
    @JsonSetter public void setRequirements(String v) { supplied.add(Field.REQUIREMENTS);requirements=v; }
    @JsonSetter public void setScope(String v) { supplied.add(Field.SCOPE);scope=v; }
    @JsonSetter public void setDeliverables(String v) { supplied.add(Field.DELIVERABLES);deliverables=v; }
    @JsonSetter public void setSuccessCriteria(String v) { supplied.add(Field.SUCCESS_CRITERIA);successCriteria=v; }
    @JsonSetter public void setStartDate(LocalDate v) { supplied.add(Field.START_DATE);startDate=v; }
    @JsonSetter public void setDueDate(LocalDate v) { supplied.add(Field.DUE_DATE);dueDate=v; }
    @JsonAnySetter public void rejectUnknownField(String name,Object value) { throw new IllegalArgumentException("Unsupported project field"); }
    @AssertTrue(message="At least one valid project field is required")
    public boolean isValidPatch() {
        if (supplied.isEmpty()) return false;
        if (has(Field.NAME) && (name==null || name.isBlank() || name.length()>200)) return false;
        return (!has(Field.DESCRIPTION)||description!=null) && (!has(Field.REQUIREMENTS)||requirements!=null)
                && (!has(Field.SCOPE)||scope!=null) && (!has(Field.DELIVERABLES)||deliverables!=null)
                && (!has(Field.SUCCESS_CRITERIA)||successCriteria!=null);
    }
    public boolean has(Field field) { return supplied.contains(field); }
    public String name() { return name; }
    public String description() { return description; }
    public String requirements() { return requirements; }
    public String scope() { return scope; }
    public String deliverables() { return deliverables; }
    public String successCriteria() { return successCriteria; }
    public LocalDate startDate() { return startDate; }
    public LocalDate dueDate() { return dueDate; }
}
