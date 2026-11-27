package com.eclectics.worksync.workitems.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "bug_details")
public class BugDetails {

    @Id
    @Column(name = "work_item_id")
    private UUID workItemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BugSeverity severity;

    @Column(name = "affected_area", nullable = false, length = 100)
    private String affectedArea;

    @Column(name = "reproduction_steps", nullable = false, columnDefinition = "TEXT")
    private String reproductionSteps;

    @Column(name = "expected_result", nullable = false, columnDefinition = "TEXT")
    private String expectedResult;

    @Column(name = "actual_result", nullable = false, columnDefinition = "TEXT")
    private String actualResult;

    @Column(name = "triage_outcome", length = 32)
    private String triageOutcome;

    @Column(name = "triage_reason", columnDefinition = "TEXT")
    private String triageReason;

    @Column(name = "fixer_id")
    private UUID fixerId;

    @Column(name = "verifier_id")
    private UUID verifierId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public BugDetails() {}

    public BugDetails(UUID workItemId, BugSeverity severity, String affectedArea,
                      String reproductionSteps, String expectedResult, String actualResult) {
        this.workItemId = Objects.requireNonNull(workItemId, "Work item ID is required");
        this.severity = Objects.requireNonNull(severity, "Severity is required");
        this.affectedArea = validateNonBlank(affectedArea, "Affected area");
        this.reproductionSteps = validateNonBlank(reproductionSteps, "Reproduction steps");
        this.expectedResult = validateNonBlank(expectedResult, "Expected result");
        this.actualResult = validateNonBlank(actualResult, "Actual result");

        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    private String validateNonBlank(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank");
        }
        return value.trim();
    }

    public void assignFixer(UUID fixerId) {
        validateFixerAndVerifier(fixerId, this.verifierId);
        this.fixerId = fixerId;
        this.updatedAt = Instant.now();
    }

    public void designateVerifier(UUID verifierId) {
        validateFixerAndVerifier(this.fixerId, verifierId);
        this.verifierId = verifierId;
        this.updatedAt = Instant.now();
    }

    public void recordTriage(String outcome, String reason) {
        this.triageOutcome = outcome;
        this.triageReason = reason;
        this.updatedAt = Instant.now();
    }

    private void validateFixerAndVerifier(UUID fixer, UUID verifier) {
        if (fixer != null && verifier != null && fixer.equals(verifier)) {
            throw new IllegalArgumentException("The verifier cannot be the fixer");
        }
    }

    // Getters
    public UUID getWorkItemId() { return workItemId; }
    public BugSeverity getSeverity() { return severity; }
    public String getAffectedArea() { return affectedArea; }
    public String getReproductionSteps() { return reproductionSteps; }
    public String getExpectedResult() { return expectedResult; }
    public String getActualResult() { return actualResult; }
    public String getTriageOutcome() { return triageOutcome; }
    public String getTriageReason() { return triageReason; }
    public UUID getFixerId() { return fixerId; }
    public UUID getVerifierId() { return verifierId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}