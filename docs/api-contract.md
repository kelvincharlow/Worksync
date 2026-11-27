# Module 1 API contract

**Status: Auth and Users Step 5B Parts 1 and 2 are implemented in main.** Part 1 provides profile and
administrator reads; Part 2 adds approval, rejection and block/unblock endpoints.
Auth registration, login and password-reset behavior is reused unchanged.
Users V7 implements direct PROJECT_CREATE permission replacement. Future role management and in-app Notifications endpoints remain planned.

[openapi.yaml](openapi.yaml) records the supplied OpenAPI 3.0.3 contract, version 0.3.0. It is the detailed schema reference for review. [Contract review notes](module1-contract-review-notes.md) record the supplied scope and security considerations. Agree changes in a pull request before implementing controllers or frontend clients.

## Endpoint scope

Base URL: `http://localhost:8080/api/v1`. JSON requests/responses. The implemented Users operations use `Authorization: Bearer <JWT>`; public Auth registration/login opt out. All administrator operations require `ROLE_ADMIN` and `STATUS_ACTIVE`.

| Status | Method and path | Behavior / draft contract |
| --- | --- | --- |
| IMPLEMENTED — MAIN (AUTH) | POST /auth/register | Auth-owned; public; 201 safe user, PENDING_APPROVAL/USER, no token |
| IMPLEMENTED — MAIN (AUTH) | POST /auth/login | Auth-owned; public; 200 accessToken, tokenType, expiresIn and safe user |
| IMPLEMENTED — MAIN (AUTH) | POST /auth/forgot-password | Auth-owned reset-code request; draft path mismatch below |
| IMPLEMENTED — MAIN (AUTH) | POST /auth/reset-password | Auth-owned reset confirmation; draft path mismatch below |
| IMPLEMENTED — MAIN (USERS STEP 5B) | GET /users/me | Own profile; ACTIVE or PENDING_APPROVAL |
| IMPLEMENTED — MAIN (USERS STEP 5B) | PUT /users/me | Update own names and phone number only; ACTIVE or PENDING_APPROVAL |
| IMPLEMENTED — MAIN (USERS STEP 5B) | GET /admin/users | ACTIVE ADMIN; optional status filter and pagination |
| IMPLEMENTED — MAIN (USERS STEP 5B) | GET /admin/users/{userId} | ACTIVE ADMIN; retrieve one account; 404 if absent |
| IMPLEMENTED — MAIN (USERS STEP 5B) | PATCH /admin/users/{userId}/approve | ACTIVE ADMIN; PENDING_APPROVAL → ACTIVE |
| IMPLEMENTED — MAIN (USERS STEP 5B) | PATCH /admin/users/{userId}/reject | ACTIVE ADMIN; PENDING_APPROVAL → REJECTED; optional reason |
| IMPLEMENTED — MAIN (USERS STEP 5B) | PATCH /admin/users/{userId}/status | ACTIVE ADMIN; ACTIVE ↔ BLOCKED; never a substitute for approval |
| PLANNED | GET /notifications | ACTIVE only; own notifications; PENDING_APPROVAL denied |
| PLANNED | PATCH /notifications/{notificationId}/read | ACTIVE only; own notification only; 200 updated NotificationRecord |
| PLANNED — LATER | POST /auth/logout | Actually revoke presented JWT; 204 only after revocation |
| IMPLEMENTED — MAIN (USERS V7) | PATCH /admin/users/{userId}/permissions | ACTIVE ADMIN replaces direct grants; stored/effective permissions returned |
| PLANNED — LATER | PATCH /admin/users/{userId}/role | Change system role under high-trust administrator policy |

In-app notification endpoints remain planned. The YAML explicitly marks the remaining logout and system-role operations with `x-implementation-phase: later`.

## Implemented Users behavior

See [Users REST endpoints](users-rest-endpoints.md) for the exact security and response contract.
The implemented single-user operations return UserResponse: UUID id, firstName, lastName, email,
phoneNumber, role, status, createdAt, updatedAt, stored permissions and derived effectivePermissions. No credentials.
The administrator list wraps these in content/page/size/totalElements (`UserPageResponse`
in Java, `PagedUsers` in OpenAPI). Its optional `status` filter uses AccountStatus;
`page` defaults to 0 (minimum 0), and `size` defaults to 20 (range 1–100).
Ordering is fixed at `createdAt DESC, id DESC`.

Profile PUT requires nonblank firstName and lastName (at most 80 characters each),
plus an E.164 phoneNumber. Every other JSON field is rejected with 400.
The current user's UUID comes only from WorkSyncPrincipal.

Missing, invalid or expired authentication returns 401. Auth's existing filter
also declines authentication for BLOCKED/REJECTED accounts, resulting in 401.
Authenticated ordinary USER accounts and pending ADMIN accounts receive 403 on
administrator operations. Users validation/missing-user errors use safe
code/message/timestamp JSON with 400/404; Auth's 401/403 responses retain their
existing format.

## Main-branch security and automated test setup

The updated main-branch SecurityConfig allows PENDING_APPROVAL accounts to GET and
PUT /api/v1/users/me. Notification access under /api/v1/notifications/** requires
STATUS_ACTIVE; pending accounts cannot access it, including account-related
notifications. Administrator routes require both ROLE_ADMIN and STATUS_ACTIVE.
In-app notification endpoints remain planned despite their security rule.

Auth now requires EmailNotificationService, and the shared application
configuration includes the email profile. Automated tests must replace email
delivery with a mock; they must never use live SMTP. See the
[Users test setup](users-rest-endpoints.md#automated-test-setup) for details.

## Administrator account lifecycle — Part 2

- PATCH /admin/users/{userId}/approve: PENDING_APPROVAL → ACTIVE.
- PATCH /admin/users/{userId}/reject: PENDING_APPROVAL → REJECTED; optional JSON
  `{"reason":"..."}`, at most 500 characters. An omitted body or empty object is valid.
- PATCH /admin/users/{userId}/status: required JSON `{"status":"BLOCKED"}` or
  `{"status":"ACTIVE"}`; optional reason of at most 500 characters. Only ACTIVE →
  BLOCKED and BLOCKED → ACTIVE are supported; this cannot approve or restore rejected users.

Unknown JSON properties on reason/status requests are rejected with 400. Reasons are
validated but not stored, logged or sent in notifications; no reason persistence,
audit trail or notification delivery is implemented. Responses contain only UserResponse.

The acting UUID comes from WorkSyncPrincipal. Auth's route chain requires ROLE_ADMIN
and STATUS_ACTIVE; the service rechecks these from current database state under a
transaction lock. Self-blocking and blocking the last ACTIVE ADMIN return safe 409
errors. Invalid transitions return 409; missing targets return 404; invalid requests
return 400. Auth retains its existing 401/403 handling.

PostgreSQL transaction advisory lock (1465075027, 1) serializes Users updates across
instances until commit/rollback. The active-admin count and block occur under that
lock, including the trusted one-argument service method. Profile/password writes use
the same lock and refresh managed entities so stale full-row updates cannot restore
an earlier account status. These operations require READ_COMMITTED and fail closed
when joined to a higher-isolation outer transaction. No schema migration is needed.

Future Users write paths, role changes or administrator maintenance tools must follow
this locking protocol. Direct SQL bypasses application safeguards. Serializing Users
updates trades write throughput for a simple consistent invariant; reads remain unlocked.
The guard preserves an existing active administrator; it does not provision the first one.

## Auth-owned password-reset path mismatch

The current AuthController and draft OpenAPI use different paths:

| Operation | AuthController (current code) | Draft OpenAPI |
| --- | --- | --- |
| Request reset code | POST /api/v1/auth/forgot-password | POST /api/v1/auth/password-reset/request |
| Confirm reset | POST /api/v1/auth/reset-password | POST /api/v1/auth/password-reset/confirm |

The Auth owner must reconcile these before client integration. This documentation
review does not rename either route or change Auth-owned endpoint contracts.

## Draft request and response expectations

- Registration requires **all five** fields: firstName, lastName, email, phoneNumber and password. Names: 1–80 characters; email: valid format, at most 254 characters; phoneNumber: E.164 (for example `+254712345678`); password: 12–128 characters. Do not silently trim/truncate passwords. Public registration cannot set role, status or permissions.
- Login accepts email/password. AuthResponse contains accessToken, tokenType `Bearer`, expiresIn in seconds and a safe user. The example 900-second lifetime is illustrative. Pending users can obtain tokens but remain restricted by the backend. BLOCKED/REJECTED accounts cannot obtain tokens.
- The draft UserRecord (used by other planned contracts) contains opaque string id, firstName, lastName, email, phoneNumber, **role** (singular), status, permissions and createdAt; updatedAt is optional. IDs are strings, not a UUID contract. Passwords and hashes never appear in responses.
- Profile PUT requires firstName, lastName and phoneNumber. It cannot change email, role, status or permission grants. Email changes require a later verification flow.
- Account statuses: PENDING_APPROVAL, ACTIVE, REJECTED, BLOCKED. System roles: USER, ADMIN. Users V7 implements named organization grants, initially PROJECT_CREATE, with no automatic grant on approval; Step 5B did not implement these grants. Project roles are separate future memberships.
- List responses contain **content**, page, size and totalElements. Pages are zero-based; size defaults to 20 and is bounded to 1–100. User lists support an account status filter; notifications support READ/UNREAD status.
- NotificationRecord contains id, type, title, message, status and createdAt, with optional nullable readAt. Types: ACCOUNT_REGISTERED, ACCOUNT_APPROVED, ACCOUNT_REJECTED, ACCOUNT_BLOCKED and GENERAL. Another user's notification cannot be read or modified through these endpoints; a read-update attempt returns 404 when missing/not owned.
- The draft proposes errors using **application/json** with code, message and timestamp, plus optional fieldErrors containing field/message pairs. This replaces the earlier Problem Details proposal. Never include passwords, hashes, SQL or stack traces.

Expected error statuses: 400 invalid input, 401 invalid/missing/expired authentication, 403 forbidden access or blocked/rejected login, 404 missing resource, 409 duplicate email or invalid account transition, and 429 rate limiting. See the YAML for each operation's declared responses. These are draft expectations for other operations; the implemented Users behavior and inherited Auth security responses are described above.

## Team decisions still needed before implementation

Finalize JWT signing/key rotation, token lifetime, browser storage, CORS/CSRF behavior, prompt status/role/permission changes on existing tokens, and the later revocation/refresh strategy. Browser token disposal must not be presented as server-side revocation. Define secure one-time first-admin provisioning, audit logging and email normalization/uniqueness. For future endpoints, agree pagination sorting and detailed validation behavior. The implemented Users reads already use fixed ordering, and profile updates reject whitespace-only names. The supplied YAML is a draft and some operations do not yet enumerate malformed-input or blocked-token error cases; review those before implementation.

Email verification, rate limiting/abuse protection, administrator MFA as appropriate, and a complete token lifecycle policy remain production-readiness work outside Users Step 5B. Password-reset code is implemented in main, with the path mismatch noted above; this step does not assess its production readiness. Email delivery failure must not roll back an otherwise successful registration or approval.

## Modules 2 and 3 — V13 contract extension (planned)

Source: `WorkSync_M2_M3_Implementation_Agreement_V13_Kelvin_Phoebe.docx`,
23 November 2026, read in full. This agreement supplies contract requirements;
its sample Git commands, Java file lists and deployment procedures are context,
not instructions to implement modules or release them in this change.

OpenAPI version **0.3.0**, specification **3.0.3**, adds Projects (M2, Kelvin) and
Work Items/Bugs (M3, Phoebe). Project creation, eligible-user lookup and membership GET/POST
are merged into main. Project GET list/details and PATCH details are implemented on
`feature/projects-list-details-update`; other M2/M3 HTTP operations remain planned. Existing Module 1 paths, operations, schemas,
error definitions and public Auth security overrides are preserved. The earlier
Module 1 sections above describe that baseline. This creation slice does not implement
other M2/M3 endpoints, feature configuration, frontend screens or deployment changes.

### Shared authorization and architecture

All new operations require a verified Bearer JWT and a currently ACTIVE account.
Obtain actor UUID only from WorkSyncPrincipal. Recheck current project membership,
responsibility and per-record authority server-side; browser role labels and
stale JWT claims are not authority. System ADMIN is not private-project access.
A blocked/rejected token may produce 401 under existing Auth filtering; an
otherwise authenticated actor without project capability receives 403. Unknown,
cross-project or deliberately non-disclosable resource IDs produce 404.

Projects owns project authorization, memberships and writable/archive checks.
Work Items owns scoped work records, assignment state, bug transitions,
independent verification and history. Cross-module calls use public Java service
contracts/immutable DTOs within the monolith, never repositories or JPA entities.
M2 overview calls M3 summaries. These are not inter-service HTTP calls.

| Actor | Authority and limits |
| --- | --- |
| ACTIVE + PROJECT_CREATE | Create a private project; atomically become its Owner and initial MANAGER. Account approval/ADMIN does not grant PROJECT_CREATE. |
| Owner | Protected designation plus MANAGER membership; exclusively appoint/remove/demote Managers, transfer ownership and archive. Cannot remove/demote self while Owner. |
| Additional MANAGER | Edit project/settings/milestones; manage Members/Viewers; create enabled Epic/Story/Task, assign and triage. Cannot administer other Managers or Owner. |
| MEMBER + TECHNICAL_LEAD | Create enabled Epic/Story/Task, assign work, triage; designate QA verifier only. No project administration. |
| MEMBER + DEVELOPER | Create only self-assigned TASK; edit allowed fields/progress of assigned work; report bugs and submit assigned fixes. No Epic/Story creation or reassignment of others. |
| MEMBER + QA_TESTER | Report bugs, read permitted internal/testing work; verify only if designated for that bug. No ordinary development-task creation or generic progress mutation. |
| VIEWER | Safe selector/project summary, own effective permissions, sanitized milestones, delivery summary/progress only. No raw work, bugs, internal summary, team directory, project configuration or preferences. |

Only MEMBER has exactly one responsibility. MANAGER and VIEWER have none; OpenAPI
`oneOf` enforces this conditional membership request shape. Targets must be ACTIVE.
Owner transfer requires an existing ACTIVE Manager, is atomic, and leaves the old
Owner a Manager. Competing mutations must preserve a valid Owner/Manager. Inactive
Owner recovery remains a separate future security decision; never elevate ADMIN
silently. Removal/reassignment of members with existing work requires final policy.

All M2/M3 writes reject ARCHIVED projects with 409. Historical authorized reads
remain available. Views never grant work rights: disabling LIST/BOARD removes
availability without deleting records. Overview and Planning remain core. Six-step
setup does not require completing brief/hierarchy at creation. Preferences apply
to MEMBER only, one per project/current user; disabled/disallowed choices fall back
to Planning or another authorized enabled page. Managers' separate preference
screens and Viewer preferences are deferred.

### Bug lifecycle and independent verification

Bugs are Work Items with type BUG and the same UUID; subtasks are TASK records
with a parent. Generic create/update/assignment routes cannot bypass specialized
bug operations. Reproduction steps, expected/actual result and description are
required plain text; reporter comes from principal. Severity is impact, priority
is urgency. No upload/attachment-URL contract is introduced.

| From | Operation / actor | To |
| --- | --- | --- |
| New | Report by Manager or Member | REPORTED |
| REPORTED | BEGIN_TRIAGE by Manager/Lead | TRIAGE |
| TRIAGE | ACCEPT with eligible fixer by Manager/Lead | ASSIGNED |
| ASSIGNED | Start work by assigned fixer | IN_PROGRESS |
| IN_PROGRESS | Submit fix by assigned fixer, independent verifier designated | READY_FOR_VERIFICATION |
| READY_FOR_VERIFICATION | Designated verifier PASS | CLOSED; VERIFIED history event |
| READY_FOR_VERIFICATION | Designated verifier FAIL | REOPENED |
| REOPENED | Authorized triage/reassignment | ASSIGNED, then fixer starts IN_PROGRESS |
| REPORTED or TRIAGE | Reasoned INVALID/DUPLICATE outcome by Manager/Lead | CLOSED, triage outcome recorded; no verification PASS |
| IN_PROGRESS | Manager/Lead changes fixer | ASSIGNED; new fixer must start work |

The fixed bug status set is REPORTED, TRIAGE, ASSIGNED, IN_PROGRESS,
READY_FOR_VERIFICATION, REOPENED and CLOSED. PASS/FAIL are verification results;
INVALID/DUPLICATE are recorded triage outcomes, not statuses. Successful fix
verification closes with PASS; triage disposal closes with a reason and
INVALID/DUPLICATE outcome, without fabricating PASS or VERIFIED history.
BEGIN_TRIAGE is a request action; ACCEPT records ACCEPTED disposition.
Duplicate targets must be another BUG in the same project, with no self/cycles.
There is no VERIFIED or AWAITING_VERIFICATION status or generic bug-status PATCH.
Failed verification is the reopen operation; no separate unrestricted reopen route.
All unlisted transitions return 409. All actors must be ACTIVE and retain eligible
membership; no mutation is allowed on an archived project.

POST `/projects/{projectId}/bugs/{bugId}/start-work` is retained as an agreed
explicit route: assigned fixer only, ASSIGNED -> IN_PROGRESS, with atomic
START_WORK actor/time/state history. Being Manager/Lead is not a start-work override.

Fixer assignment/reassignment is limited to ASSIGNED/IN_PROGRESS/REOPENED.
A changed fixer in IN_PROGRESS resets to ASSIGNED; REOPENED also returns to
ASSIGNED. Conflicting verifier assignment is invalidated; historical attempt
submitter remains immutable. Verifier designation is allowed in ASSIGNED,
IN_PROGRESS or REOPENED only. READY_FOR_VERIFICATION assignment changes return
409 rather than changing the pending attempt's identity. These rules, state
changes and history must be atomic under concurrent requests.

Only the assigned fixer submits a fix. Only the designated ACTIVE eligible
verifier records PASS/FAIL, and **a fixer may never verify their own fix**, even
if Manager. Preserve the submitter of the specific fix attempt so reassignment
cannot evade this rule. Manager may designate another eligible independent
Member/Manager for one bug; Lead may select QA only. This does not promote roles
or create a project-wide VERIFY_BUG grant. Recheck eligibility and status at
verification, serialize competing verification/assignment changes, and retain
append-only actor/time/state and attempt history. No eligible independent person
means fix submission returns 409 and the bug remains IN_PROGRESS. No waiting
status is introduced. Phoebe's Work Items module owns these records, fixer/verifier
assignments, transitions and history; Kelvin's Projects module owns membership,
ACTIVE participant eligibility and project authorization.

### Persisted triage details and history

BugResponse always includes nullable `triageOutcome`, `triageReason` and
`duplicateOfBugId`. Before a disposition they are null; BEGIN_TRIAGE alone does
not set one. ACCEPT records ACCEPTED; its explanation may be null. INVALID and
DUPLICATE require a nonblank explanation. Only DUPLICATE populates duplicateOfBugId;
it must identify a different BUG Work Item in the same project, with no duplicate
cycle. References never grant access and require scoped type/project validation.

Phoebe's Work Items domain persists the current decision and lifecycle change
atomically, together with actor/time/state and the three decision fields in
append-only bug history. BugHistoryResponse includes these nullable fields;
non-triage events use null for decision fields, and historical triage events retain
their original values. Later changes cannot rewrite earlier decision history.
Exact persistence mapping/Flyway allocation remains a foundation decision; this
PR adds no migration or working persistence implementation.

### Role-specific effective permission responses

GET `/projects/{projectId}/permissions/me` retains EffectiveProjectPermissionsResponse
as its role-discriminated union. MANAGER/MEMBER receive
InternalProjectPermissionsResponse: role, responsibility, isOwner, effective
actions and authorized project configuration. Internal ProjectResponse also uses
that internal permissions variant.

VIEWER receives ViewerProjectPermissionsResponse only: role VIEWER, null
responsibility, isOwner false, actions containing only VIEW_DELIVERY, and
availableDeliveryFeatures restricted to DELIVERY_SUMMARY, MILESTONES and
DELIVERY_PROGRESS. Availability is computed server-side for existing delivery
surfaces; it never means data is approved for publication. Unpublished content
remains omitted/null. No configuration, enabled work types, LIST/BOARD options,
internal reports or administrative capabilities appear in the Viewer shape;
unknown/extra fields are rejected by the schema. Publication mechanics remain
planned and unchanged.

### Request, response and validation conventions

UUID project/user/work/bug/milestone references are scoped and validated. Return
safe DTOs, never entities, password hashes or private directory data. Requests
reject unknown fields; patch objects require at least one supported property.
Dates use ISO date, timestamps date-time; startDate cannot exceed dueDate after
patch application. Server services must enforce cross-field, role, hierarchy,
state and concurrent integrity rules that OpenAPI cannot encode.

Membership changes use complete role/responsibility replacement. Ordinary work
updates exclude project/type/creator/assignee/parent mass assignment; dedicated
assignment routes enforce eligible ACTIVE Member/Manager targets. Manager/Lead
planning rights never permit fabricating another person's completion history.
Bug reporter/status/fixer/verifier cannot be supplied in a report request.

Lists reuse content/page/size/totalElements; page >= 0, size 1–100, default 20.
Default ordering is createdAt DESC, id DESC; members tie-break on userId,
milestones use dueDate ASC/id ASC, bug history createdAt ASC/id ASC. Work-list
sort/filter/group vocabularies are controlled whitelists, not arbitrary query or
property expressions. Role-specific queues use the principal for my fixes,
reported-by-me and my verifications; filters never enlarge visibility.

M2/M3 errors reuse application/json ApiError (code/message/timestamp, optional
fieldErrors): 400 malformed/invalid fields, 401 missing/invalid authentication,
403 insufficient capability, 404 unknown/cross-project/non-disclosable resource,
409 archived/duplicate/ownership/lifecycle/assignment conflicts. Provider errors,
SQL, stack traces and secrets must not be returned. A new M2M3Conflict reusable
response preserves the existing Module 1 account-transition response unchanged.

### Controlled personal preferences

MEMBER only; Manager/Viewer editing remains deferred. Values are fixed below:

| Field | Controlled values / checks |
| --- | --- |
| preferredView | PLANNING, LIST, BOARD; LIST/BOARD require project enablement |
| landingPage | TECHNICAL_OVERVIEW (Lead), DEVELOPMENT_OVERVIEW (Developer), TESTING_OVERVIEW (QA), PLANNING, LIST, BOARD; role/view checks apply |
| filters | type, ordinary status, bugStatus, priority, assignedToMe only; status/bugStatus mutually exclusive and compatible with type |
| sort | CREATED_AT_DESC, DUE_DATE_ASC, PRIORITY_DESC; id DESC tie-breaker, null due dates last |
| groupBy | NONE, STATUS, ASSIGNEE, TYPE |

Unknown fields or enum values return 400. A recognized selection that is no longer
available resolves to PLANNING for both effective view and landing page, including
after role or feature changes. Supplied filters replace the filter object; `{}`
clears filters. Preferences cannot enable features, override record/action
permissions or reveal inaccessible work. MEMBER-only personal presentation is
separate from Manager-controlled project configuration.

### Viewer publication: deny publication by default

Viewer responses contain only client-safe high-level delivery progress/status and
approved milestone information; never internal tasks, bugs, team, assignments,
technical notes, private activity or sensitive project brief. Selector/project
summary and own permissions remain limited metadata routes, not internal browsing.

A Manager must explicitly determine that the exact milestone title/date/status or
aggregate snapshot is publishable. Internal existence, completion or membership
never counts as publication approval. Projects owns publication authorization and
the Viewer projection; Work Items supplies only sanitized approved aggregate
inputs. Approval persistence, approval/revocation endpoints, snapshot freshness
and the percentage formula remain **planned contract decisions**. No working
publication feature or endpoint is invented here.

Until that mechanism is reviewed and available, return an empty approved milestone
list, null completionPercent and null deliveryStatus; do not fall back to internal
records. Zero means an explicitly approved actual zero, not missing publication.
Internal milestone endpoints remain authorized for Manager/Member; Viewer receives
only approved ClientMilestoneResponse fields. This policy covers delivery-summary,
delivery-progress and the Viewer branch of milestone reads equally.

### Proposals and unresolved decisions (pending final review)

`x-contract-decision: proposed-pending-review` marks schema/operation proposals.
Agreed security invariants above apply regardless of eventual vocabulary.

- Ordinary statuses TODO/IN_PROGRESS/IN_REVIEW/DONE; final transitions and
  role/field-by-type edit/assignment matrix still require review.
- Optional EPIC -> STORY -> TASK -> TASK subtask, at most one subtask level;
  standalone tasks require neither Story nor Epic. Same-project, acyclic
  hierarchy/link validation mandatory. Confirm optional milestone association.
- Member internal directory scope is proposed from the agreement's "as agreed"
  wording. Viewer never has directory access.
- Ordinary creation starts TODO; omitted ordinary priority and initial bug priority
  default to MEDIUM as a pending-review proposal. Initial priority LOW/MEDIUM/HIGH
  (URGENT deferred), severity
  LOW/MEDIUM/HIGH/CRITICAL, links RELATES_TO/BLOCKS, milestone lifecycle
  PLANNED/ACHIEVED, delivery NOT_STARTED/IN_PROGRESS/DELIVERED need final approval.
- Name/title/affected-area limits of 200 and fix-reference limit 500 remain
  proposals, not claimed V13 decisions. Preference whitelists above
  are now defined. Completion calculation, approval persistence/revocation and
  client-safe milestone publication mechanics remain planned decisions.
- V5/V6/V7 are merged. POST `/projects` now uses the transactional Users PROJECT_CREATE guard;
  creation atomically inserts the Project and creator Owner/MANAGER membership.
- Inactive-owner recovery, member removal with assignments, exact cross-module
  Java signatures/packages, and shared Flyway version allocation remain open.
  Main contains V1/V3/V4/V5/V6/V7; no migration is changed by this creation slice.
- Home-wide invitations/assignments, calendar/Gantt, advanced Scrum, custom roles,
  workflow customization, automation, files and integrations remain deferred.

### M2/M3 operation inventory (see operation-level implementation markers)

All paths below are relative to `/api/v1`; `{projectId}` consistently names the
agreement's project `{id}`. The YAML contains each request, response, error and
operation-specific authorization rule.

| Module | Method and path | Operation |
| --- | --- | --- |
| M2 | POST `/projects` | Create private project |
| M2 | GET `/projects` | List accessible projects |
| M2 | GET `/projects/{projectId}` | Read project |
| M2 | PATCH `/projects/{projectId}` | Update project details |
| M2 | GET `/projects/{projectId}/members` | List internal team |
| M2 | POST `/projects/{projectId}/members` | Add ACTIVE project participant |
| M2 | PATCH `/projects/{projectId}/members/{userId}` | Change role or responsibility |
| M2 | DELETE `/projects/{projectId}/members/{userId}` | Remove project participant |
| M2 | POST `/projects/{projectId}/ownership-transfer` | Transfer project ownership |
| M2 | GET `/projects/{projectId}/permissions/me` | Read effective project permissions |
| M2 | GET `/projects/{projectId}/configuration` | Read internal feature configuration |
| M2 | PATCH `/projects/{projectId}/configuration` | Update project-wide configuration |
| M2 | GET `/projects/{projectId}/preferences/me` | Read own workspace preferences |
| M2 | PATCH `/projects/{projectId}/preferences/me` | Update own workspace preferences |
| M2 | GET `/projects/{projectId}/milestones` | Read project milestones |
| M2 | POST `/projects/{projectId}/milestones` | Create milestone |
| M2 | PATCH `/projects/{projectId}/milestones/{milestoneId}` | Update milestone |
| M2 | GET `/projects/{projectId}/overview` | Read role-aware internal overview |
| M2 | GET `/projects/{projectId}/delivery-summary` | Read client-safe delivery summary |
| M2 | PATCH `/projects/{projectId}/archive` | Archive project |
| M3 | POST `/projects/{projectId}/work-items` | Create epic, story or task |
| M3 | GET `/projects/{projectId}/work-items` | Query internal work for List/Board/backlog |
| M3 | GET `/projects/{projectId}/work-items/{workItemId}` | Read internal work item |
| M3 | PATCH `/projects/{projectId}/work-items/{workItemId}` | Update ordinary work planning/progress |
| M3 | PATCH `/projects/{projectId}/work-items/{workItemId}/assignee` | Assign ordinary work |
| M3 | POST `/projects/{projectId}/work-items/{workItemId}/subtasks` | Create child TASK |
| M3 | POST `/projects/{projectId}/work-items/{workItemId}/links` | Link same-project work items |
| M3 | POST `/projects/{projectId}/bugs` | Report BUG Work Item |
| M3 | GET `/projects/{projectId}/bugs` | Query internal bug queues |
| M3 | GET `/projects/{projectId}/bugs/{bugId}` | Read specialized BUG details |
| M3 | POST `/projects/{projectId}/bugs/{bugId}/triage` | Triage or reassign reopened BUG |
| M3 | PATCH `/projects/{projectId}/bugs/{bugId}/assignee` | Assign/reassign BUG fixer |
| M3 | PATCH `/projects/{projectId}/bugs/{bugId}/verifier` | Designate independent BUG verifier |
| M3 | POST `/projects/{projectId}/bugs/{bugId}/start-work` | Start assigned BUG fix |
| M3 | POST `/projects/{projectId}/bugs/{bugId}/submit-fix` | Submit assigned fix for verification |
| M3 | POST `/projects/{projectId}/bugs/{bugId}/verification` | Verify or reopen a BUG fix |
| M3 | GET `/projects/{projectId}/bugs/{bugId}/history` | Read append-only BUG history |
| M3 | GET `/projects/{projectId}/work-summary` | Read internal work aggregates |
| M3 | GET `/projects/{projectId}/delivery-progress` | Read client-safe progress aggregate |

### Added M2/M3 schemas

`AddProjectMemberRequest`, `AssignBugVerifierRequest`, `AssignWorkItemRequest`, `BugHistoryPageResponse`, `BugHistoryResponse`, `BugPageResponse`, `BugResponse`, `BugSeverity`, `BugStatus`, `BugTriageOutcome`, `ClientDeliveryStatus`, `ClientDeliverySummaryResponse`, `ClientMilestonePageResponse`, `ClientMilestoneResponse`, `ClientWorkProgressSummary`, `CreateMilestoneRequest`, `CreateProjectRequest`, `CreateSubtaskRequest`, `CreateWorkItemLinkRequest`, `CreateWorkItemRequest`, `EffectiveProjectPermissionsResponse`, `InternalProjectPermissionsResponse`, `ViewerProjectPermissionsResponse`, `MilestonePageResponse`, `MilestoneResponse`, `MilestoneStatus`, `OrdinaryWorkItemResponse`, `ProjectAction`, `ProjectConfigurationResponse`, `ProjectLandingPage`, `ProjectMemberPageResponse`, `ProjectMemberResponse`, `ProjectOverviewResponse`, `ProjectPageResponse`, `ProjectPreferenceFields`, `ProjectPreferenceResponse`, `ProjectResponse`, `ProjectResponsibility`, `ProjectRole`, `ProjectStatus`, `ProjectSummaryResponse`, `ProjectView`, `ReportBugRequest`, `SubmitBugFixRequest`, `TransferOwnershipRequest`, `TriageBugRequest`, `UpdateMilestoneRequest`, `UpdateProjectConfigurationRequest`, `UpdateProjectMemberRequest`, `UpdateProjectPreferenceRequest`, `UpdateProjectRequest`, `UpdateWorkItemRequest`, `VerificationResult`, `VerifyBugRequest`, `WorkItemLinkResponse`, `WorkItemLinkType`, `WorkItemPageResponse`, `WorkItemPriority`, `WorkItemResponse`, `WorkItemStatus`, `WorkItemSummaryResponse`, `WorkItemType`.


## Users V7 organization permissions

See [Users permissions](users-permissions.md) for persistence, the Admin replacement
API and Projects transaction contract. V5 Projects foundation, V6 Work Items foundation
and V7 organization permissions (including PROJECT_CREATE) are merged into main.
POST `/api/v1/projects` is merged into main through PR #17. It creates the creator's designated Owner/MANAGER membership
automatically. Eligible-user selection and membership GET/POST are merged through PR #18; membership updates/removal remain planned.
Existing migrations are preserved; the verified sequence is V1/V3/V4/V5/V6/V7.

Compatibility clarification: the legacy draft permission route used an opaque
UserId example and omitted 409. The implemented route uses actual Users UUIDs and
returns 409 INVALID_PERMISSION_TARGET when an inactive target would receive a NEW
grant. All existing response fields remain; permissions and effectivePermissions
are additive. Revoke inactive targets with an empty array; unchanged stored grants
may remain, but never confer effective access while inactive.

### Project creation implementation

POST `/api/v1/projects` returns 201 and the agreed ProjectResponse. Actor identity comes
only from WorkSyncPrincipal. ACTIVE status and a current direct PROJECT_CREATE grant
are checked by Users.requirePermissionForWrite inside an outer writable READ_COMMITTED
transaction, before Projects writes. ADMIN alone grants no creation authority. Revocation
is serialized by the existing Users lifecycle lock, held through Project/membership commit.
Validation rejects unknown properties, non-string JSON values for all text fields,
blank/overlong names and reversed date ranges. Optional text fields may be omitted;
explicit null text values are rejected, matching their non-nullable OpenAPI definitions.
Dates may be omitted or null and retain the existing date parsing rules.
Safe responses use 400 for invalid bodies, 401 for unauthenticated callers, 403 for
missing effective permission (pending accounts receive 403; Auth rejects blocked/rejected tokens with 401), and 409 for persistence conflicts after rollback.

Initial creation response defaults are temporary and explicitly approved for this slice:
`permissions.role=MANAGER`, `responsibility=null`, `isOwner=true`, `actions=[]`,
`configuration.enabledViews=[]`, `configuration.enabledWorkTypes=[]`. Overview and Planning
remain mandatory. No configurable-feature persistence, additional permission grants or
Work Item functionality is enabled. Only currently implemented project API capabilities
may be advertised; no follow-on project actions are advertised yet. The configurable-feature
policy remains pending team review. These creation-only defaults must not be reused as a
general authorization projection for future project read/member APIs. This creation slice did not include membership management, project update/archive or frontend endpoints; see the team-management slice below.

Project creation tests include a real deferred Owner/MANAGER foreign-key failure at
outer commit, not only a simulated repository exception. The complete populated
response shape and nested creation defaults are asserted. Automated evaluation of
responses against the entire OpenAPI schema remains a follow-up; no new generic
schema-validation infrastructure is introduced in this slice.

## Project team management — merged through PR #18

POST `/projects` is merged through PR #17. The following additions are implemented
in main through PR #18; membership PATCH/DELETE, ownership
transfer, project archive/update and frontend work remain planned:

| Endpoint | Authorization and behavior |
| --- | --- |
| GET `/projects/{projectId}/eligible-users` | Current ACTIVE Owner/MANAGER; archived project 409; excludes all existing members in PostgreSQL |
| POST `/projects/{projectId}/members` | Owner adds MANAGER/MEMBER/VIEWER; additional MANAGER adds MEMBER/VIEWER only; 201 safe member response |
| GET `/projects/{projectId}/members` | ACTIVE Owner/MANAGER and MEMBER; VIEWER denied; archived team remains readable |

All actor identities come from WorkSyncPrincipal. ADMIN and PROJECT_CREATE grants
alone never provide project access. Unknown/non-disclosable projects return 404;
known participants lacking capability return 403. Bad request/UUID/pagination data
returns 400. Archived writes, duplicate memberships and missing/inactive targets
return safe 409 without disclosing target account details. MEMBER requires one
TECHNICAL_LEAD/DEVELOPER/QA_TESTER responsibility; MANAGER/VIEWER require none.
Unknown body properties, including ownership fields, are rejected. Owner designation
is never changed. Member responses follow the agreed fields plus additive `isOwner`;
no email, phone, account status or credentials appear in internal team responses.

### Deliberate lookup dependency

Users owns UserSelection (id, firstName, lastName, email), bounded ACTIVE search,
and user repository access. No general public or authenticated directory endpoint
is exposed. Projects authorizes Owner/MANAGER access before calling Users. The
explicitly approved read-only Users query references the existing V5 table
`project_members(project_id,user_id)` using NOT EXISTS. This deliberate SQL schema
coupling avoids loading all users/members and permits identical ACTIVE/search/
exclusion predicates for page content and totalElements. Renaming that Projects
schema requires coordination with Users. There is no cross-domain repository access,
JPA relationship or new migration. Search is optional, trimmed/case-insensitive literal
full-name/email substring matching (max 100); % and _ are literal. Ordering is fixed
lower(last_name), lower(first_name), id ASC. Pages are zero-based, size 1–100, default 20.

Team pages use createdAt DESC,userId DESC and batch-load safe display names only for
that page's users, including inactive existing members. Membership is not silently
removed when an account is blocked. The agreed policy permits current ACTIVE
Project Owners, MANAGERs and MEMBERs to view the internal team directory. VIEWERs
must not access it. Eligible-user search remains restricted to Owner/MANAGER.

### Account lifecycle concurrency

Additions run in one writable READ_COMMITTED transaction. A trusted Users lookup
write guard joins it with MANDATORY propagation, reuses the exact V7 lifecycle
advisory lock, and checks current ACTIVE actor state using a scalar query. Projects
then locks/refetches the Project row and rechecks membership/Owner rules. A fresh
ACTIVE target query occurs under the same Users lock before insertion. All locks
remain until outer commit/rollback. Blocking winning the lock prevents an addition;
a successful insertion winning the lock commits before blocking can finish. Existing
V7 permission checks and lock semantics remain unchanged. The existing unique
project/user constraint remains authoritative for concurrent duplicates. No existing
migration, Auth, Notifications, Work Items or frontend behavior is modified.

Team endpoint pagination is validated before repository access. The supported offset
is `(long) page * size <= 2147483647`, with page >= 0 and size 1–100. Unsupported
offsets return safe 400 INVALID_PROJECT_REQUEST; the exact maximum offset is accepted
(e.g. page=2147483647,size=1) and returns an empty page with the matching total when
beyond the available rows. These bounds apply only to the team endpoints and the
Users directory helper; no unrelated pagination behavior is changed.

## Projects list, details and updates — feature/projects-list-details-update

Project Creation (PR #17) and Team Management (PR #18) are merged. This branch adds
only the following endpoints; other planned routes retain their existing status:

| Endpoint | Behavior |
| --- | --- |
| GET `/projects` | ACTIVE actor's own memberships only; optional status filter; safe ProjectSummaryResponse pages |
| GET `/projects/{projectId}` | Current ACTIVE participant; internal ProjectResponse for Owner/MANAGER/MEMBER, safe summary for VIEWER |
| PATCH `/projects/{projectId}` | Current ACTIVE Owner/MANAGER only; partial brief/date update; archived projects reject writes |

No ADMIN or PROJECT_CREATE bypass exists. The list query joins current project
memberships in PostgreSQL, filters status before pagination/counting, and fixes
createdAt DESC,id DESC ordering. Only status/page/size are accepted. Offset validation
uses long arithmetic and the existing 2147483647 maximum. Empty memberships return
empty content; hidden/missing project IDs return safe 404. Viewers never receive
creator/Owner identifiers, briefs, team data, internal configuration or permissions.
The summary contains only id/name/status plus the actor's role/responsibility/isOwner.
Internal responses compute those permissions from the actor's actual membership;
the approved empty optional-feature configuration/actions remain temporary defaults.

PATCH tracks field presence: omitted values retain their current stored values;
explicit null clears startDate/dueDate only. All six text fields require JSON strings;
name must remain nonblank and <=200 characters. Unknown/protected properties,
empty patches, malformed dates and invalid merged date ranges return safe 400.
Validation applies after merging the patch with the locked current Project, so
concurrent partial updates preserve each other's omitted fields. The outer writable
READ_COMMITTED transaction reuses Users' existing ACTIVE-actor lifecycle guard
before the Project row lock, then refreshes and rechecks authority/status. No new
Users interface, permission grant, migration or ownership/membership change is needed.
The complete transaction rolls back on failure; updatedAt changes on persisted edits.
Owner transfer, archive, member PATCH/DELETE, feature settings and frontend work
remain outside this slice. No new unresolved contract decisions are introduced;
previous configurable-feature policy and generic response-schema validation remain
follow-ups.
