package com.eclectics.worksync.projects.controller;

import com.eclectics.worksync.auth.service.JwtService;
import com.eclectics.worksync.projects.dto.*;
import com.eclectics.worksync.projects.entity.*;
import com.eclectics.worksync.projects.exception.ProjectMembershipConflictException;
import com.eclectics.worksync.projects.service.*;
import com.eclectics.worksync.users.dto.*;
import com.eclectics.worksync.users.entity.*;
import com.eclectics.worksync.users.service.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
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
class ProjectTeamIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserService users;
    @Autowired UserDirectoryService directory;
    @Autowired ProjectService projects;
    @Autowired ProjectTeamService teams;
    @Autowired JwtService jwt;
    @Autowired PlatformTransactionManager manager;
    @MockitoBean JavaMailSender sender;
    UserResponse owner;
    UUID project;
    TransactionTemplate tx() { var t=new TransactionTemplate(manager);t.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);return t; }
    @BeforeEach void setup() {
        cleanup(); owner=fixture("Owner",AccountStatus.ACTIVE,SystemRole.USER);
        var admin=fixture("Admin",AccountStatus.ACTIVE,SystemRole.ADMIN);
        users.updatePermissions(admin.id(),owner.id(),new UpdatePermissionsRequest(List.of(OrganizationPermission.PROJECT_CREATE)));
        project=projects.createProject(owner.id(),new CreateProjectRequest("Team",null,null,null,null,null,null,null)).id();
    }
    @AfterEach void cleanup() {
        tx().executeWithoutResult(t -> {
            for (String table:List.of("bug_verifications","bug_details","work_item_links","work_items","organization_permissions",
                    "project_members","projects","refresh_tokens","password_reset_tokens","users")) jdbc.update("DELETE FROM "+table);
        });
    }
    UserResponse fixture(String name,AccountStatus state,SystemRole role) {
        var u=users.createPendingUser(new CreateUserCommand(name,"Person",UUID.randomUUID()+"@example.com","+254712345678","fixture-hash"));
        jdbc.update("UPDATE users SET role=?,status=? WHERE id=?",role.name(),state.name(),u.id());
        return users.getUserById(u.id());
    }
    UserResponse candidate(String name) {return fixture(name,AccountStatus.ACTIVE,SystemRole.USER);}
    String token(UserResponse u) {return "Bearer "+jwt.generateToken(u.id().toString(),u.email(),"ADMIN","ACTIVE",Set.of("PROJECT_CREATE"));}
    String base() {return "/api/v1/projects/"+project;}
    String body(UUID id,String role,String responsibility) {
        return "{\"userId\":\""+id+"\",\"role\":\""+role+"\",\"responsibility\":"+(responsibility==null?"null":"\""+responsibility+"\"")+"}";
    }
    void seed(UserResponse u,ProjectRole role,ProjectResponsibility responsibility) {
        teams.addMember(project,owner.id(),new AddProjectMemberRequest(u.id(),role,responsibility));
    }
    int memberCount() {return jdbc.queryForObject("SELECT count(*) FROM project_members WHERE project_id=?",Integer.class,project);}

    @Test void eligibleSearchExcludesMembersAndInactiveUsersBeforePagination() throws Exception {
        var a=candidate("Candidate A");var b=candidate("Candidate B");var existing=candidate("Candidate Member");seed(existing,ProjectRole.MEMBER,ProjectResponsibility.DEVELOPER);
        for(var state:List.of(AccountStatus.BLOCKED,AccountStatus.REJECTED,AccountStatus.PENDING_APPROVAL))fixture("Candidate Hidden",state,SystemRole.USER);
        mvc.perform(get(base()+"/eligible-users").header("Authorization",token(owner)).param("search","  cAnDiDaTe ").param("size","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(1)).andExpect(jsonPath("$.content[0].id").value(a.id().toString()))
                .andExpect(jsonPath("$.content[0].phoneNumber").doesNotExist()).andExpect(jsonPath("$.content[0].status").doesNotExist())
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());
        mvc.perform(get(base()+"/eligible-users").header("Authorization",token(owner)).param("search","candidate").param("size","1").param("page","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.content[0].id").value(b.id().toString()));
        mvc.perform(get(base()+"/eligible-users").header("Authorization",token(owner)).param("search",b.email().toUpperCase()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get(base()+"/eligible-users").header("Authorization",token(owner)).param("search","candidate").param("page","5"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.content").isEmpty());
    }
    @Test void literalSearchDoesNotTreatPercentOrUnderscoreAsWildcards() throws Exception {
        candidate("Normal");
        mvc.perform(get(base()+"/eligible-users").header("Authorization",token(owner)).param("search","%_"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }
    @ParameterizedTest @CsvSource({"MANAGER,", "MEMBER,TECHNICAL_LEAD", "MEMBER,DEVELOPER", "MEMBER,QA_TESTER", "VIEWER,"})
    void ownerAddsAllValidRoleCombinations(String role,String responsibility) throws Exception {
        var target=candidate("Selected");
        mvc.perform(post(base()+"/members").header("Authorization",token(owner)).contentType("application/json").content(body(target.id(),role,responsibility)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.userId").value(target.id().toString()))
                .andExpect(jsonPath("$.displayName").value("Selected Person")).andExpect(jsonPath("$.role").value(role))
                .andExpect(jsonPath("$.isOwner").value(false)).andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.id").doesNotExist()).andExpect(jsonPath("$.projectId").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        assertThat(memberCount()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT owner_user_id FROM projects WHERE id=?",UUID.class,project)).isEqualTo(owner.id());
    }
    @ParameterizedTest @CsvSource({"MEMBER,DEVELOPER", "VIEWER,"})
    void additionalManagerAddsMembersAndViewers(String role,String responsibility) throws Exception {
        var manager=candidate("Manager");seed(manager,ProjectRole.MANAGER,null);var target=candidate("Target");
        mvc.perform(post(base()+"/members").header("Authorization",token(manager)).contentType("application/json").content(body(target.id(),role,responsibility)))
                .andExpect(status().isCreated());
        mvc.perform(get(base()+"/eligible-users").header("Authorization",token(manager))).andExpect(status().isOk());
    }
    @Test void additionalManagerCannotAddManager() throws Exception {
        var manager=candidate("Manager");seed(manager,ProjectRole.MANAGER,null);var target=candidate("Target");
        mvc.perform(post(base()+"/members").header("Authorization",token(manager)).contentType("application/json").content(body(target.id(),"MANAGER",null)))
                .andExpect(status().isForbidden());assertThat(memberCount()).isEqualTo(2);
    }
    @ParameterizedTest @CsvSource({"MEMBER,TECHNICAL_LEAD", "MEMBER,DEVELOPER", "MEMBER,QA_TESTER", "VIEWER,"})
    void ordinaryParticipantsCannotSearchOrAdd(String role,String responsibility) throws Exception {
        var actor=candidate("Actor");seed(actor,ProjectRole.valueOf(role),responsibility==null?null:ProjectResponsibility.valueOf(responsibility));var target=candidate("Target");
        mvc.perform(get(base()+"/eligible-users").header("Authorization",token(actor))).andExpect(status().isForbidden());
        mvc.perform(post(base()+"/members").header("Authorization",token(actor)).contentType("application/json").content(body(target.id(),"VIEWER",null)))
                .andExpect(status().isForbidden());
        mvc.perform(get(base()+"/members").header("Authorization",token(actor)))
                .andExpect(status().is(role.equals("VIEWER")?403:200));
    }
    @ParameterizedTest @EnumSource(SystemRole.class)
    void outsidersCannotDiscoverDirectoryOrTeamEvenWithCreatePermission(SystemRole role) throws Exception {
        var actor=fixture("Outsider",AccountStatus.ACTIVE,role);var admin=fixture("Granter",AccountStatus.ACTIVE,SystemRole.ADMIN);
        users.updatePermissions(admin.id(),actor.id(),new UpdatePermissionsRequest(List.of(OrganizationPermission.PROJECT_CREATE)));
        var target=candidate("Target");
        mvc.perform(get(base()+"/eligible-users").header("Authorization",token(actor))).andExpect(status().isNotFound());
        mvc.perform(get(base()+"/members").header("Authorization",token(actor))).andExpect(status().isNotFound());
        mvc.perform(post(base()+"/members").header("Authorization",token(actor)).contentType("application/json").content(body(target.id(),"VIEWER",null)))
                .andExpect(status().isNotFound());
    }
    @Test void noPublicDirectoryAndAdminDirectoryRemainsRestricted() throws Exception {
        mvc.perform(get(base()+"/eligible-users")).andExpect(status().isUnauthorized());
        mvc.perform(get(base()+"/members")).andExpect(status().isUnauthorized());
        mvc.perform(post(base()+"/members").contentType("application/json").content("{}" )).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/users").header("Authorization",token(owner))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/users/active").header("Authorization",token(owner))).andExpect(status().isNotFound());
    }
    @ParameterizedTest @ValueSource(strings={"{}", "{\"role\":\"MEMBER\"}","{\"role\":\"MANAGER\",\"responsibility\":\"DEVELOPER\"}","{\"role\":\"VIEWER\",\"responsibility\":\"QA_TESTER\"}","{\"role\":\"WRONG\"}","{\"role\":null}","{\"role\":\"VIEWER\",\"ownerUserId\":\"00000000-0000-0000-0000-000000000001\"}"})
    void invalidRoleResponsibilityOrImpersonationRejected(String fields) throws Exception {
        var target=candidate("Target");String json="{\"userId\":\""+target.id()+"\""+(fields.length()>2?","+fields.substring(1):"}");
        if(fields.equals("{}"))json="{}";
        mvc.perform(post(base()+"/members").header("Authorization",token(owner)).contentType("application/json").content(json))
                .andExpect(status().isBadRequest());assertThat(memberCount()).isOne();
    }
    @ParameterizedTest @EnumSource(value=AccountStatus.class,names="ACTIVE",mode=EnumSource.Mode.EXCLUDE)
    void inactiveTargetsCannotBeAdded(AccountStatus status) throws Exception {
        var target=fixture("Inactive",status,SystemRole.USER);
        mvc.perform(post(base()+"/members").header("Authorization",token(owner)).contentType("application/json").content(body(target.id(),"VIEWER",null)))
                .andExpect(status().isConflict());assertThat(memberCount()).isOne();
    }
    @ParameterizedTest @ValueSource(strings={"{\"role\":0}","{\"role\":true}","{\"role\":\"MEMBER\",\"responsibility\":0}"})
    void numericAndBooleanEnumsAreRejected(String fields) throws Exception {
        var target=candidate("Target");
        String json="{\"userId\":\""+target.id()+"\","+fields.substring(1);
        mvc.perform(post(base()+"/members").header("Authorization",token(owner)).contentType("application/json").content(json))
                .andExpect(status().isBadRequest());assertThat(memberCount()).isOne();
    }
    @Test void duplicateNameLookupUsesUuidTieBreakerAndMembershipExclusionIsProjectScoped() throws Exception {
        var first=candidate("Same Name");var second=candidate("Same Name");
        var other=projects.createProject(owner.id(),new CreateProjectRequest("Other",null,null,null,null,null,null,null)).id();
        teams.addMember(other,owner.id(),new AddProjectMemberRequest(first.id(),ProjectRole.VIEWER,null));
        List<UUID> order=jdbc.queryForList("SELECT id FROM users WHERE first_name='Same Name' ORDER BY lower(last_name),lower(first_name),id",UUID.class);
        for(int page=0;page<2;page++)mvc.perform(get(base()+"/eligible-users").header("Authorization",token(owner)).param("search","same name").param("size","1").param("page",String.valueOf(page)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.content[0].id").value(order.get(page).toString()));
        assertThat(second.id()).isNotEqualTo(first.id());
    }
    @Test void missingTargetAndDuplicatesHaveSafeConflicts() throws Exception {
        mvc.perform(post(base()+"/members").header("Authorization",token(owner)).contentType("application/json").content(body(UUID.randomUUID(),"VIEWER",null)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PROJECT_MEMBERSHIP_CONFLICT"));
        mvc.perform(post(base()+"/members").header("Authorization",token(owner)).contentType("application/json").content(body(owner.id(),"MANAGER",null)))
                .andExpect(status().isConflict());assertThat(memberCount()).isOne();
    }
    @Test void teamListingIsPagedStableAndMarksOwnerWithoutContactData() throws Exception {
        var target=candidate("Developer");seed(target,ProjectRole.MEMBER,ProjectResponsibility.DEVELOPER);
        jdbc.update("UPDATE project_members SET created_at='2026-01-01T00:00:00Z' WHERE project_id=?",project);
        List<UUID> order=jdbc.queryForList("SELECT user_id FROM project_members WHERE project_id=? ORDER BY created_at DESC,user_id DESC",UUID.class,project);
        for(int page=0;page<2;page++) {
            mvc.perform(get(base()+"/members").header("Authorization",token(owner)).param("size","1").param("page",String.valueOf(page)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.content[0].userId").value(order.get(page).toString()))
                    .andExpect(jsonPath("$.content[0].isOwner").value(order.get(page).equals(owner.id())))
                    .andExpect(jsonPath("$.content[0].email").doesNotExist()).andExpect(jsonPath("$.content[0].phoneNumber").doesNotExist());
        }
        users.blockUser(target.id());
        mvc.perform(get(base()+"/members").header("Authorization",token(owner))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
    }
    @Test void archivedProjectRejectsLookupAndAddButAllowsInternalTeamRead() throws Exception {
        var target=candidate("Target");jdbc.update("UPDATE projects SET status='ARCHIVED' WHERE id=?",project);
        mvc.perform(get(base()+"/eligible-users").header("Authorization",token(owner))).andExpect(status().isConflict());
        mvc.perform(post(base()+"/members").header("Authorization",token(owner)).contentType("application/json").content(body(target.id(),"VIEWER",null)))
                .andExpect(status().isConflict());
        mvc.perform(get(base()+"/members").header("Authorization",token(owner))).andExpect(status().isOk());
    }
    @Test void missingProjectsAndBadPaginationReturnSafeErrors() throws Exception {
        mvc.perform(get("/api/v1/projects/"+UUID.randomUUID()+"/members").header("Authorization",token(owner))).andExpect(status().isNotFound());
        for(String path:List.of("/members","/eligible-users")) {
            mvc.perform(get(base()+path).header("Authorization",token(owner)).param("size","101")).andExpect(status().isBadRequest());
            mvc.perform(get(base()+path).header("Authorization",token(owner)).param("page","-1")).andExpect(status().isBadRequest());
        }
        mvc.perform(get(base()+"/eligible-users").header("Authorization",token(owner)).param("search","a".repeat(101))).andExpect(status().isBadRequest());
    }
    @ParameterizedTest @ValueSource(strings={"/members","/eligible-users"})
    void paginationBoundariesReturnSafeErrorsOrSupportedEmptyPages(String path) throws Exception {
        for (String[] values : List.of(new String[]{"2147483647","100"},new String[]{"21474837","100"},
                new String[]{"-1","20"},new String[]{"0","0"},new String[]{"0","101"})) {
            mvc.perform(get(base()+path).header("Authorization",token(owner)).param("page",values[0]).param("size",values[1]))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PROJECT_REQUEST"))
                    .andExpect(jsonPath("$.message").value("Invalid project request"));
        }
        mvc.perform(get(base()+path).header("Authorization",token(owner)).param("page","2147483647").param("size","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(2147483647))
                .andExpect(jsonPath("$.size").value(1)).andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get(base()+path).header("Authorization",token(owner)).param("page","0").param("size","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1));
    }
    @ParameterizedTest @ValueSource(strings={"/members","/eligible-users"})
    void malformedProjectIdsReturnSafeBadRequests(String path) throws Exception {
        mvc.perform(get("/api/v1/projects/not-a-uuid"+path).header("Authorization",token(owner)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PROJECT_REQUEST"))
                .andExpect(jsonPath("$.message").value("Invalid project request"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("not-a-uuid"))));
    }
    @Test void malformedMemberIdsAndAddProjectPathHaveSafeBadRequests() throws Exception {
        mvc.perform(post(base()+"/members").header("Authorization",token(owner)).contentType("application/json")
                .content("{\"userId\":\"private-invalid-id\",\"role\":\"VIEWER\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PROJECT_REQUEST"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-invalid-id"))));
        mvc.perform(post("/api/v1/projects/not-a-uuid/members").header("Authorization",token(owner)).contentType("application/json")
                .content(body(UUID.randomUUID(),"VIEWER",null)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Invalid project request"));
        assertThat(memberCount()).isOne();
    }
    @Test void concurrentDuplicateAdditionsCommitExactlyOneMembership() throws Exception {
        var target=candidate("Target");var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Integer> call=() -> {await(start);return mvc.perform(post(base()+"/members").header("Authorization",token(owner)).contentType("application/json").content(body(target.id(),"VIEWER",null))).andReturn().getResponse().getStatus();};
            var first=pool.submit(call);var second=pool.submit(call);start.countDown();
            assertThat(List.of(first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);
        } assertThat(memberCount()).isEqualTo(2);
    }
    @Test void blockingWinningLockPreventsWaitingMembership() throws Exception {
        var target=candidate("Target");var held=new CountDownLatch(1);var release=new CountDownLatch(1);var attempting=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var block=pool.submit(() -> tx().executeWithoutResult(t -> {users.blockUser(target.id());held.countDown();await(release);}));
            await(held);
            var add=pool.submit(() -> {attempting.countDown();return teams.addMember(project,owner.id(),new AddProjectMemberRequest(target.id(),ProjectRole.VIEWER,null));});
            try {await(attempting);assertThatThrownBy(() -> add.get(200,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);} finally {release.countDown();}
            block.get(15,TimeUnit.SECONDS);assertThatThrownBy(() -> add.get(15,TimeUnit.SECONDS)).hasCauseInstanceOf(ProjectMembershipConflictException.class);
        } finally {release.countDown();}
        assertThat(memberCount()).isOne();
    }
    @Test void membershipWinningLockCommitsBeforeBlockingCanComplete() throws Exception {
        var target=candidate("Target");var inserted=new CountDownLatch(1);var release=new CountDownLatch(1);var attempting=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var add=pool.submit(() -> tx().execute(t -> {var member=teams.addMember(project,owner.id(),new AddProjectMemberRequest(target.id(),ProjectRole.VIEWER,null));inserted.countDown();await(release);return member;}));
            await(inserted);
            var block=pool.submit(() -> {attempting.countDown();users.blockUser(target.id());});
            try {await(attempting);assertThatThrownBy(() -> block.get(200,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);} finally {release.countDown();}
            add.get(15,TimeUnit.SECONDS);block.get(15,TimeUnit.SECONDS);
        } finally {release.countDown();}
        assertThat(memberCount()).isEqualTo(2);assertThat(users.getUserById(target.id()).status()).isEqualTo(AccountStatus.BLOCKED);
    }
    @Test void usersLookupIsBoundedReadOnlyAndCaseInsensitive() {
        var a=candidate("Lookup A");var b=candidate("Lookup B");fixture("Lookup Hidden",AccountStatus.BLOCKED,SystemRole.USER);
        var page=directory.searchActive(" LOOKUP ",PageRequest.of(0,1));
        assertThat(page.getTotalElements()).isEqualTo(2);assertThat(page.getContent()).extracting(UserSelection::id).containsExactly(a.id());
        assertThat(directory.searchActive(b.email().toUpperCase(),PageRequest.of(0,10)).getContent()).extracting(UserSelection::id).containsExactly(b.id());
        assertThatThrownBy(() -> directory.searchActive("",PageRequest.of(0,101))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void usersMembershipGuardRequiresWritableReadCommittedOuterTransaction() {
        assertThatThrownBy(() -> directory.lockActiveActorForMembershipWrite(owner.id()))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        var readOnly=tx(); readOnly.setReadOnly(true);
        assertThatThrownBy(() -> readOnly.executeWithoutResult(t -> directory.lockActiveActorForMembershipWrite(owner.id())))
                .isInstanceOf(com.eclectics.worksync.users.exception.PermissionTransactionRequiredException.class);
        var snapshot=tx(); snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        assertThatThrownBy(() -> snapshot.executeWithoutResult(t -> directory.lockActiveActorForMembershipWrite(owner.id())))
                .isInstanceOf(com.eclectics.worksync.users.exception.PermissionTransactionRequiredException.class);
    }
    @Test void actorBlockedWhileWaitingCannotAddMembership() throws Exception {
        var target=candidate("Target");var held=new CountDownLatch(1);var release=new CountDownLatch(1);var attempting=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var block=pool.submit(() -> tx().executeWithoutResult(t -> {users.blockUser(owner.id());held.countDown();await(release);}));
            await(held);
            var add=pool.submit(() -> {attempting.countDown();return teams.addMember(project,owner.id(),new AddProjectMemberRequest(target.id(),ProjectRole.VIEWER,null));});
            try {await(attempting);assertThatThrownBy(() -> add.get(200,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);} finally {release.countDown();}
            block.get(15,TimeUnit.SECONDS);
            assertThatThrownBy(() -> add.get(15,TimeUnit.SECONDS)).hasCauseInstanceOf(com.eclectics.worksync.projects.exception.ProjectAccessDeniedException.class);
        } finally {release.countDown();}
        assertThat(memberCount()).isOne();
    }
    static void await(CountDownLatch latch) {
        try {assertThat(latch.await(15,TimeUnit.SECONDS)).isTrue();} catch(InterruptedException e) {Thread.currentThread().interrupt();throw new IllegalStateException(e);}
    }
}
