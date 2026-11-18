# Vercel frontend deployment

The deployment configuration originates from `feature/vercel-deployment`, commit
`4864474`. That commit was not in `main`; Production therefore did not run the
Blazor publish step. Only deployment files are integrated here, preserving main's UI.

Vercel project settings:

- Root Directory: `frontend`.
- Framework Preset: Other.
- Build Command: `bash scripts/vercel-build.sh` (provided by vercel.json).
- Output Directory: `publish/wwwroot` (provided by vercel.json).
- No package install command is required. The script installs .NET SDK 10.0.403
  when no compatible .NET 10 SDK is available, then restores and publishes.
- Set `WORKSYNC_API_BASE_URL` for Production and relevant Preview environments to
  `https://worksync-api-0vnk.onrender.com/api/v1/`.

The script changes only published appsettings.json and ensures a trailing slash.
This URL is public browser configuration. Never include credentials, tokens or
secrets in it or any wwwroot file. Local source appsettings.json remains unchanged.
Python 3 and curl must be available in the build image.

The SPA rewrite serves index.html for application routes; existing static files
are served first by Vercel. Verify `/`, `/login`, and a real `_framework` asset
when a deployment is separately approved. Publishing checks are not a live Vercel
routing test.

Automatic Git deployment is disabled specifically for
`feature/vercel-production-build` in vercel.json so pushing this review branch
will not deploy it. Other branches retain their existing deployment behavior.
Do not merge or deploy until approved. After an approved merge, confirm build logs
show dotnet publish and check the published appsettings.json uses the intended URL.

Local verification from the repository root:

```sh
WORKSYNC_API_BASE_URL=https://worksync-api-0vnk.onrender.com/api/v1/ \
  bash frontend/scripts/vercel-build.sh
```

Temporary SDK and publish output are ignored by Git. No backend configuration is
changed by this build.
