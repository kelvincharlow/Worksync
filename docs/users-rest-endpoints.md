# Users REST endpoints — Step 5B Parts 1 and 2

The profile/read routes and three lifecycle routes below are implemented in main.
Auth registration, login and password-reset endpoints are implemented in main,
not by Users Step 5B. The merged Auth and Notifications email modules are integrated.
These routes reuse Auth's SecurityConfig, JwtFilter and WorkSyncPrincipal.
Identity comes from the validated JWT subject; role and account status are reloaded
through UserService on every request. Controllers never access UserRepository.

| Method and path | Access | Result |
| --- | --- | --- |
| GET /api/v1/users/me | ACTIVE or PENDING_APPROVAL | Own UserResponse |
| PUT /api/v1/users/me | ACTIVE or PENDING_APPROVAL | Updated own UserResponse |
| GET /api/v1/admin/users | ACTIVE ADMIN | UserPageResponse |
| GET /api/v1/admin/users/{userId} | ACTIVE ADMIN | Target UserResponse or 404 |
| PATCH /api/v1/admin/users/{userId}/approve | ACTIVE ADMIN | PENDING_APPROVAL → ACTIVE |
| PATCH /api/v1/admin/users/{userId}/reject | ACTIVE ADMIN | PENDING_APPROVAL → REJECTED |
| PATCH /api/v1/admin/users/{userId}/status | ACTIVE ADMIN | ACTIVE ↔ BLOCKED |
| PATCH /api/v1/admin/users/{userId}/permissions | ACTIVE ADMIN | Replace complete direct permission collection (Users V7) |

PUT requires firstName and lastName (nonblank, at most 80 characters), plus
phoneNumber in E.164 format. All other JSON properties are rejected with 400,
including email, IDs, role, status, permissions and credentials. The authenticated
principal supplies the target UUID. A query parameter cannot change that identity.

UserResponse has UUID id, firstName, lastName, email, phoneNumber, role, status,
createdAt, updatedAt, stored permissions and derived effectivePermissions. Password hashes never appear in public responses.

The list accepts optional status (an exact AccountStatus enum value), page
(default 0, minimum 0), and size (default 20, range 1–100). Database pagination
uses createdAt DESC, id DESC for deterministic ordering. Client sort parameters
do not change this order. The envelope (`UserPageResponse` in Java, `PagedUsers`
in OpenAPI) contains only content, page, size and totalElements. Records may move
between pages if concurrent writes occur.

Missing/invalid/expired authentication returns 401. An authenticated ordinary
USER or PENDING_APPROVAL ADMIN receives 403 on administrator routes. Auth's
existing filter declines to authenticate BLOCKED and REJECTED accounts, so those
tokens currently receive 401. This behavior is inherited, not a new Auth policy.

Updated main SecurityConfig permits PENDING_APPROVAL accounts to GET and PUT
/api/v1/users/me. Access under /api/v1/notifications/** requires STATUS_ACTIVE;
pending accounts cannot access notifications, including account-related ones.
Administrator routes require both ROLE_ADMIN and STATUS_ACTIVE.

Users-specific validation and missing-user errors use safe code/message/timestamp
JSON with 400 and 404 respectively. Invalid lifecycle transitions and protected
administrator operations use the same safe envelope with 409. Auth retains its existing 401/403 handling;
those responses are not promised to use the Users JSON error envelope.

## Administrator account safety

Actor UUID comes only from WorkSyncPrincipal. Actor-aware service overloads recheck
ACTIVE ADMIN under the database transaction lock, so an actor disabled while waiting
cannot continue an administrator action. Administrators cannot block themselves.
The trusted one-argument blockUser method also protects the last ACTIVE ADMIN.

All Users updates share PostgreSQL transaction advisory lock (1465075027, 1);
the last-admin count and write are serialized across application instances through
commit/rollback. Managed entities are refreshed under the lock to avoid stale
profile/password writes restoring old statuses. READ_COMMITTED is required;
higher-isolation outer transactions fail closed. No new table or migration is needed.
Future write/role-management paths must use this protocol; direct SQL bypasses it.
The tradeoff is serialization of Users writes; reads remain unlocked.

Reject accepts no body, an empty object, or optional reason (at most 500 characters).
Status requires status ACTIVE or BLOCKED and accepts the same optional reason.
Unknown JSON properties are rejected. Reasons are not persisted, logged or delivered.
No audit trail, role change or notification creation is implemented.

Invalid transitions, self-blocking and last-admin protection return 409.
Current authorization failure returns 403; unauthenticated access returns 401.
Missing target UUID returns 404 and malformed UUID/JSON/request fields return 400.
Auth continues to reload database authorities on subsequent JWT requests, so
blocking immediately prevents further protected access with existing tokens.

## Automated test setup

Security integration tests run the actual JWT service/filter/security chain.
Service integration tests exercise PostgreSQL pagination and protected fields.
From `backend/`, run `./mvnw verify` with disposable `TEST_DB_URL`,
`TEST_DB_USER`, `TEST_DB_PASSWORD` and a test-only `JWT_SECRET` of at least 32 bytes.
Never use a production database.

Updated main Auth requires EmailNotificationService, and the shared application
configuration includes the email profile. Automated tests must mock email
delivery; omitting explicit email-profile activation is not a delivery safeguard.

For full application tests, replace EmailNotificationService with a Spring test
mock (for example, `@MockitoBean EmailNotificationService emailNotificationService`).
Keep the real JwtService, JwtFilter and SecurityConfig in controller/security tests.
Tests of email delivery itself should mock JavaMailSender rather than contact SMTP.

Use test-only placeholder mail configuration if included email configuration
requires values. Never load the private `.env` or use real Brevo credentials for
automated tests. Set `spring.mail.test-connection=false` to prevent startup
connection checks; this setting alone does not prevent sends, so mocks remain
required. Do not opt into manual live-email tests during normal verification.

PostgreSQL integration tests exercise competing administrator blocks, service-level
guards, entity refresh and committed HTTP transitions. Existing profile, Auth,
password-reset and mocked-email regression tests are retained.

Auth's token validation checks signature, issuer and expiration; it currently
does not require an audience. Token lifecycle, CORS and other Auth policy remain
the Auth owner's responsibility. No Auth production code is changed here.
System-role management and in-app notification endpoints remain planned and
outside this step.

Auth's password-reset paths differ from the draft OpenAPI; see the
[Auth-owned path mismatch](api-contract.md#auth-owned-password-reset-path-mismatch).
Neither Auth production code nor its password-reset routes are changed by this step.


## Swagger UI JWT authorization

Open `/swagger-ui/index.html` (or `/swagger-ui.html`) on the running backend.
Use Auth's public login endpoint to obtain an accessToken. Click **Authorize**,
select `bearerAuth`, and paste the token value without the `Bearer ` prefix.
Swagger UI adds the Authorization header when using **Try it out** on Users routes.

`UsersOpenApiConfig` declares an HTTP bearer scheme with JWT format. Both Users
controllers apply it to their operations. No global security requirement is set,
so public Auth operations remain documented without authentication. The generated
`/v3/api-docs` is the source used by Swagger UI; `docs/openapi.yaml` remains the
separate reviewed draft contract.

These annotations describe authentication only. Existing SecurityConfig, JwtFilter,
principal checks and UserService lifecycle guards enforce all actual access,
including ACTIVE ADMIN, self-blocking and last-admin restrictions.


Users V7 adds organization permission replacement; see [Users permissions](users-permissions.md). No Projects creation endpoint or V7 integration into ProjectService is enabled by this change.
