package com.eclectics.worksync.projects.controller;

import com.eclectics.worksync.auth.service.JwtService;
import com.eclectics.worksync.projects.dto.*;
import com.eclectics.worksync.projects.entity.*;
import com.eclectics.worksync.projects.exception.*;
import com.eclectics.worksync.projects.service.*;
import com.eclectics.worksync.users.dto.*;
import com.eclectics.worksync.users.entity.*;
import com.eclectics.worksync.users.service.UserService;
import jakarta.persistence.EntityManager;
import java.util.*;
import java.util.concurrent.*;
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
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"debug=false","logging.level.org.springframework=WARN"})
@AutoConfigureMockMvc @ActiveProfiles("test")
class ProjectOwnershipIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserService users;
    @Autowired ProjectService creation;
    @Autowired ProjectTeamService teams;
    @Autowired ProjectOwnershipService ownership;
    @Autowired PlatformTransactionManager manager;
    @Autowired EntityManager entityManager;
    @Autowired JwtService jwt;
    @MockitoBean JavaMailSender sender;
    UserResponse owner;
    UUID project;
    TransactionTemplate tx(){var t=new TransactionTemplate(manager);t.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);return t;}
    @BeforeEach void setup(){
        cleanup();owner=fixture(SystemRole.USER,AccountStatus.ACTIVE);
        var admin=fixture(SystemRole.ADMIN,AccountStatus.ACTIVE);
        users.updatePermissions(admin.id(),owner.id(),new UpdatePermissionsRequest(List.of(OrganizationPermission.PROJECT_CREATE)));
        project=creation.createProject(owner.id(),new CreateProjectRequest("Transfer",null,null,null,null,null,null,null)).id();
    }
    @AfterEach void cleanup(){tx().executeWithoutResult(t->{for(String table:List.of("bug_verifications","bug_details","work_item_links","work_items","organization_permissions","project_members","projects","refresh_tokens","password_reset_tokens","users"))jdbc.update("DELETE FROM "+table);});}
    UserResponse fixture(SystemRole role,AccountStatus status){
        var u=users.createPendingUser(new CreateUserCommand("Test","Person",UUID.randomUUID()+"@example.com","+254712345678","fixture-hash"));
        jdbc.update("UPDATE users SET role=?,status=? WHERE id=?",role.name(),status.name(),u.id());return users.getUserById(u.id());
    }
    UserResponse participant(ProjectRole role){var u=fixture(SystemRole.USER,AccountStatus.ACTIVE);teams.addMember(project,owner.id(),new AddProjectMemberRequest(u.id(),role,role==ProjectRole.MEMBER?ProjectResponsibility.DEVELOPER:null));return u;}
    String token(UserResponse u){return "Bearer "+jwt.generateToken(u.id().toString(),u.email(),"ADMIN","ACTIVE",Set.of("PROJECT_CREATE"));}
    String path(){return "/api/v1/projects/"+project+"/ownership-transfer";}
    String body(UUID target){return "{\"newOwnerUserId\":\""+target+"\"}";}
    UUID currentOwner(){return jdbc.queryForObject("SELECT owner_user_id FROM projects WHERE id=?",UUID.class,project);}

    @Test void transferPreservesBothManagerMembershipsAndRefreshesEffectivePermissions() throws Exception {
        var target=participant(ProjectRole.MANAGER);
        var before=jdbc.queryForMap("SELECT creator_user_id,created_at,status,name FROM projects WHERE id=?",project);
        var memberships=jdbc.queryForList("SELECT * FROM project_members WHERE project_id=? ORDER BY user_id",project);
        String actorToken=token(owner);String targetToken=token(target);
        mvc.perform(post(path()).header("Authorization",actorToken).contentType("application/json").content(body(target.id())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ownerUserId").value(target.id().toString()))
                .andExpect(jsonPath("$.creatorUserId").value(owner.id().toString())).andExpect(jsonPath("$.permissions.role").value("MANAGER"))
                .andExpect(jsonPath("$.permissions.isOwner").value(false))
                .andExpect(jsonPath("$.permissions.actions").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("TRANSFER_PROJECT_OWNERSHIP"))));
        assertThat(currentOwner()).isEqualTo(target.id());
        assertThat(jdbc.queryForList("SELECT * FROM project_members WHERE project_id=? ORDER BY user_id",project)).isEqualTo(memberships);
        assertThat(jdbc.queryForMap("SELECT creator_user_id,created_at,status,name FROM projects WHERE id=?",project)).isEqualTo(before);
        String permissions="/api/v1/projects/"+project+"/permissions/me";
        mvc.perform(get(permissions).header("Authorization",actorToken)).andExpect(status().isOk()).andExpect(jsonPath("$.isOwner").value(false))
                .andExpect(jsonPath("$.actions").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("ADD_PROJECT_MANAGER"))));
        mvc.perform(get(permissions).header("Authorization",targetToken)).andExpect(status().isOk()).andExpect(jsonPath("$.isOwner").value(true))
                .andExpect(jsonPath("$.actions").value(org.hamcrest.Matchers.hasItems("ADD_PROJECT_MANAGER","TRANSFER_PROJECT_OWNERSHIP")));
    }
    @ParameterizedTest @EnumSource(value=ProjectRole.class,names={"MEMBER","VIEWER"})
    void targetMustAlreadyBeManager(ProjectRole role) throws Exception {
        var target=participant(role);mvc.perform(post(path()).header("Authorization",token(owner)).contentType("application/json").content(body(target.id())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PROJECT_OWNERSHIP_CONFLICT"));assertThat(currentOwner()).isEqualTo(owner.id());
    }
    @ParameterizedTest @EnumSource(value=AccountStatus.class,names="ACTIVE",mode=EnumSource.Mode.EXCLUDE)
    void inactiveManagerCannotBecomeOwner(AccountStatus state) throws Exception {
        var target=participant(ProjectRole.MANAGER);jdbc.update("UPDATE users SET status=? WHERE id=?",state.name(),target.id());
        mvc.perform(post(path()).header("Authorization",token(owner)).contentType("application/json").content(body(target.id())))
                .andExpect(status().isConflict());assertThat(currentOwner()).isEqualTo(owner.id());
    }
    @Test void outsideProjectMissingUserAndSelfTargetsConflict() throws Exception {
        var outside=fixture(SystemRole.ADMIN,AccountStatus.ACTIVE);
        for(UUID target:List.of(outside.id(),UUID.randomUUID(),owner.id()))mvc.perform(post(path()).header("Authorization",token(owner)).contentType("application/json").content(body(target)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Ownership transfer conflicts with current eligibility"));
        assertThat(currentOwner()).isEqualTo(owner.id());
    }
    @ParameterizedTest @EnumSource(ProjectRole.class)
    void nonOwnerParticipantCannotTransfer(ProjectRole role) throws Exception {
        var actor=participant(role);var target=participant(ProjectRole.MANAGER);
        mvc.perform(post(path()).header("Authorization",token(actor)).contentType("application/json").content(body(target.id())))
                .andExpect(status().isForbidden());assertThat(currentOwner()).isEqualTo(owner.id());
    }
    @ParameterizedTest @EnumSource(SystemRole.class)
    void systemRoleOrCreateGrantCannotBypassOwnership(SystemRole role) throws Exception {
        var actor=fixture(role,AccountStatus.ACTIVE);var admin=fixture(SystemRole.ADMIN,AccountStatus.ACTIVE);
        users.updatePermissions(admin.id(),actor.id(),new UpdatePermissionsRequest(List.of(OrganizationPermission.PROJECT_CREATE)));
        var target=participant(ProjectRole.MANAGER);
        mvc.perform(post(path()).header("Authorization",token(actor)).contentType("application/json").content(body(target.id()))).andExpect(status().isNotFound());
        teams.addMember(project,owner.id(),new AddProjectMemberRequest(actor.id(),ProjectRole.MANAGER,null));
        mvc.perform(post(path()).header("Authorization",token(actor)).contentType("application/json").content(body(target.id()))).andExpect(status().isForbidden());
    }
    @ParameterizedTest @EnumSource(value=AccountStatus.class,names="ACTIVE",mode=EnumSource.Mode.EXCLUDE)
    void inactiveOwnerCannotTransferUsingExistingToken(AccountStatus state) throws Exception {
        var target=participant(ProjectRole.MANAGER);String token=token(owner);jdbc.update("UPDATE users SET status=? WHERE id=?",state.name(),owner.id());
        mvc.perform(post(path()).header("Authorization",token).contentType("application/json").content(body(target.id())))
                .andExpect(status().is(state==AccountStatus.PENDING_APPROVAL?403:401));
        assertThatThrownBy(()->ownership.transfer(project,owner.id(),new TransferOwnershipRequest(target.id()))).isInstanceOf(ProjectAccessDeniedException.class);
    }
    @Test void archivedProjectRejectsTransferAndOmitsTransferAction() throws Exception {
        var target=participant(ProjectRole.MANAGER);jdbc.update("UPDATE projects SET status='ARCHIVED' WHERE id=?",project);
        mvc.perform(post(path()).header("Authorization",token(owner)).contentType("application/json").content(body(target.id())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PROJECT_ARCHIVED"));
        mvc.perform(get("/api/v1/projects/"+project+"/permissions/me").header("Authorization",token(owner))).andExpect(status().isOk())
                .andExpect(jsonPath("$.actions").value(org.hamcrest.Matchers.containsInAnyOrder("VIEW_PROJECT","VIEW_PROJECT_TEAM")));
        assertThat(currentOwner()).isEqualTo(owner.id());
    }
    @ParameterizedTest @ValueSource(strings={"{}","{\"newOwnerUserId\":null}","{\"newOwnerUserId\":\"invalid-uuid\"}","{\"newOwnerUserId\":123}","{\"newOwnerUserId\":false}","{\"newOwnerUserId\":[]}","{\"newOwnerUserId\":{}}","{\"ownerUserId\":\"00000000-0000-0000-0000-000000000001\"}"})
    void invalidBodiesHaveSafe400AndPreserveOwner(String body) throws Exception {
        mvc.perform(post(path()).header("Authorization",token(owner)).contentType("application/json").content(body)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PROJECT_REQUEST")).andExpect(jsonPath("$.message").value("Invalid project request"));
        assertThat(currentOwner()).isEqualTo(owner.id());
    }
    @ParameterizedTest @ValueSource(strings={"AAAAAAAAAAAAAAAAAAAAAA==","AAAAAAAAAAAAAAAAAAAAAA",
            "00000000000000000000000000000000","1-1-1-1-1","{00000000-0000-0000-0000-000000000001}",
            "urn:uuid:00000000-0000-0000-0000-000000000001"," 00000000-0000-0000-0000-000000000001",
            "00000000-0000-0000-0000-000000000001 ","00000000-0000-0000-0000-00000000000g",""})
    void nonstandardUuidTextReturnsSafe400WithoutChangingAnyProjectFields(String value) throws Exception {
        var before=jdbc.queryForMap("SELECT * FROM projects WHERE id=?",project);
        String response=mvc.perform(post(path()).header("Authorization",token(owner)).contentType("application/json")
                .content("{\"newOwnerUserId\":\""+value+"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PROJECT_REQUEST"))
                .andExpect(jsonPath("$.message").value("Invalid project request")).andReturn().getResponse().getContentAsString();
        var error=tools.jackson.databind.json.JsonMapper.builder().build().readValue(response,Map.class);
        assertThat(error.keySet()).containsExactlyInAnyOrder("code","message","timestamp");
        assertThat(response).doesNotContain("Invalid project UUID","UUID.fromString","StrictProjectUuidDeserializer","stackTrace");
        assertThat(jdbc.queryForMap("SELECT * FROM projects WHERE id=?",project)).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void canonicalUuidTextInEitherHexCaseIsAccepted(boolean upperCase) throws Exception {
        var target=participant(ProjectRole.MANAGER);String value=target.id().toString();
        if(upperCase)value=value.toUpperCase(Locale.ROOT);
        mvc.perform(post(path()).header("Authorization",token(owner)).contentType("application/json")
                .content("{\"newOwnerUserId\":\""+value+"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ownerUserId").value(target.id().toString()));
        assertThat(currentOwner()).isEqualTo(target.id());
    }
    @Test void managerOfAnotherProjectCannotBecomeThisProjectsOwner() throws Exception {
        var target=fixture(SystemRole.USER,AccountStatus.ACTIVE);
        var other=creation.createProject(owner.id(),new CreateProjectRequest("Other project",null,null,null,null,null,null,null)).id();
        teams.addMember(other,owner.id(),new AddProjectMemberRequest(target.id(),ProjectRole.MANAGER,null));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project_members WHERE project_id=? AND user_id=?",Integer.class,project,target.id())).isZero();
        var otherBefore=jdbc.queryForMap("SELECT * FROM projects WHERE id=?",other);
        var membershipsBefore=jdbc.queryForList("SELECT * FROM project_members ORDER BY project_id,user_id");
        var firstBefore=jdbc.queryForMap("SELECT * FROM projects WHERE id=?",project);
        mvc.perform(post(path()).header("Authorization",token(owner)).contentType("application/json").content(body(target.id())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PROJECT_OWNERSHIP_CONFLICT"));
        assertThat(currentOwner()).isEqualTo(owner.id());
        assertThat(jdbc.queryForMap("SELECT * FROM projects WHERE id=?",project)).isEqualTo(firstBefore);
        assertThat(jdbc.queryForMap("SELECT * FROM projects WHERE id=?",other)).isEqualTo(otherBefore);
        assertThat(jdbc.queryForList("SELECT * FROM project_members ORDER BY project_id,user_id")).isEqualTo(membershipsBefore);
    }
    @Test void unknownFieldsAnonymousMalformedAndMissingProjectsAreRejected() throws Exception {
        var target=participant(ProjectRole.MANAGER);
        mvc.perform(post(path()).contentType("application/json").content(body(target.id()))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/projects/invalid-id/ownership-transfer").header("Authorization",token(owner)).contentType("application/json").content(body(target.id()))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/projects/"+UUID.randomUUID()+"/ownership-transfer").header("Authorization",token(owner)).contentType("application/json").content(body(target.id()))).andExpect(status().isNotFound());
        mvc.perform(post(path()).header("Authorization",token(owner)).contentType("application/json").content(body(target.id()).replace("}",",\"actorUserId\":\""+owner.id()+"\"}")))
                .andExpect(status().isBadRequest());
    }
    @Test void concurrentTransfersFromSameOwnerSerializeWithOnlyOneSuccess() throws Exception {
        var first=participant(ProjectRole.MANAGER);var second=participant(ProjectRole.MANAGER);var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var a=pool.submit(()->{await(start);return mvc.perform(post(path()).header("Authorization",token(owner)).contentType("application/json").content(body(first.id()))).andReturn().getResponse().getStatus();});
            var b=pool.submit(()->{await(start);return mvc.perform(post(path()).header("Authorization",token(owner)).contentType("application/json").content(body(second.id()))).andReturn().getResponse().getStatus();});start.countDown();
            assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,403);
        }
        assertThat(currentOwner()).isIn(first.id(),second.id());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project_members m JOIN projects p ON p.id=m.project_id AND p.owner_user_id=m.user_id WHERE p.id=? AND m.role='MANAGER'",Integer.class,project)).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project_members WHERE project_id=? AND role='MANAGER'",Integer.class,project)).isEqualTo(3);
    }
    @Test void failedOuterTransactionRollsBackOwnerChangeAndPreservesBothMemberships(){
        var target=participant(ProjectRole.MANAGER);var memberships=jdbc.queryForList("SELECT * FROM project_members WHERE project_id=? ORDER BY user_id",project);
        assertThatThrownBy(()->tx().executeWithoutResult(t->{ownership.transfer(project,owner.id(),new TransferOwnershipRequest(target.id()));throw new IllegalStateException("test rollback");})).isInstanceOf(IllegalStateException.class);
        assertThat(currentOwner()).isEqualTo(owner.id());assertThat(jdbc.queryForList("SELECT * FROM project_members WHERE project_id=? ORDER BY user_id",project)).isEqualTo(memberships);
    }
    @Test void refreshedTargetMembershipCannotUseCachedManagerRole(){
        var target=participant(ProjectRole.MANAGER);UUID id=jdbc.queryForObject("SELECT id FROM project_members WHERE project_id=? AND user_id=?",UUID.class,project,target.id());
        assertThatThrownBy(()->tx().executeWithoutResult(t->{
            entityManager.find(ProjectMember.class,id);
            jdbc.update("UPDATE project_members SET role='VIEWER' WHERE id=?",id);
            ownership.transfer(project,owner.id(),new TransferOwnershipRequest(target.id()));
        })).isInstanceOf(ProjectOwnershipConflictException.class);
        assertThat(currentOwner()).isEqualTo(owner.id());
    }
    @Test void targetBlockingWinningLockPreventsTransfer() throws Exception {
        var target=participant(ProjectRole.MANAGER);var held=new CountDownLatch(1);var release=new CountDownLatch(1);var attempting=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var block=pool.submit(()->tx().executeWithoutResult(t->{users.blockUser(target.id());held.countDown();await(release);}));await(held);
            var transfer=pool.submit(()->{attempting.countDown();return ownership.transfer(project,owner.id(),new TransferOwnershipRequest(target.id()));});
            try{await(attempting);assertThatThrownBy(()->transfer.get(200,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);}finally{release.countDown();}
            block.get(15,TimeUnit.SECONDS);assertThatThrownBy(()->transfer.get(15,TimeUnit.SECONDS)).hasCauseInstanceOf(ProjectOwnershipConflictException.class);
        }finally{release.countDown();}
        assertThat(currentOwner()).isEqualTo(owner.id());
    }
    static void await(CountDownLatch latch){try{assertThat(latch.await(15,TimeUnit.SECONDS)).isTrue();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}
}
