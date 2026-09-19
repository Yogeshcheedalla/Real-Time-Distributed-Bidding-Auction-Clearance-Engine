# Deployment

## Local (native Windows — no Docker)

Order matters: Eureka first, then services (they self-register), gateway any time
after Eureka, frontend last.

```
scripts\start-eureka.bat      :8761
scripts\start-gateway.bat     :8080   (needs JWT_SECRET from .env)
scripts\start-auth.bat        :8081
scripts\start-auction.bat     :8082
scripts\start-bidding.bat     :8083
scripts\start-payment.bat     :8084
scripts\start-frontend.bat    :5173
scripts\check-infra.bat       port/service health snapshot
```

Each `start-*.bat` loads `.env` (git-ignored) and pins `JAVA_HOME` to a Temurin 21 JDK.
Port collisions: set `PORT=<n>` in `.env` per terminal (e.g. when Docker Desktop's WSL
relay holds :8080). Databases: `scripts\setup-databases.bat` (UTF8 required — emoji).

## Frontend on Vercel

The `frontend/` app builds statically; the API origin comes from the build-time env var
`VITE_API_BASE` (empty = same-origin/dev proxy). Already wired:

1. `vercel link` once (project `bidvelocity`)
2. `vercel env add VITE_API_BASE production` → your public gateway URL
3. `vercel deploy --prod --yes`

Current deployment: https://bidvelocity.vercel.app

## Backend hosting options

| Option | How | Notes |
|---|---|---|
| Cloudflared quick tunnel (used for the demo) | `cloudflared tunnel --url http://localhost:8080` | Free, ephemeral URL per restart; keep the stack running |
| Render / Railway / Fly.io | One web service per module + managed PostgreSQL (Neon/RDS) | Point `DB_HOST/PORT/USER/PASSWORD` env at it; set `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` or switch to static route URIs (`spring.cloud.gateway.routes[].uri=https://…`) when no shared Eureka network exists |
| Named tunnel + domain | `cloudflared tunnel create` + DNS | Stable public URL, stable Google redirect URI |

## Google OAuth in any deployment

Add the **exact** callback to Google Cloud Console → OAuth client (Web application):
`<public-gateway-origin>/login/oauth2/code/google` (locally:
`http://localhost:8080/login/oauth2/code/google`). The gateway rewrites
`X-Forwarded-Host` so the auth service builds this URI correctly. Client secret lives
only in the auth-service environment.

## CI

`.github/workflows/ci.yml`: JDK 21 + Maven build/test for all modules, Node build for
the frontend, dependency audit (report-only). No deploy credentials required.
