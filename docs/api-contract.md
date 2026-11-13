# Module 1 API contract

**Status: Auth is implemented in main; Users Step 5B Part 1 is implemented on
`feature/users-rest-endpoints`.** The four Users operations are GET/PUT /users/me,
GET /admin/users and GET /admin/users/{userId}.

Auth registration, login and password-reset endpoints were implemented by the Auth
module and merged into main; they were not implemented by Users Step 5B.
The Users branch was created before that merge. The remaining Users/Notifications
endpoints below are still planned.

[openapi.yaml](openapi.yaml) records the supplied OpenAPI 3.0.3 contract, version 0.2.0. It is the detailed schema reference for review. [Contract review notes](module1-contract-review-notes.md) record the supplied scope and security considerations. Agree changes in a pull request before implementing controllers or frontend clients.

## Endpoint scope

Base URL: `http://localhost:8080/api/v1`. JSON requests/responses. The four implemented Users operations use `Authorization: Bearer <JWT>`; public Auth registration/login opt out. Both implemented administrator read operations require `ROLE_ADMIN` and `STATUS_ACTIVE`.

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
| PLANNED | PATCH /admin/users/{userId}/approve | ADMIN; PENDING_APPROVAL → ACTIVE |
| PLANNED | PATCH /admin/users/{userId}/reject | ADMIN; PENDING_APPROVAL → REJECTED; optional reason |
| PLANNED | PATCH /admin/users/{userId}/status | ADMIN; ACTIVE ↔ BLOCKED; never a substitute for approval |
| PLANNED | GET /notifications | ACTIVE only; own notifications; PENDING_APPROVAL denied |
| PLANNED | PATCH /notifications/{notificationId}/read | ACTIVE only; own notification only; 200 updated NotificationRecord |
| PLANNED — LATER | POST /auth/logout | Actually revoke presented JWT; 204 only after revocation |
| PLANNED — LATER | PATCH /admin/users/{userId}/permissions | Replace additional organization-level permission grants |
| PLANNED — LATER | PATCH /admin/users/{userId}/role | Change system role under high-trust administrator policy |

In-app notification endpoints remain planned. The YAML explicitly marks the three later operations with `x-implementation-phase: later`.

## Implemented Users behavior

See [Users REST endpoints](users-rest-endpoints.md) for the exact security and response contract.
These four operations return UserResponse: UUID id, firstName, lastName, email,
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
administrator reads. Users validation/missing-user errors use safe
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

This Users branch predates the Auth merge: its current local SecurityConfig still
allows pending notification access, and its local Auth email dependency/configuration
still use the older setup. The rules above describe updated main, not verification
of those changes on this branch. No code or branch integration is performed here.

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

Finalize JWT signing/key rotation, token lifetime, browser storage, CORS/CSRF behavior, prompt status/role/permission changes on existing tokens, and the later revocation/refresh strategy. Browser token disposal must not be presented as server-side revocation. Define secure one-time first-admin provisioning, self-lockout/final-admin protection, audit logging and email normalization/uniqueness. For future endpoints, agree pagination sorting and detailed validation behavior. The implemented Users reads already use fixed ordering, and profile updates reject whitespace-only names. The supplied YAML is a draft and some operations do not yet enumerate malformed-input or blocked-token error cases; review those before implementation.

Email verification, rate limiting/abuse protection, administrator MFA as appropriate, and a complete token lifecycle policy remain production-readiness work outside Users Step 5B. Password-reset code is implemented in main, with the path mismatch noted above; this step does not assess its production readiness. Email delivery failure must not roll back an otherwise successful registration or approval.
