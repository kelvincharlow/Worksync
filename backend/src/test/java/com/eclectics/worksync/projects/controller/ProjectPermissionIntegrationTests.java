package com.eclectics.worksync.projects.controller;

import com.eclectics.worksync.auth.service.JwtService;
import com.eclectics.worksync.projects.dto.*;
import com.eclectics.worksync.projects.entity.*;
import com.eclectics.worksync.projects.service.*;
import com.eclectics.worksync.users.dto.*;
import com.eclectics.worksync.users.entity.*;
import com.eclectics.worksync.users.service.UserService;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"debug=false","logging.level.org.springframework=WARN"})
@AutoConfigureMockMvc @ActiveProfiles("test")
class ProjectPermissionIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserService users;
    @Autowired ProjectService creation;
    @Autowired ProjectTeamService teams;
    @Autowired ProjectPermissionService permissions;
    @Autowired JwtService jwt;
    @Autowired org.springframework.transaction.PlatformTransactionManager manager;
    @MockitoBean JavaMailSender sender;
    UserResponse owner;
    UUID project;
    @BeforeEach void setup() {
        cleanup();owner=fixture(SystemRole.USER,AccountStatus.ACTIVE);
        var admin=fixture(SystemRole.ADMIN,AccountStatus.ACTIVE);
        users.updatePermissions(admin.id(),owner.id(),new UpdatePermissionsRequest(List.of(OrganizationPermission.PROJECT_CREATE)));
        project=creation.createProject(owner.id(),new CreateProjectRequest("Permissions",null,null,null,null,null,null,null)).id();
    }
    @AfterEach void cleanup() {
        new org.springframework.transaction.support.TransactionTemplate(manager).executeWithoutResult(t -> {
            for(String table:List.of("bug_verifications","bug_details","work_item_links","work_items","organization_permissions","project_members","projects","refresh_tokens","password_reset_tokens","users"))jdbc.update("DELETE FROM "+table);
        });
    }
    UserResponse fixture(SystemRole role,AccountStatus status) {
        var u=users.createPendingUser(new CreateUserCommand("Test","Participant",UUID.randomUUID()+"@example.com","+254712345678","fixture-hash"));
        jdbc.update("UPDATE users SET role=?,status=? WHERE id=?",role.name(),status.name(),u.id());return users.getUserById(u.id());
    }
    String token(UserResponse actor) {return "Bearer "+jwt.generateToken(actor.id().toString(),actor.email(),"ADMIN","ACTIVE",Set.of("PROJECT_CREATE"));}
    String path() {return "/api/v1/projects/"+project+"/permissions/me";}
    static java.util.stream.Stream<Arguments> memberships() {
        return java.util.stream.Stream.of(ProjectStatus.values()).flatMap(status -> java.util.stream.Stream.of(
                Arguments.of("OWNER",null,status),Arguments.of("MANAGER",null,status),
                Arguments.of("MEMBER",ProjectResponsibility.TECHNICAL_LEAD,status),
                Arguments.of("MEMBER",ProjectResponsibility.DEVELOPER,status),
                Arguments.of("MEMBER",ProjectResponsibility.QA_TESTER,status),Arguments.of("VIEWER",null,status)));
    }
    @ParameterizedTest @MethodSource("memberships") @SuppressWarnings("unchecked")
    void roleAndArchiveMatrixAdvertisesExactlyImplementedCapabilities(String label,ProjectResponsibility responsibility,ProjectStatus status) throws Exception {
        var actor=owner;ProjectRole role=label.equals("OWNER")?ProjectRole.MANAGER:ProjectRole.valueOf(label);
        boolean isOwner=label.equals("OWNER");
        if(!isOwner){actor=fixture(SystemRole.USER,AccountStatus.ACTIVE);teams.addMember(project,owner.id(),new AddProjectMemberRequest(actor.id(),role,responsibility));}
        if(status==ProjectStatus.ARCHIVED)jdbc.update("UPDATE projects SET status='ARCHIVED' WHERE id=?",project);
        String body=mvc.perform(get(path()).header("Authorization",token(actor))).andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value(role.name())).andExpect(jsonPath("$.isOwner").value(isOwner))
                .andExpect(jsonPath("$.responsibility").hasJsonPath()).andReturn().getResponse().getContentAsString();
        Map<String,Object> result=tools.jackson.databind.json.JsonMapper.builder().build().readValue(body,Map.class);
        assertThat(result.get("responsibility")).isEqualTo(responsibility==null?null:responsibility.name());
        Set<String> expected=new HashSet<>(Set.of("VIEW_PROJECT"));
        if(role!=ProjectRole.VIEWER)expected.add("VIEW_PROJECT_TEAM");
        if(status==ProjectStatus.ACTIVE && role==ProjectRole.MANAGER) {
            expected.addAll(Set.of("UPDATE_PROJECT_DETAILS","SELECT_ELIGIBLE_USERS","ADD_PROJECT_MEMBER"));
            if(isOwner)expected.add("ADD_PROJECT_MANAGER");
        }
        assertThat((List<String>)result.get("actions")).containsExactlyInAnyOrderElementsOf(expected).doesNotHaveDuplicates();
        if(role==ProjectRole.VIEWER) {
            assertThat(result.keySet()).containsExactlyInAnyOrder("role","responsibility","isOwner","actions","availableDeliveryFeatures");
            assertThat((List<?>)result.get("availableDeliveryFeatures")).isEmpty();
            assertThat(body).doesNotContain("configuration","enabledViews","enabledWorkTypes","password","email","ownerUserId","creatorUserId");
        } else {
            assertThat(result.keySet()).containsExactlyInAnyOrder("role","responsibility","isOwner","actions","configuration");
            assertThat((Map<String,Object>)result.get("configuration")).containsOnlyKeys("enabledViews","enabledWorkTypes")
                    .containsEntry("enabledViews",List.of()).containsEntry("enabledWorkTypes",List.of());
        }
        // Existing detail responses use the same calculation; no inconsistent stale action list.
        if(role!=ProjectRole.VIEWER) {
            String detail=mvc.perform(get("/api/v1/projects/"+project).header("Authorization",token(actor))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            var document=tools.jackson.databind.json.JsonMapper.builder().build().readValue(detail,Map.class);
            assertThat((List<String>)((Map<String,Object>)document.get("permissions")).get("actions")).containsExactlyInAnyOrderElementsOf(expected);
        }
    }
    @ParameterizedTest @EnumSource(SystemRole.class)
    void systemRoleOrOrganizationGrantNeverReplaceMembership(SystemRole role) throws Exception {
        var actor=fixture(role,AccountStatus.ACTIVE);
        mvc.perform(get(path()).header("Authorization",token(actor))).andExpect(status().isNotFound());
        var admin=fixture(SystemRole.ADMIN,AccountStatus.ACTIVE);
        users.updatePermissions(admin.id(),actor.id(),new UpdatePermissionsRequest(List.of(OrganizationPermission.PROJECT_CREATE)));
        mvc.perform(get(path()).header("Authorization",token(actor))).andExpect(status().isNotFound());
    }
    @ParameterizedTest @EnumSource(value=AccountStatus.class,names="ACTIVE",mode=EnumSource.Mode.EXCLUDE)
    void inactiveAccountsCannotUseExistingToken(AccountStatus status) throws Exception {
        String token=token(owner);jdbc.update("UPDATE users SET status=? WHERE id=?",status.name(),owner.id());
        mvc.perform(get(path()).header("Authorization",token)).andExpect(status().is(status==AccountStatus.PENDING_APPROVAL?403:401));
        assertThatThrownBy(() -> permissions.get(project,owner.id())).isInstanceOf(com.eclectics.worksync.projects.exception.ProjectNotFoundException.class);
    }
    @Test void membershipInOneProjectCannotRevealAnotherProjectsPermissions() throws Exception {
        var actor=fixture(SystemRole.USER,AccountStatus.ACTIVE);
        teams.addMember(project,owner.id(),new AddProjectMemberRequest(actor.id(),ProjectRole.MEMBER,ProjectResponsibility.DEVELOPER));
        var hidden=creation.createProject(owner.id(),new CreateProjectRequest("Hidden Project B","Private brief B",
                "Private requirements B",null,null,null,null,null));
        String token=token(actor);
        mvc.perform(get(path()).header("Authorization",token)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project_members WHERE project_id=? AND user_id=?",
                Integer.class,hidden.id(),actor.id())).isZero();
        String body=mvc.perform(get("/api/v1/projects/"+hidden.id()+"/permissions/me").header("Authorization",token))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Project not found")).andReturn().getResponse().getContentAsString();
        var error=tools.jackson.databind.json.JsonMapper.builder().build().readValue(body,Map.class);
        assertThat(error.keySet()).containsExactlyInAnyOrder("code","message","timestamp");
        assertThat(body).doesNotContain("Hidden Project B","Private brief B","Private requirements B",
                hidden.id().toString(),owner.id().toString(),"configuration","enabledViews","enabledWorkTypes",
                "actions","responsibility","members","ownerUserId","creatorUserId");
    }
    @Test void removedMembershipImmediatelyRevokesPermissionsForExistingToken() throws Exception {
        var actor=fixture(SystemRole.USER,AccountStatus.ACTIVE);teams.addMember(project,owner.id(),new AddProjectMemberRequest(actor.id(),ProjectRole.MEMBER,ProjectResponsibility.DEVELOPER));
        String token=token(actor);mvc.perform(get(path()).header("Authorization",token)).andExpect(status().isOk());
        jdbc.update("DELETE FROM project_members WHERE project_id=? AND user_id=?",project,actor.id());
        mvc.perform(get(path()).header("Authorization",token)).andExpect(status().isNotFound());
    }
    @Test void missingMalformedIdsAndAnonymousRequestsReturnSafeErrors() throws Exception {
        mvc.perform(get(path())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/projects/"+UUID.randomUUID()+"/permissions/me").header("Authorization",token(owner)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
        mvc.perform(get("/api/v1/projects/invalid-id/permissions/me").header("Authorization",token(owner)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Invalid project request"));
    }
    @Test void currentOwnerDesignationOverridesPreviouslyReturnedOwnerHints() throws Exception {
        var manager=fixture(SystemRole.USER,AccountStatus.ACTIVE);
        teams.addMember(project,owner.id(),new AddProjectMemberRequest(manager.id(),ProjectRole.MANAGER,null));
        String oldToken=token(owner);String managerToken=token(manager);
        mvc.perform(get(path()).header("Authorization",oldToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$.actions").value(org.hamcrest.Matchers.hasItem("ADD_PROJECT_MANAGER")));
        // Test-only SQL simulates a later valid Owner designation; no transfer endpoint is added.
        jdbc.update("UPDATE projects SET owner_user_id=? WHERE id=?",manager.id(),project);
        mvc.perform(get(path()).header("Authorization",oldToken)).andExpect(status().isOk()).andExpect(jsonPath("$.isOwner").value(false))
                .andExpect(jsonPath("$.actions").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("ADD_PROJECT_MANAGER"))));
        mvc.perform(get(path()).header("Authorization",managerToken)).andExpect(status().isOk()).andExpect(jsonPath("$.isOwner").value(true))
                .andExpect(jsonPath("$.actions").value(org.hamcrest.Matchers.hasItem("ADD_PROJECT_MANAGER")));
    }
    @Test void currentMembershipWinsOverStaleHintsAndJwtClaims() throws Exception {
        var actor=fixture(SystemRole.USER,AccountStatus.ACTIVE);teams.addMember(project,owner.id(),new AddProjectMemberRequest(actor.id(),ProjectRole.MANAGER,null));
        String token=token(actor);
        mvc.perform(get(path()).header("Authorization",token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.actions").value(org.hamcrest.Matchers.hasItem("UPDATE_PROJECT_DETAILS")));
        jdbc.update("UPDATE project_members SET role='VIEWER',responsibility=null WHERE project_id=? AND user_id=?",project,actor.id());
        mvc.perform(get(path()).header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("VIEWER"))
                .andExpect(jsonPath("$.actions").value(org.hamcrest.Matchers.contains("VIEW_PROJECT"))).andExpect(jsonPath("$.configuration").doesNotExist());
        mvc.perform(patch("/api/v1/projects/"+project).header("Authorization",token).contentType("application/json").content("{\"name\":\"Denied\"}"))
                .andExpect(status().isForbidden());
    }
}
