# Users REST endpoints — Step 5B Part 1

These four routes use Auth's existing SecurityConfig, JwtFilter and WorkSyncPrincipal.
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
do not change this order. The envelope contains only content, page, size and
totalElements. Records may move between pages if concurrent writes occur.

Missing/invalid/expired authentication returns 401. An authenticated ordinary
USER or PENDING_APPROVAL ADMIN receives 403 on administrator routes. Auth's
existing filter declines to authenticate BLOCKED and REJECTED accounts, so those
tokens currently receive 401. This behavior is inherited, not a new Auth policy.

Users-specific validation and missing-user errors use safe code/message/timestamp
JSON with 400 and 404 respectively. Auth retains its existing 401/403 handling;
those responses are not promised to use the Users JSON error envelope.

Security integration tests run the actual JWT service/filter/security chain.
Service integration tests exercise PostgreSQL pagination and protected fields.
Run backend/./mvnw verify with disposable TEST_DB_URL, TEST_DB_USER,
TEST_DB_PASSWORD and a test-only JWT_SECRET of at least 32 bytes. Never use a
production database. No email profile or live SMTP test is needed.

Auth's token validation checks signature, issuer and expiration; it currently
does not require an audience. Token lifecycle, CORS and other Auth policy remain
the Auth owner's responsibility. No Auth production code is changed here.
Approval/rejection/blocking controllers remain outside this step.
