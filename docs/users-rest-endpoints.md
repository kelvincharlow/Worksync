# Users REST endpoints — Step 5B Part 1

These four routes are implemented on `feature/users-rest-endpoints`.
Auth registration, login and password-reset endpoints are implemented in main,
not by Users Step 5B. This Users branch was created before that merge.
These routes reuse Auth's SecurityConfig, JwtFilter and WorkSyncPrincipal.
Identity comes from the validated JWT subject; role and account status are reloaded
through UserService on every request. Controllers never access UserRepository.

| Method and path | Access | Result |
| --- | --- | --- |
| GET /api/v1/users/me | ACTIVE or PENDING_APPROVAL | Own UserResponse |
| PUT /api/v1/users/me | ACTIVE or PENDING_APPROVAL | Updated own UserResponse |
| GET /api/v1/admin/users | ACTIVE ADMIN | UserPageResponse |
| GET /api/v1/admin/users/{userId} | ACTIVE ADMIN | Target UserResponse or 404 |

PUT requires firstName and lastName (nonblank, at most 80 characters), plus
phoneNumber in E.164 format. All other JSON properties are rejected with 400,
including email, IDs, role, status, permissions and credentials. The authenticated
principal supplies the target UUID. A query parameter cannot change that identity.

UserResponse has UUID id, firstName, lastName, email, phoneNumber, role, status,
createdAt and updatedAt. Password hashes never appear in public responses.

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

This branch's local SecurityConfig still has the earlier pending-notification
rule. Its Auth email dependency and shared configuration also predate main's
updates. These docs record the updated main requirements; no code or branch
integration is performed in this documentation step.

Users-specific validation and missing-user errors use safe code/message/timestamp
JSON with 400 and 404 respectively. Auth retains its existing 401/403 handling;
those responses are not promised to use the Users JSON error envelope.

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

The earlier 129-test result was obtained with this branch's older Auth setup; it
does not verify updated main's required email dependency or included email
profile. Review test mocks when integrating those changes. No tests are changed
or rerun in this documentation-only step.

Auth's token validation checks signature, issuer and expiration; it currently
does not require an audience. Token lifecycle, CORS and other Auth policy remain
the Auth owner's responsibility. No Auth production code is changed here.
Approval/rejection/blocking, role/permission management, and in-app notification
endpoints remain planned and outside this step.

Auth's password-reset paths differ from the draft OpenAPI; see the
[Auth-owned path mismatch](api-contract.md#auth-owned-password-reset-path-mismatch).
Neither Auth code nor its endpoint contracts are changed by this review.
