# Module 1 API contract

**Status: DRAFT FOR TEAM REVIEW. All endpoints are PLANNED, not implemented.** The repository is a project skeleton; it has no business APIs or Worksync authentication flow.

[openapi.yaml](openapi.yaml) records the supplied OpenAPI 3.0.3 contract, version 0.2.0. It is the detailed schema reference for review. [Contract review notes](module1-contract-review-notes.md) record the supplied scope and security considerations. Agree changes in a pull request before implementing controllers or frontend clients.

## Planned endpoints

Base URL: `http://localhost:8080/api/v1`. JSON requests/responses. Protected operations use `Authorization: Bearer <JWT>` once implemented; public registration/login explicitly opt out. Every admin operation requires ADMIN plus an account status permitted to perform it.

| Status | Method and path | Planned behavior |
| --- | --- | --- |
| PLANNED | POST /auth/register | Public; 201 safe UserRecord, PENDING_APPROVAL/USER, no token |
| PLANNED | POST /auth/login | Public; 200 accessToken, tokenType, expiresIn and safe user |
| PLANNED | GET /users/me | Own profile; ACTIVE or PENDING_APPROVAL |
| PLANNED | PUT /users/me | Update own names and phone number only; ACTIVE or PENDING_APPROVAL |
| PLANNED | GET /admin/users | ADMIN; optional status filter and pagination |
| PLANNED | GET /admin/users/{userId} | ADMIN; retrieve one account |
| PLANNED | PATCH /admin/users/{userId}/approve | ADMIN; PENDING_APPROVAL → ACTIVE |
| PLANNED | PATCH /admin/users/{userId}/reject | ADMIN; PENDING_APPROVAL → REJECTED; optional reason |
| PLANNED | PATCH /admin/users/{userId}/status | ADMIN; ACTIVE ↔ BLOCKED; never a substitute for approval |
| PLANNED | GET /notifications | Own notifications; pending users restricted to account-related notifications |
| PLANNED | PATCH /notifications/{notificationId}/read | Own notification only; 200 updated NotificationRecord |
| PLANNED — LATER | POST /auth/logout | Actually revoke presented JWT; 204 only after revocation |
| PLANNED — LATER | PATCH /admin/users/{userId}/permissions | Replace additional organization-level permission grants |
| PLANNED — LATER | PATCH /admin/users/{userId}/role | Change system role under high-trust administrator policy |

In-app notifications are first-milestone work if feasible, not part of the skeleton. The YAML explicitly marks the three later operations with `x-implementation-phase: later`.

## Request and response expectations

- Registration requires **all five** fields: firstName, lastName, email, phoneNumber and password. Names: 1–80 characters; email: valid format, at most 254 characters; phoneNumber: E.164 (for example `+254712345678`); password: 12–128 characters. Do not silently trim/truncate passwords. Public registration cannot set role, status or permissions.
- Login accepts email/password. AuthResponse contains accessToken, tokenType `Bearer`, expiresIn in seconds and a safe user. The example 900-second lifetime is illustrative. Pending users can obtain tokens but remain restricted by the backend. BLOCKED/REJECTED accounts cannot obtain tokens.
- UserRecord contains opaque string id, firstName, lastName, email, phoneNumber, **role** (singular), status, permissions and createdAt; updatedAt is optional. IDs are strings, not a UUID contract. Passwords and hashes never appear in responses.
- Profile PUT requires firstName, lastName and phoneNumber. It cannot change email, role, status or permission grants. Email changes require a later verification flow.
- Account statuses: PENDING_APPROVAL, ACTIVE, REJECTED, BLOCKED. System roles: USER, ADMIN. Additional permissions are unique strings, initially PROJECT_CREATE; approval does not grant it. Project roles are separate future memberships.
- List responses contain **content**, page, size and totalElements. Pages are zero-based; size defaults to 20 and is bounded to 1–100. User lists support an account status filter; notifications support READ/UNREAD status.
- NotificationRecord contains id, type, title, message, status and createdAt, with optional nullable readAt. Types: ACCOUNT_REGISTERED, ACCOUNT_APPROVED, ACCOUNT_REJECTED, ACCOUNT_BLOCKED and GENERAL. Another user's notification cannot be read or modified through these endpoints; a read-update attempt returns 404 when missing/not owned.
- Errors use **application/json** with code, message and timestamp, plus optional fieldErrors containing field/message pairs. This replaces the earlier Problem Details proposal. Never include passwords, hashes, SQL or stack traces.

Expected error statuses: 400 invalid input, 401 invalid/missing/expired authentication, 403 forbidden access or blocked/rejected login, 404 missing resource, 409 duplicate email or invalid account transition, and 429 rate limiting. See the YAML for each operation's declared responses. These are planned business responses, not a description of the skeleton's framework responses.

## Team decisions still needed before implementation

Finalize JWT signing/key rotation, token lifetime, browser storage, CORS/CSRF behavior, prompt status/role/permission changes on existing tokens, and the later revocation/refresh strategy. Browser token disposal must not be presented as server-side revocation. Define secure one-time first-admin provisioning, self-lockout/final-admin protection, audit logging and email normalization/uniqueness. Agree stable pagination sorting and detailed validation behavior, including whitespace-only names. The supplied YAML is a draft and some operations do not yet enumerate malformed-input or blocked-token error cases; review those before implementation.

Email verification, secure password recovery, rate limiting/abuse protection, administrator MFA as appropriate, and a complete token lifecycle policy are production-readiness work. They are not implemented here. Email delivery failure must not roll back an otherwise successful registration or approval.
