package com.eclectics.worksync.workitems.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "bug_verifications")
public class BugVerification {

    @Id
    private UUID id;

    @Column(name = "bug_id", nullable = false)
    private UUID bugId;

    @Column(name = "verifier_id", nullable = false)
    private UUID verifierId;

    @Column(nullable = false, length = 16)
    private String result;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "verified_at", nullable = false)
    private Instant verifiedAt;

    public BugVerification() {}

    public BugVerification(UUID id, UUID bugId, UUID verifierId, 
                           String result, String notes) {
        this.id = id != null ? id : UUID.randomUUID();
        this.bugId = Objects.requireNonNull(bugId, "Bug ID is required");
        this.verifierId = Objects.requireNonNull(verifierId, "Verifier ID is required");
        this.result = validateResult(result);
        this.notes = notes;
        this.verifiedAt = Instant.now();
    }

    private String validateResult(String rawResult) {
        if (rawResult == null || (!rawResult.equalsIgnoreCase("PASS") 
                && !rawResult.equalsIgnoreCase("FAIL"))) {
            throw new IllegalArgumentException("Verification result must be PASS or FAIL");
        }
        return rawResult.toUpperCase();
    }

    // Getters
    public UUID getId() { return id; }
    public UUID getBugId() { return bugId; }
    public UUID getVerifierId() { return verifierId; }
    public String getResult() { return result; }
    public String getNotes() { return notes; }
    public Instant getVerifiedAt() { return verifiedAt; }
}