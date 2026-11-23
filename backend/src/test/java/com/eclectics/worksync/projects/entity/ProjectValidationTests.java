package com.eclectics.worksync.projects.entity;

import com.eclectics.worksync.projects.dto.CreateProjectRequest;
import com.eclectics.worksync.projects.exception.InvalidProjectMembershipException;
import jakarta.validation.Validation;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;

class ProjectValidationTests {
    @ParameterizedTest
    @CsvSource({"MANAGER,,true", "VIEWER,,true", "MEMBER,TECHNICAL_LEAD,true", "MEMBER,DEVELOPER,true",
            "MEMBER,QA_TESTER,true", "MEMBER,,false", "MANAGER,DEVELOPER,false", "VIEWER,QA_TESTER,false"})
    void validatesRoleResponsibilityCombination(ProjectRole role, ProjectResponsibility responsibility, boolean valid) {
        if (valid) assertThat(new ProjectMember(UUID.randomUUID(), UUID.randomUUID(), role, responsibility)
                .isRoleResponsibilityValid()).isTrue();
        else assertThatThrownBy(() -> new ProjectMember(UUID.randomUUID(), UUID.randomUUID(), role, responsibility))
                .isInstanceOf(InvalidProjectMembershipException.class);
    }
    @Test void ownerStartsAsCreatorWithoutPublicOwnerOrStatusSetters() {
        UUID creator = UUID.randomUUID();
        Project p = new Project(creator, "Project", null, null, null, null, null, null, null);
        assertThat(p.getOwnerUserId()).isEqualTo(creator);
        assertThat(p.getStatus()).isEqualTo(ProjectStatus.ACTIVE);
        assertThat(Project.class.getMethods()).noneMatch(m -> m.getName().equals("setOwnerUserId") || m.getName().equals("setStatus"));
    }
    @Test void validatesRequestAndEntityNamesAndDates() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(new CreateProjectRequest(" ", null, null, null, null, null, null, null))).isNotEmpty();
            assertThat(validator.validate(new CreateProjectRequest("x".repeat(201), null, null, null, null, null, null, null))).isNotEmpty();
            assertThat(validator.validate(new CreateProjectRequest("Valid", null, null, null, null, null, null, null))).isEmpty();
            assertThat(validator.validate(new CreateProjectRequest("Valid", null, null, null, null, null,
                    LocalDate.of(2026, 5, 2), LocalDate.of(2026, 5, 1)))).isNotEmpty();
            assertThat(validator.validate(new Project(UUID.randomUUID(), " ", null, null, null, null, null, null, null))).isNotEmpty();
        }
        assertThatThrownBy(() -> new Project(UUID.randomUUID(), "Valid", null, null, null, null, null,
                LocalDate.of(2026, 5, 2), LocalDate.of(2026, 5, 1))).isInstanceOf(IllegalArgumentException.class);
    }
}
