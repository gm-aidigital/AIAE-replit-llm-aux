# Replit Custom Template — AI Digital MVP

## Overview

Custom Template configuration repository (not a runnable app). On fork, the
Replit Agent uses these rules/skills/artifacts to generate near-production
MVPs for non-technical users.

Generated MVPs run on Replit (SQL Database, port 5000), export to local-dev
via `docker-compose --profile local`, and hand off to engineering with full
quality gates.

Authoritative rules: `custom_instruction/instructions.md`. Workflows:
`.agents/skills/*/SKILL.md`. Canonical artifacts: `templates/generated-project/*`.

## User Preferences

- Stack lock absolute (Java 21 + Spring Boot 3.x + Maven; React + TS + Vite).
  Override phrase + forbidden list: `custom_instruction/instructions.md` →
  "ABSOLUTE STACK LOCK".
- Audience: non-technical product users; explanations stay business-focused.
- Default: dual-mode auth (Clerk + mock fallback), mocked/approved-only data,
  Replit Secrets for real keys.
- Code: simple over clever; canonical artifacts over hand-written boilerplate.
- UI: Elevate design system, compact product surfaces, no left side menu.
- Generation budget: reference canonical files, never paste — see
  `templates/generated-project/generation/token-efficient-generation-rules.md`.
- When in doubt: smallest demo that publishes on Replit; document what
  engineering must replace.

## System Architecture

### Template repo layout

```
custom_instruction/instructions.md         # authoritative rules
replit.md                                  # this file
.replit                                    # Replit workspace config
replit.nix                                 # Nix packages
.agents/skills/<name>/SKILL.md             # on-demand workflows
templates/generated-project/*              # canonical artifacts + scaffold/
.github/workflows/ci.yml                   # template integrity CI
```

### Generated project default stack

- **Backend**: Java 21 LTS + Spring Boot 3.x + Maven multi-module + PostgreSQL +
  Liquibase + HikariCP + Lombok + Checkstyle + JaCoCo. OpenAPI contract-first.
  Required modules are `application`, `service`, `domain`, `db`; optional
  `external-services` is generated only for real outbound integrations, never
  as an empty/POM-only module.
- **Frontend**: React + TypeScript + Vite + TanStack Query, typed via
  `openapi-typescript` + `openapi-fetch` from the backend OpenAPI YAML.
- **Auth**: dual-mode `AUTH_MODE=auto|sso|mock` (Clerk + backend-signed mock JWT).
- **Observability**: structured JSON logs to stdout, Actuator
  (`health`, `prometheus`), Postgres `usage_events` for usage estimation.
- **Runtime split**: Replit → profile `replit`, port `5000`, Replit Postgres
  env vars (`PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD`);
  Local-dev → profile `local`, port `8080`, `docker-compose --profile local`.

Authoritative-references table: `custom_instruction/instructions.md` →
"Authoritative references".

### Replit deployment model

- **Reserved VM** (`deploymentTarget = "gce"`) — not Autoscale (cold starts
  break warm JVM + JDBC pool).
- **One public port**: 5000 → externalPort 80. Spring Boot serves `/api/*` AND
  the built React SPA from `src/main/resources/static/`. Vite (5173) is
  workspace-preview only.
- `onBoot` runs `templates/generated-project/scaffold/scripts/setup-project.sh`.
- Workspace `[env]` does NOT propagate to Deployments. Before publish, copy
  `SPRING_PROFILES_ACTIVE`, `CLERK_*`, `AUTH_*`, `USAGE_LOG_*`, and any Replit
  Postgres env vars shown in the Secrets pane into Deployment Secrets when
  Replit does not auto-propagate them.

### Replit-only auth alternative

For Replit-only demos (no local-dev export),
[Replit Auth](https://docs.replit.com/references/auth-and-identity/authentication)
is zero-config. Template defaults to Clerk because generated apps must also
run locally.

## External Dependencies

All optional in MVP mode — generated projects must run on Replit with none set.

| Service | Purpose | Activation |
|---|---|---|
| **Clerk** | Google SSO. | `CLERK_PUBLISHABLE_KEY` + `CLERK_SECRET_KEY` in Replit Secrets. |
| **Google OAuth** (via Clerk) | Backing IdP. | Configured in Clerk Dashboard. |
| **Usage logging** | Telemetry → app's `usage_events` Postgres table. | `USAGE_LOGGING_ENABLED=true` + `USAGE_LOG_SERVICE_NAME`. |
| **PostgreSQL** | Persistence. | Replit `postgresql-16` injects Postgres env vars (`PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD`; `DATABASE_URL` may also exist). Backend config uses the individual vars directly; no custom post-processor and no forced SSL. Local-dev: docker-compose. See `.agents/skills/backend-java-feature/references/database-url-translation.md`. |

### Required env placeholders (`.env.example`)

Auth: `AUTH_MODE`, `AUTH_ISSUER_URI`, `AUTH_JWKS_URI`, `AUTH_AUDIENCE`,
`AUTH_MOCK_USER`, `AUTH_MOCK_JWT_SECRET`, `CLERK_PUBLISHABLE_KEY`,
`CLERK_SECRET_KEY`, `CLERK_SIGN_IN_FORCE_REDIRECT_URL`,
`CLERK_SIGN_UP_FORCE_REDIRECT_URL`.

Usage logging: `USAGE_LOGGING_ENABLED`, `USAGE_LOG_SERVICE_NAME`,
`USAGE_LOG_ENVIRONMENT`.

Provider setup (README only): `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`
(in Clerk Dashboard).

### Prohibited

- Production secrets or data in MVPs.
- Frontend reaching DB or service-account keys.
- Committing service-account JSON.
- Hardcoded auth secrets.
