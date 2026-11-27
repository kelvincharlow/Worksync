# Users V7: direct organization permissions

This feature adds PROJECT_CREATE to Users-owned direct grants. It does not enable
ProjectService creation, add Projects controllers, change project roles or alter JWT
handling. Registration, account approval and system ADMIN do not grant permissions.

## Admin API and safe responses

PATCH `/api/v1/admin/users/{userId}/permissions` replaces the ENTIRE collection:

```json
{"permissions":["PROJECT_CREATE"]}
```

Revoke with `{"permissions":[]}`. There are no incremental PUT/DELETE routes.
Actor UUID comes from WorkSyncPrincipal. Auth's current security chain requires
ACTIVE ADMIN; UserService rechecks database role/status after acquiring the Users
lifecycle lock. New grants require an ACTIVE target. Inactive targets may retain
an identical stored grant or have it revoked, but cannot exercise it.

Missing/null collections, null entries, duplicates, unsupported names and unknown
fields return 400. No authentication/invalid token returns 401; ordinary/pending
actors are denied by the existing chain (403), while blocked/rejected tokens are
not authenticated (401). Missing target is 404; new grant to inactive target is
409 INVALID_PERMISSION_TARGET. Errors use safe Users code/message/timestamp.

All existing UserResponse fields remain. Read/update/profile/admin/list responses
now additionally contain:

```json
{"permissions":["PROJECT_CREATE"],"effectivePermissions":["PROJECT_CREATE"]}
```

Those are field excerpts, not complete response bodies. On an inactive account,
permissions can remain `["PROJECT_CREATE"]` while effectivePermissions is `[]`.
The same old JWT cannot preserve a revoked grant: effective authorization reads
the database. Responses are display snapshots, never authority for later writes.
No password/hash is returned. Actor IDs/roles/statuses in request JSON are rejected.

## Persistence and replacement

V7 creates organization_permissions with UUID ID, user UUID, named permission,
grantor UUID and grant timestamp. Foreign keys restrict User deletion, unique
(user_id, permission) prevents duplicates, and a CHECK limits supported names.
The unique index covers target lookups; a grantor index supports the other FK.
No grants are seeded for existing/new users. Future named grants require enum and
CHECK updates via a NEW migration, not new boolean columns or rewritten V7.

Under the lifecycle lock, replacement computes removals/additions atomically.
Unchanged grants preserve ID, grantor and timestamp. Empty replacement deletes all.
Rollback restores prior state. Revocation does not alter Projects or membership.
The grant row is attribution, not a permanent audit trail: revocation removes it;
a permanent grant/revoke event history remains a separate decision.

## Trusted Users service contract

- `updatePermissions(actorId, targetId, UpdatePermissionsRequest)`: actor-aware
  complete replacement, writable READ_COMMITTED transaction.
- `getGrantedPermissions(userId)`: direct stored names, not effective authority.
- `hasPermission(userId, permission)`: acquires the lifecycle lock and refreshes
  account state; false for inactive/no grant. Its own transaction may end on return,
  so it MUST NOT authorize a subsequent write outside that transaction.
- `requirePermissionForWrite(userId, permission)`: MANDATORY transaction guard for
  future Projects integration; throws on missing grant/inactive account.

A future ProjectService must invoke the proxied Users bean inside its own writable,
explicit READ_COMMITTED transaction BEFORE acquiring Projects/Work Items row locks:

```java
// Inside the future ProjectService writable READ_COMMITTED transaction:
users.requirePermissionForWrite(actorId, OrganizationPermission.PROJECT_CREATE);
// Then persist project and initial Owner/MANAGER before the outer commit.
```

The snippet explains ordering only; no implementation is added to ProjectService.
No transaction fails through Spring's MANDATORY propagation. Read-only, implicit
unspecified isolation and snapshot transactions are rejected by the guard. It never
uses REQUIRES_NEW. Scalar permission queries and refreshed account state avoid stale
managed entities. The existing PostgreSQL advisory lock (1465075027, 1) remains held
until the OUTER commit/rollback. Lifecycle changes and permission replacement use
the same lock: if the guarded write wins, revocation waits for it to commit; if
revocation wins, the later guard denies. Acquire Users lock before domain row locks
consistently. Direct SQL bypasses the protocol and is not a supported grant path.

## Migration ordering and tests

V5 remains Projects, V6 remains Phoebe's Work Items, V7 is Users permissions.
V6 is not in this branch; never copy or fabricate it to run tests. A fresh disposable
database can migrate V1/V3/V4/V5/V7, but that does not prove compatibility with the
revised V6. Applying V7 to a shared database before later introducing lower V6 can
cause Flyway validation/order problems. Coordinate V6 before shared application of
V7. Do not renumber, rewrite applied migrations or silently enable out-of-order.

Tests use disposable PostgreSQL with TEST_DB_URL/USER/PASSWORD, a process-only test
JWT_SECRET and mocked JavaMailSender. No private .env or production database is used.
Run focused tests from backend with those test variables configured:

```sh
./mvnw -Dtest=UserPermissionServiceTests,OrganizationPermissionIntegrationTests,UserServiceTests,UserControllerSecurityTests test
./mvnw verify
```

Tests cover the real security chain, strict validation, default/no automatic grants,
stored/effective state, database constraints, rollback, cache freshness, lock ordering
and both guard/revocation orderings. Manual SMTP tests remain excluded/opt-in.
