package com.eclectics.worksync.workitems.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BugDetailsTests {

    @Test
    @DisplayName("Initializes bug details with valid evidence fields")
    void initializesWithValidFields() {
        UUID workItemId = UUID.randomUUID();

        BugDetails bug = new BugDetails(
                workItemId, BugSeverity.HIGH, "Auth Module",
                "1. Click login\n2. Wait", "Redirects to dashboard", "Shows 500 error"
        );

        assertThat(bug.getWorkItemId()).isEqualTo(workItemId);
        assertThat(bug.getSeverity()).isEqualTo(BugSeverity.HIGH);
        assertThat(bug.getAffectedArea()).isEqualTo("Auth Module");
        assertThat(bug.getReproductionSteps()).contains("1. Click login");
        assertThat(bug.getCreatedAt()).isNotNull();
        assertThat(bug.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Rejects blank or missing evidence fields")
    void rejectsBlankEvidenceFields() {
        UUID workItemId = UUID.randomUUID();

        assertThatThrownBy(() -> new BugDetails(
                workItemId, BugSeverity.CRITICAL, "   ",
                "Steps", "Expected", "Actual"
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Affected area cannot be blank");

        assertThatThrownBy(() -> new BugDetails(
                workItemId, BugSeverity.CRITICAL, "UI",
                "   ", "Expected", "Actual"
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Reproduction steps cannot be blank");

        assertThatThrownBy(() -> new BugDetails(
                workItemId, null, "UI",
                "Steps", "Expected", "Actual"
        )).isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Severity is required");
    }

    @Test
    @DisplayName("Enforces rule: The verifier cannot be the fixer")
    void rejectsVerifierEqualsFixer() {
        UUID workItemId = UUID.randomUUID();
        UUID developerId = UUID.randomUUID();

        BugDetails bug = new BugDetails(
                workItemId, BugSeverity.MEDIUM, "Billing",
                "Steps", "Expected", "Actual"
        );

        // Assign developer as fixer
        bug.assignFixer(developerId);
        assertThat(bug.getFixerId()).isEqualTo(developerId);

        // Attempting to designate the same developer as verifier must fail
        assertThatThrownBy(() -> bug.designateVerifier(developerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The verifier cannot be the fixer");
    }

    @Test
    @DisplayName("Allows assigning separate users as fixer and verifier")
    void allowsSeparateFixerAndVerifier() {
        UUID fixerId = UUID.randomUUID();
        UUID verifierId = UUID.randomUUID();

        BugDetails bug = new BugDetails(
                UUID.randomUUID(), BugSeverity.LOW, "Settings",
                "Steps", "Expected", "Actual"
        );

        bug.assignFixer(fixerId);
        bug.designateVerifier(verifierId);

        assertThat(bug.getFixerId()).isEqualTo(fixerId);
        assertThat(bug.getVerifierId()).isEqualTo(verifierId);
    }

    @Test
    @DisplayName("Records triage outcome and reason")
    void recordsTriageSuccessfully() {
        BugDetails bug = new BugDetails(
                UUID.randomUUID(), BugSeverity.HIGH, "Checkout",
                "Steps", "Expected", "Actual"
        );

        bug.recordTriage("ACCEPTED", "Reproduced on staging environment");

        assertThat(bug.getTriageOutcome()).isEqualTo("ACCEPTED");
        assertThat(bug.getTriageReason()).isEqualTo("Reproduced on staging environment");
    }
}