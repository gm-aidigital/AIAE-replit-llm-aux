---
name: mvp-safety-review
description: Safety and release-readiness checklist for a generated MVP before sharing, publishing on Replit, or recording a demo. Use when the user says the project is ready, is about to share/publish, or is preparing a stakeholder demo.
metadata:
  user-invocable: "true"
---

# MVP Safety Review

Run before any publish / share / demo. Pure gate — does not change code.
Every check is binary: pass or **reject publish**.

Detailed grep commands and examples: `references/publish-gate-checks.md`.
Automated subset: `bash scripts/local-verify.sh` (includes `structure-lint.sh` + `verify-gates.sh`).

## Security and secret hygiene

- [ ] No real secrets committed (private keys, service-account JSON).
- [ ] `.env.example` placeholders only; README lists env vars and limitations.

## Auth (Clerk SSO only)

Canonical: `templates/generated-project/auth/google-sso-clerk-blueprint.md`.

- [ ] Backend fails fast without issuer/JWKS — **no mock/Replit OIDC fallback**.
- [ ] Login UI uses Clerk `<SignIn/>`; API calls send Bearer JWT.
- [ ] Backend validates JWT (JWKS, `iss`, `aud`, `exp`, `nbf`).
- [ ] No `MockJwtDecoder`, `ReplitOidcSecurityConfig`, or `/auth/mock/login`.

## Usage logging

Canonical: `templates/generated-project/observability/usage-logging-rules.md`.

- [ ] `usage_events` Liquibase runs; aspect auto-intercepts `*ServiceImpl`.
- [ ] `@Async("usageLoggingExecutor")` on persistence service, not on aspect.
- [ ] Real `USAGE_LOG_SERVICE_NAME` — not empty/placeholder.

## Quality

- [ ] OpenAPI updated; controllers implement generated `*Api`.
- [ ] Frontend: typed `apiClient` only; Elevate UI; no left sidebar.
- [ ] `SpaFallbackController` present for deployment deep links.
- [ ] `ProtectedRoute` wraps authenticated routes.

## Tests (hard reject if missing)

- [ ] Backend: smoke + auth boundary + service unit tests + Liquibase smoke.
- [ ] `mvn -f backend/pom.xml verify` passes.
- [ ] Frontend: `npm test && npm run build` when frontend exists.

## Replit deployment

- [ ] `.replit`: GCE deployment, port 5000→80, `setup-project.sh` on boot.
- [ ] Deployment Secrets include Clerk + auth + usage-logging vars.
- [ ] Vite on 5173 only; Spring on 5000.

## Stack lock (hard reject)

- [ ] No Python/Node backend files; parent POM at `backend/pom.xml`.
- [ ] Package namespace `com.aidigital.<app-name-package>.*`.
- [ ] Control plane (`.agents/`, `templates/`, `replit.md`) not in git.

## Non-publish conditions

- Production credentials required for local demo.
- Backend not Java + Spring Boot + Maven.
- Any item above fails.

Full checklist with bash snippets: `references/publish-gate-checks.md`.
