# Module 1 OpenAPI — Contract Review Notes

**Status:** Draft for team approval. The supplied YAML is recorded at [openapi.yaml](openapi.yaml) for review; inclusion does not mark it as approved or implemented. Endpoints are designed, **not implemented**.

## Decisions reflected in this revision

- **JWT Bearer authentication** is retained. `POST /api/v1/auth/login` returns `accessToken`, `tokenType: Bearer`, `expiresIn` and a safe `user` object. The short-lived token lifetime shown in examples is 900 seconds, not a required production setting.
- Public registration accepts **firstName, lastName, email, phoneNumber, password**. **All five fields are required in this draft.** Phone numbers use international **E.164 format**, e.g. `+254712345678`; the frontend may normalize locally entered numbers before submission.
- New accounts begin with **`PENDING_APPROVAL`**, role **`USER`**, and no additional permissions. Registration **does not** issue an access token.
- Valid **PENDING_APPROVAL** users may log in and access only their own profile and account-related notifications. **REJECTED** and **BLOCKED** accounts cannot sign in.
- Admin users see pending registrations via `GET /api/v1/admin/users?status=PENDING_APPROVAL`; admin-only **approve**, **reject**, and **block/unblock** operations are distinct.
- System-level roles are **`ADMIN` / `USER`**. Future project-level **Manager / Member / Viewer** roles are separate; additional organization permissions are represented as strings (currently `PROJECT_CREATE`), not boolean flags.
- Standard errors include input validation (400), authentication failure (401), forbidden (403), not found (404), duplicate email (409), invalid status transition (409), and rate limiting (429).
- Notification endpoints return only the signed-in user's notifications. Attempts to mark another user's notification as read must not succeed.

## Scope guidance for the week

**First working milestone:** Registration, JWT login, `/users/me`, admin list/approve/reject/block, in-app registration/approval notifications if feasible. Secure first-admin creation must be separate from public registration.

**Clearly marked `x-implementation-phase: later` in the contract:** JWT revocation-based `/auth/logout`, admin permission grants and admin role changes. Browser-only logout by discarding a token **does not revoke that token**; don't claim otherwise.

**Before exposing public sign-up to production:** Implement and test email verification, password reset, rate limiting, MFA for admins as appropriate, and a complete JWT expiration/revocation/refresh policy.

## JWT and browser security checklist

- Use HTTPS and strong server-side signing key management; do not commit secrets.
- Validate JWT signature, issuer, intended API audience, expiry, and relevant permission/account status on every protected operation.
- Changes such as BLOCKED and REJECTED must promptly affect existing tokens, through a server-side status check or token invalidation mechanism; claims in an old JWT alone are not enough.
- Blazor must not treat a visible/hidden button as authorization. Spring Boot must enforce every access check.
- Avoid storing long-lived tokens in localStorage. Agree on short-lived tokens and browser storage behavior together.
- Apply administrator-only checks for **all** `/admin/**` endpoints, not just a JWT-presence check.

## API contract collaboration

The two backend developers and the Blazor developer should review the schemas and error responses before writing controllers. Assign each endpoint to one backend owner and avoid changing the contract without informing the frontend developer.
