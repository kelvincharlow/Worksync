package com.eclectics.worksync.projects.controller;

import com.eclectics.worksync.auth.service.JwtService;
import com.eclectics.worksync.projects.dto.*;
import com.eclectics.worksync.projects.entity.*;
import com.eclectics.worksync.projects.service.*;
import com.eclectics.worksync.users.dto.*;
import com.eclectics.worksync.users.entity.*;
import com.eclectics.worksync.users.service.UserService;
import java.time.*;
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
class ProjectDetailsIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserService users;
    @Autowired ProjectService creation;
    @Autowired ProjectDetailsService details;
    @Autowired ProjectTeamService teams;
    @Autowired JwtService jwt;
    @Autowired PlatformTransactionManager manager;
    @MockitoBean JavaMailSender sender;
    UserResponse owner;
    UUID project;
    TransactionTemplate tx() {var t=new TransactionTemplate(manager);t.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);return t;}
    @BeforeEach void setup() {
        cleanup();owner=fixture(SystemRole.USER,AccountStatus.ACTIVE);
        var admin=fixture(SystemRole.ADMIN,AccountStatus.ACTIVE);
        users.updatePermissions(admin.id(),owner.id(),new UpdatePermissionsRequest(List.of(OrganizationPermission.PROJECT_CREATE)));
        project=create("Private");
    }
    @AfterEach void cleanup() {
        tx().executeWithoutResult(t -> {
            for(String table:List.of("bug_verifications","bug_details","work_item_links","work_items","organization_permissions","project_members","projects","refresh_tokens","password_reset_tokens","users"))jdbc.update("DELETE FROM "+table);
        });
    }
    UserResponse fixture(SystemRole role,AccountStatus status) {
        var u=users.createPendingUser(new CreateUserCommand("Test","Participant",UUID.randomUUID()+"@example.com","+254712345678","fixture-hash"));
        jdbc.update("UPDATE users SET role=?,status=? WHERE id=?",role.name(),status.name(),u.id());return users.getUserById(u.id());
    }
    UUID create(String name) {return creation.createProject(owner.id(),new CreateProjectRequest(name,"Secret brief","Requirements","Scope","Delivery","Success",LocalDate.of(2026,1,1),LocalDate.of(2026,2,1))).id();}
    UserResponse participant(ProjectRole role) {
        var user=fixture(SystemRole.USER,AccountStatus.ACTIVE);
        teams.addMember(project,owner.id(),new AddProjectMemberRequest(user.id(),role,role==ProjectRole.MEMBER?ProjectResponsibility.DEVELOPER:null));return user;
    }
    String token(UserResponse u) {return "Bearer "+jwt.generateToken(u.id().toString(),u.email(),"ADMIN","ACTIVE",Set.of("PROJECT_CREATE"));}
    String path() {return "/api/v1/projects/"+project;}
    UpdateProjectRequest name(String name) {var patch=new UpdateProjectRequest();patch.setName(name);return patch;}

    @ParameterizedTest @EnumSource(ProjectRole.class)
    void membersListOnlyTheirProjectsWithSafeProjection(ProjectRole role) throws Exception {
        var actor=participant(role);create("Other private");
        mvc.perform(get("/api/v1/projects").header("Authorization",token(actor)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(project.toString())).andExpect(jsonPath("$.content[0].role").value(role.name()))
                .andExpect(jsonPath("$.content[0].isOwner").value(false)).andExpect(jsonPath("$.content[0].description").doesNotExist())
                .andExpect(jsonPath("$.content[0].creatorUserId").doesNotExist()).andExpect(jsonPath("$.content[0].ownerUserId").doesNotExist())
                .andExpect(jsonPath("$.content[0].permissions").doesNotExist());
    }
    @Test void ownerListingHasStablePagingAndContractStatusFilter() throws Exception {
        UUID other=create("Other");jdbc.update("UPDATE projects SET status='ARCHIVED' WHERE id=?",other);
        jdbc.update("UPDATE projects SET created_at='2026-01-01T00:00:00Z'");
        List<UUID> order=jdbc.queryForList("SELECT id FROM projects ORDER BY created_at DESC,id DESC",UUID.class);
        for(int page=0;page<2;page++)mvc.perform(get("/api/v1/projects").header("Authorization",token(owner)).param("size","1").param("page",String.valueOf(page)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.content[0].id").value(order.get(page).toString()))
                .andExpect(jsonPath("$.content[0].isOwner").value(true));
        mvc.perform(get("/api/v1/projects").header("Authorization",token(owner)).param("status","ARCHIVED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(other.toString()));
    }
    @ParameterizedTest @EnumSource(ProjectRole.class)
    void detailsReturnCorrectRoleProjection(ProjectRole role) throws Exception {
        var actor=participant(role);
        var result=mvc.perform(get(path()).header("Authorization",token(actor))).andExpect(status().isOk());
        if(role==ProjectRole.VIEWER) {
            String json=result.andExpect(jsonPath("$.role").value("VIEWER")).andExpect(jsonPath("$.description").doesNotExist())
                    .andExpect(jsonPath("$.requirements").doesNotExist()).andExpect(jsonPath("$.ownerUserId").doesNotExist())
                    .andExpect(jsonPath("$.creatorUserId").doesNotExist()).andExpect(jsonPath("$.permissions").doesNotExist())
                    .andReturn().getResponse().getContentAsString();
            var map=tools.jackson.databind.json.JsonMapper.builder().build().readValue(json,Map.class);
            assertThat(map.keySet()).containsExactlyInAnyOrder("id","name","status","role","responsibility","isOwner");
        } else result.andExpect(jsonPath("$.description").value("Secret brief")).andExpect(jsonPath("$.permissions.role").value(role.name()))
                .andExpect(jsonPath("$.permissions.isOwner").value(false)).andExpect(jsonPath("$.permissions.actions").value(org.hamcrest.Matchers.containsInAnyOrder(role==ProjectRole.MANAGER
                        ?new String[]{"VIEW_PROJECT","VIEW_PROJECT_TEAM","UPDATE_PROJECT_DETAILS","SELECT_ELIGIBLE_USERS","ADD_PROJECT_MEMBER","VIEW_PROJECT_CONFIGURATION","UPDATE_PROJECT_CONFIGURATION"}
                        :new String[]{"VIEW_PROJECT","VIEW_PROJECT_TEAM","VIEW_PROJECT_CONFIGURATION"})))
                .andExpect(jsonPath("$.permissions.configuration.enabledWorkTypes").isEmpty());
    }
    @ParameterizedTest @EnumSource(SystemRole.class)
    void outsiderSystemRoleAndGrantCannotRevealOrUpdatePrivateProjects(SystemRole role) throws Exception {
        var actor=fixture(role,AccountStatus.ACTIVE);var admin=fixture(SystemRole.ADMIN,AccountStatus.ACTIVE);
        users.updatePermissions(admin.id(),actor.id(),new UpdatePermissionsRequest(List.of(OrganizationPermission.PROJECT_CREATE)));
        mvc.perform(get("/api/v1/projects").header("Authorization",token(actor))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get(path()).header("Authorization",token(actor))).andExpect(status().isNotFound());
        mvc.perform(patch(path()).header("Authorization",token(actor)).contentType("application/json").content("{\"name\":\"Denied\"}"))
                .andExpect(status().isNotFound());
    }
    @ParameterizedTest @EnumSource(value=AccountStatus.class,names="ACTIVE",mode=EnumSource.Mode.EXCLUDE)
    void inactiveActorCannotListReadOrUpdate(AccountStatus state) throws Exception {
        jdbc.update("UPDATE users SET status=? WHERE id=?",state.name(),owner.id());int expected=state==AccountStatus.PENDING_APPROVAL?403:401;
        mvc.perform(get("/api/v1/projects").header("Authorization",token(owner))).andExpect(status().is(expected));
        mvc.perform(get(path()).header("Authorization",token(owner))).andExpect(status().is(expected));
        mvc.perform(patch(path()).header("Authorization",token(owner)).contentType("application/json").content("{\"name\":\"Denied\"}"))
                .andExpect(status().is(expected));
        assertThatThrownBy(() -> details.update(project,owner.id(),name("Denied"))).isInstanceOf(com.eclectics.worksync.projects.exception.ProjectAccessDeniedException.class);
    }
    @Test void ownerAndManagerUpdateOnlyBriefAndReceiveAccuratePermissions() throws Exception {
        var manager=participant(ProjectRole.MANAGER);var original=jdbc.queryForMap("SELECT owner_user_id,creator_user_id,status,created_at,updated_at FROM projects WHERE id=?",project);
        mvc.perform(patch(path()).header("Authorization",token(owner)).contentType("application/json").content("{\"name\":\"Renamed\",\"description\":\"Updated\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.permissions.isOwner").value(true));
        mvc.perform(patch(path()).header("Authorization",token(manager)).contentType("application/json")
                .content("{\"scope\":\"New scope\",\"requirements\":\"New requirements\",\"deliverables\":\"New delivery\",\"successCriteria\":\"New success\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Renamed")).andExpect(jsonPath("$.description").value("Updated"))
                .andExpect(jsonPath("$.permissions.role").value("MANAGER")).andExpect(jsonPath("$.permissions.isOwner").value(false));
        var now=jdbc.queryForMap("SELECT owner_user_id,creator_user_id,status,created_at,updated_at FROM projects WHERE id=?",project);
        for(String field:List.of("owner_user_id","creator_user_id","status","created_at"))assertThat(now.get(field)).isEqualTo(original.get(field));
        assertThat(((java.sql.Timestamp)now.get("updated_at")).toInstant()).isAfter(((java.sql.Timestamp)original.get("updated_at")).toInstant());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project_members WHERE project_id=?",Integer.class,project)).isEqualTo(2);
    }
    @ParameterizedTest @EnumSource(value=ProjectRole.class,names={"MEMBER","VIEWER"})
    void nonManagersCannotPatch(ProjectRole role) throws Exception {
        var actor=participant(role);
        mvc.perform(patch(path()).header("Authorization",token(actor)).contentType("application/json").content("{\"name\":\"Denied\"}"))
                .andExpect(status().isForbidden());
    }
    @Test void patchDatesDistinguishOmissionFromNullAndValidateMergedState() throws Exception {
        mvc.perform(patch(path()).header("Authorization",token(owner)).contentType("application/json").content("{\"startDate\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.startDate").doesNotExist()).andExpect(jsonPath("$.dueDate").value("2026-02-01"));
        mvc.perform(patch(path()).header("Authorization",token(owner)).contentType("application/json").content("{\"startDate\":\"2026-03-01\",\"name\":\"Invalid merged\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(path()).header("Authorization",token(owner))).andExpect(jsonPath("$.name").value("Private"));
        mvc.perform(patch(path()).header("Authorization",token(owner)).contentType("application/json").content("{\"startDate\":\"2026-03-01\",\"dueDate\":\"2026-04-01\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.startDate").value("2026-03-01"));
        mvc.perform(patch(path()).header("Authorization",token(owner)).contentType("application/json").content("{\"dueDate\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dueDate").doesNotExist());
    }
    static java.util.stream.Stream<Arguments> invalidDateValues() {
        return java.util.stream.Stream.of("startDate","dueDate").flatMap(field -> java.util.stream.Stream.of(
                "\"+999999999-12-31\"","\"-0001-01-01\"","\"2026-1-05\"","\"2026-01-5\"",
                "\"2026-02-30\"","\"2026-01-01T12:00:00\"","\"2027-02-29\"","\"1900-02-29\"",
                "\"0000-01-01\"","\" 2026-01-01\"","123","false","[]","{}")
                .map(value -> Arguments.of(field,value)));
    }
    @ParameterizedTest @MethodSource("invalidDateValues")
    void invalidCalendarRepresentationsNeverModifyStoredProject(String field,String value) throws Exception {
        var before=jdbc.queryForMap("SELECT * FROM projects WHERE id=?",project);
        mvc.perform(patch(path()).header("Authorization",token(owner)).contentType("application/json")
                .content("{\"name\":\"Must not persist\",\""+field+"\":"+value+"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PROJECT_REQUEST"))
                .andExpect(jsonPath("$.message").value("Invalid project request"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Must not persist"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("LocalDate"))));
        assertThat(jdbc.queryForMap("SELECT * FROM projects WHERE id=?",project)).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings={"2024-02-29","2000-02-29","2028-02-29"})
    void validLeapDatesPersistAndRetainNullOmissionSemantics(String date) throws Exception {
        mvc.perform(patch(path()).header("Authorization",token(owner)).contentType("application/json")
                .content("{\"startDate\":\""+date+"\",\"dueDate\":\""+date+"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.startDate").value(date)).andExpect(jsonPath("$.dueDate").value(date));
        assertThat(jdbc.queryForObject("SELECT start_date FROM projects WHERE id=?",LocalDate.class,project)).isEqualTo(LocalDate.parse(date));
        mvc.perform(patch(path()).header("Authorization",token(owner)).contentType("application/json").content("{\"description\":\"Only brief changes\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.startDate").value(date)).andExpect(jsonPath("$.dueDate").value(date));
        mvc.perform(patch(path()).header("Authorization",token(owner)).contentType("application/json").content("{\"startDate\":null,\"dueDate\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.startDate").doesNotExist()).andExpect(jsonPath("$.dueDate").doesNotExist());
        assertThat(jdbc.queryForMap("SELECT start_date,due_date FROM projects WHERE id=?",project))
                .containsEntry("start_date",null).containsEntry("due_date",null);
    }
    @Test void archivedViewerReceivesOnlyTheSafeSummary() throws Exception {
        var viewer=participant(ProjectRole.VIEWER);
        jdbc.update("UPDATE projects SET status='ARCHIVED' WHERE id=?",project);
        String json=mvc.perform(get(path()).header("Authorization",token(viewer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.role").value("VIEWER")).andExpect(jsonPath("$.isOwner").value(false))
                .andExpect(jsonPath("$.description").doesNotExist()).andExpect(jsonPath("$.requirements").doesNotExist())
                .andExpect(jsonPath("$.scope").doesNotExist()).andExpect(jsonPath("$.deliverables").doesNotExist())
                .andExpect(jsonPath("$.successCriteria").doesNotExist()).andExpect(jsonPath("$.ownerUserId").doesNotExist())
                .andExpect(jsonPath("$.creatorUserId").doesNotExist()).andExpect(jsonPath("$.permissions").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        var response=tools.jackson.databind.json.JsonMapper.builder().build().readValue(json,Map.class);
        assertThat(response.keySet()).containsExactlyInAnyOrder("id","name","status","role","responsibility","isOwner");
    }
    static java.util.stream.Stream<Arguments> invalidText() {
        return java.util.stream.Stream.of("name","description","requirements","scope","deliverables","successCriteria")
                .flatMap(field -> java.util.stream.Stream.of("123","true","null","[]","{}").map(value -> Arguments.of("{\""+field+"\":"+value+"}")));
    }
    @ParameterizedTest @MethodSource("invalidText")
    void patchTextTypesCannotBeCoerced(String body) throws Exception {badPatch(body);}
    @ParameterizedTest @ValueSource(strings={"{}","{\"name\":\" \"}","{\"ownerUserId\":\"00000000-0000-0000-0000-000000000001\"}","{\"creatorUserId\":\"00000000-0000-0000-0000-000000000001\"}","{\"status\":\"ARCHIVED\"}","{\"id\":\"00000000-0000-0000-0000-000000000001\"}","{\"memberships\":[]}","{\"extra\":true}","{\"startDate\":false}","{\"startDate\":[2026,1,1]}","{\"dueDate\":123}","{\"dueDate\":\"2026-02-30\"}","{\"startDate\":\"invalid\"}"})
    void invalidPatchOrProtectedFieldIsRejected(String body) throws Exception {badPatch(body);}
    void badPatch(String body) throws Exception {
        mvc.perform(patch(path()).header("Authorization",token(owner)).contentType("application/json").content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PROJECT_REQUEST"))
                .andExpect(jsonPath("$.message").value("Invalid project request"));
        assertThat(jdbc.queryForObject("SELECT name FROM projects WHERE id=?",String.class,project)).isEqualTo("Private");
    }
    @Test void overlongNameAndArchivedUpdatesAreRejected() throws Exception {
        badPatch("{\"name\":\""+"A".repeat(201)+"\"}");jdbc.update("UPDATE projects SET status='ARCHIVED' WHERE id=?",project);
        mvc.perform(patch(path()).header("Authorization",token(owner)).contentType("application/json").content("{\"name\":\"Denied\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PROJECT_ARCHIVED"));
        mvc.perform(get(path()).header("Authorization",token(owner))).andExpect(status().isOk());
    }
    @Test void missingInvalidIdsAnonymousAndPaginationHaveSafeResults() throws Exception {
        mvc.perform(get("/api/v1/projects")).andExpect(status().isUnauthorized());mvc.perform(get(path())).andExpect(status().isUnauthorized());
        mvc.perform(patch(path()).contentType("application/json").content("{\"name\":\"Denied\"}" )).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/projects/"+UUID.randomUUID()).header("Authorization",token(owner))).andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/projects/"+UUID.randomUUID()).header("Authorization",token(owner)).contentType("application/json").content("{\"name\":\"Denied\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/projects/not-a-uuid").header("Authorization",token(owner))).andExpect(status().isBadRequest());
        for(String[] params:List.of(new String[]{"2147483647","100"},new String[]{"-1","20"},new String[]{"0","0"},new String[]{"0","101"}))
            mvc.perform(get("/api/v1/projects").header("Authorization",token(owner)).param("page",params[0]).param("size",params[1])).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/projects").header("Authorization",token(owner)).param("page","2147483647").param("size","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty()).andExpect(jsonPath("$.totalElements").value(1));
        for(String param:List.of("sort","search","ownerUserId"))mvc.perform(get("/api/v1/projects").header("Authorization",token(owner)).param(param,"ignored"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/projects").header("Authorization",token(owner)).param("status","INVALID")).andExpect(status().isBadRequest());
    }
    @Test void serializedPartialUpdatesPreserveBothChangesAndRollbackFailedOuterTransaction() throws Exception {
        var held=new CountDownLatch(1);var release=new CountDownLatch(1);var attempting=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(() -> tx().execute(t -> {var result=details.update(project,owner.id(),name("Renamed"));held.countDown();await(release);return result;}));await(held);
            var second=pool.submit(() -> {attempting.countDown();var patch=new UpdateProjectRequest();patch.setDescription("Later brief");return details.update(project,owner.id(),patch);});
            try {await(attempting);assertThatThrownBy(() -> second.get(200,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);}finally{release.countDown();}
            first.get(15,TimeUnit.SECONDS);second.get(15,TimeUnit.SECONDS);
        }finally{release.countDown();}
        assertThat(jdbc.queryForMap("SELECT name,description FROM projects WHERE id=?",project)).containsEntry("name","Renamed").containsEntry("description","Later brief");
        assertThatThrownBy(() -> tx().executeWithoutResult(t -> {details.update(project,owner.id(),name("Rollback"));throw new IllegalStateException("test rollback");})).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT name FROM projects WHERE id=?",String.class,project)).isEqualTo("Renamed");
    }
    @Test void blockWinningLifecycleLockPreventsWaitingUpdate() throws Exception {
        var held=new CountDownLatch(1);var release=new CountDownLatch(1);var attempting=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var block=pool.submit(() -> tx().executeWithoutResult(t -> {users.blockUser(owner.id());held.countDown();await(release);}));await(held);
            var patch=pool.submit(() -> {attempting.countDown();return details.update(project,owner.id(),name("Denied"));});
            try {await(attempting);assertThatThrownBy(() -> patch.get(200,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);}finally{release.countDown();}
            block.get(15,TimeUnit.SECONDS);assertThatThrownBy(() -> patch.get(15,TimeUnit.SECONDS)).hasCauseInstanceOf(com.eclectics.worksync.projects.exception.ProjectAccessDeniedException.class);
        }finally{release.countDown();}
        assertThat(jdbc.queryForObject("SELECT name FROM projects WHERE id=?",String.class,project)).isEqualTo("Private");
    }
    static void await(CountDownLatch latch){try{assertThat(latch.await(15,TimeUnit.SECONDS)).isTrue();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}
}
