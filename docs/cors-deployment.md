# Frontend CORS configuration

Spring Security's existing CorsFilter runs before JwtFilter. Permitting OPTIONS alone
cannot approve an origin missing from the CORS allowlist. The reported Render service
accepted a localhost preflight but rejected the supplied Vercel preview origin with
`403 Invalid CORS request`.

## Render environment

Set this comma-separated exact-origin allowlist:

```text
CORS_ALLOWED_ORIGINS=https://worksync-frontend-gx8mj5zah-kelvins-projects-e3a5059b.vercel.app,http://localhost:5000,http://127.0.0.1:5000
```

Append the intended production frontend origin once its domain is known. Origins have
only scheme, hostname and optional port: no trailing slash, path, query or wildcard.
The browser page's origin is needed, not the Render API address. Preview URLs change
with deployments: approve individual URLs and remove obsolete ones; do not allow all
Vercel applications. Local origins may be omitted on Render if developers use only a
local backend. An unset variable defaults to localhost:5000 and 127.0.0.1:5000; an
explicitly empty value denies cross-origin access. Invalid/wildcard entries fail startup.

Redeploy/restart Render after setting the variable and deploying the code. No JWT
secret, BCrypt/password, database or SMTP changes are required.

## Policy

- Exact origins supplied through the existing app.cors.allowed-origins property.
- Methods: GET, POST, PUT, PATCH and OPTIONS, matching current API/client needs.
- Headers: Content-Type, Authorization and Accept (case-insensitive).
- Browser cookie credentials disabled: Bearer tokens are sent explicitly, and token
  responses use JSON. Authorization request headers remain supported.
- No exposed Authorization response header, because tokens are returned in JSON.
- CorsFilter completes allowed preflights before JWT processing. It does not authenticate
  later requests or grant role/status privileges. Existing route authorization is unchanged.

## Deployment verification

After deployment, send this unauthenticated preflight (no account or email side effects):

```bash
curl -i -X OPTIONS 'https://worksync-api-0vnk.onrender.com/api/v1/auth/login' \
  -H 'Origin: https://worksync-frontend-gx8mj5zah-kelvins-projects-e3a5059b.vercel.app' \
  -H 'Access-Control-Request-Method: POST' \
  -H 'Access-Control-Request-Headers: content-type'
```

Expect HTTP 200, Access-Control-Allow-Origin matching that exact preview URL, POST
among allowed methods, and content-type among allowed headers. No Allow-Credentials
header is intentional. Repeat for the production origin after adding it. An unlisted
origin such as https://attacker.example must return 403 without Allow-Origin.

Verify a protected request still requires authentication:

```bash
curl -i 'https://worksync-api-0vnk.onrender.com/api/v1/users/me' \
  -H 'Origin: https://worksync-frontend-gx8mj5zah-kelvins-projects-e3a5059b.vercel.app'
```

Expect 401 plus the allowed origin header. Then test browser login with an intended
test account: OPTIONS must succeed, and POST must return the application login result.
Invalid credentials may correctly return 401/403; CORS does not bypass authentication.
Check that the deployed Vercel appsettings.json uses
`https://worksync-api-0vnk.onrender.com/api/v1/`. Local frontend configuration may retain
its localhost development URL. No frontend source changes are needed for this CORS fix.

## Tests

CorsSecurityTests uses the actual Spring Security CorsFilter/JwtFilter ordering with
mock services and no database/SMTP. It covers the preview, a test-only production
origin, local origins, rejected origins/methods/headers, public Auth operations and
protected 401/403 responses. CorsConfigurationTests rejects invalid/wildcard origins.
Full verification uses disposable PostgreSQL, test-only JWT secrets and mocked email.
