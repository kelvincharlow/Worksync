# Module 1 API contract

**Status: Auth is implemented in main; Users Step 5B Parts 1 and 2 are implemented
on the current `feature/users-admin-actions` branch.** Part 1 provides profile and
administrator reads; Part 2 adds approval, rejection and block/unblock endpoints.
Auth registration, login and password-reset behavior is reused unchanged.
Future role/permission management and in-app Notifications endpoints remain planned.

[openapi.yaml](openapi.yaml) records the supplied OpenAPI 3.0.3 contract, version 0.2.0. It is the detailed schema reference for review. [Contract review notes](module1-contract-review-notes.md) record the supplied scope and security considerations. Agree changes in a pull request before implementing controllers or frontend clients.

## Endpoint scope

Base URL: `http://localhost:8080/api/v1`. JSON requests/responses. The implemented Users operations use `Authorization: Bearer <JWT>`; public Auth registration/login opt out. All administrator operations require `ROLE_ADMIN` and `STATUS_ACTIVE`.

| Status | Method and path | Behavior / draft contract |
| --- | --- | --- |
| IMPLEMENTED — MAIN (AUTH) | POST /auth/register | Auth-owned; public; 201 safe user, PENDING_APPROVAL/USER, no token |
| IMPLEMENTED — MAIN (AUTH) | POST /auth/login | Auth-owned; public; 200 accessToken, tokenType, expiresIn and safe user |
| IMPLEMENTED — MAIN (AUTH) | POST /auth/forgot-password | Auth-owned reset-code request; draft path mismatch below |
| IMPLEMENTED — MAIN (AUTH) | POST /auth/reset-password | Auth-owned reset confirmation; draft path mismatch below |
| IMPLEMENTED — USERS FEATURE BRANCH | GET /users/me | Own profile; ACTIVE or PENDING_APPROVAL |
| IMPLEMENTED — USERS FEATURE BRANCH | PUT /users/me | Update own names and phone number only; ACTIVE or PENDING_APPROVAL |
| IMPLEMENTED — USERS FEATURE BRANCH | GET /admin/users | ACTIVE ADMIN; optional status filter and pagination |
| IMPLEMENTED — USERS FEATURE BRANCH | GET /admin/users/{userId} | ACTIVE ADMIN; retrieve one account; 404 if absent |
| IMPLEMENTED — USERS FEATURE BRANCH | PATCH /admin/users/{userId}/approve | ACTIVE ADMIN; PENDING_APPROVAL → ACTIVE |
| IMPLEMENTED — USERS FEATURE BRANCH | PATCH /admin/users/{userId}/reject | ACTIVE ADMIN; PENDING_APPROVAL → REJECTED; optional reason |
| IMPLEMENTED — USERS FEATURE BRANCH | PATCH /admin/users/{userId}/status | ACTIVE ADMIN; ACTIVE ↔ BLOCKED; never a substitute for approval |
| PLANNED | GET /notifications | ACTIVE only; own notifications; PENDING_APPROVAL denied |
| PLANNED | PATCH /notifications/{notificationId}/read | ACTIVE only; own notification only; 200 updated NotificationRecord |
| PLANNED — LATER | POST /auth/logout | Actually revoke presented JWT; 204 only after revocation |
| PLANNED — LATER | PATCH /admin/users/{userId}/permissions | Replace additional organization-level permission grants |
| PLANNED — LATER | PATCH /admin/users/{userId}/role | Change system role under high-trust administrator policy |

In-app notification endpoints remain planned. The YAML explicitly marks the three later operations with `x-implementation-phase: later`.

## Implemented Users behavior

See [Users REST endpoints](users-rest-endpoints.md) for the exact security and response contract.
The implemented single-user operations return UserResponse: UUID id, firstName, lastName, email,
phoneNumber, role, status, createdAt and updatedAt. No permissions or credentials.
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
- Account statuses: PENDING_APPROVAL, ACTIVE, REJECTED, BLOCKED. System roles: USER, ADMIN. Additional organization permissions are planned, not implemented by Step 5B; the draft proposes unique strings, initially PROJECT_CREATE, with no automatic grant on approval. Project roles are separate future memberships.
- List responses contain **content**, page, size and totalElements. Pages are zero-based; size defaults to 20 and is bounded to 1–100. User lists support an account status filter; notifications support READ/UNREAD status.
- NotificationRecord contains id, type, title, message, status and createdAt, with optional nullable readAt. Types: ACCOUNT_REGISTERED, ACCOUNT_APPROVED, ACCOUNT_REJECTED, ACCOUNT_BLOCKED and GENERAL. Another user's notification cannot be read or modified through these endpoints; a read-update attempt returns 404 when missing/not owned.
- The draft proposes errors using **application/json** with code, message and timestamp, plus optional fieldErrors containing field/message pairs. This replaces the earlier Problem Details proposal. Never include passwords, hashes, SQL or stack traces.

Expected error statuses: 400 invalid input, 401 invalid/missing/expired authentication, 403 forbidden access or blocked/rejected login, 404 missing resource, 409 duplicate email or invalid account transition, and 429 rate limiting. See the YAML for each operation's declared responses. These are draft expectations for other operations; the implemented Users behavior and inherited Auth security responses are described above.

## Team decisions still needed before implementation

Finalize JWT signing/key rotation, token lifetime, browser storage, CORS/CSRF behavior, prompt status/role/permission changes on existing tokens, and the later revocation/refresh strategy. Browser token disposal must not be presented as server-side revocation. Define secure one-time first-admin provisioning, audit logging and email normalization/uniqueness. For future endpoints, agree pagination sorting and detailed validation behavior. The implemented Users reads already use fixed ordering, and profile updates reject whitespace-only names. The supplied YAML is a draft and some operations do not yet enumerate malformed-input or blocked-token error cases; review those before implementation.

Email verification, rate limiting/abuse protection, administrator MFA as appropriate, and a complete token lifecycle policy remain production-readiness work outside Users Step 5B. Password-reset code is implemented in main, with the path mismatch noted above; this step does not assess its production readiness. Email delivery failure must not roll back an otherwise successful registration or approval.
