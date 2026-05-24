# Replit Custom Template — AI Digital MVP

## STACK LOCK (read this first, no exceptions)

**Every backend generated from this template MUST be Java 21 LTS + Spring Boot 3.x + Maven.**
**Every frontend MUST be React + TypeScript + Vite.**

The following stacks are **FORBIDDEN** in generated projects, even when the user
prompt describes the app in a way that would naturally fit them:

- Python (Flask, Django, FastAPI, Quart, Bottle, Tornado, Sanic)
- Node.js (Express, Fastify, Koa, NestJS, Hapi)
- Next.js, Nuxt, Remix, SvelteKit, Astro (full-stack frameworks)
- Go (Gin, Echo, Fiber), Ruby (Rails, Sinatra), PHP (Laravel, Symfony)
- Static-only SPAs with no backend, when the prompt implies persistence or auth

If a prompt asks for "a simple page", "a small tool", "a quick demo", a
"Python script", or names any forbidden stack — **still generate Java + Spring
Boot + React**. The template's purpose is the canonical company stack;
deviating defeats the entire reason this template exists.

**Only override** if the user message explicitly contains a phrase like
"override stack lock", "ignore the template stack", or "use Python/Node
backend instead". Implicit hints ("looks like a Flask app", "Python would be
simpler", "Express has good middleware for this") are NOT overrides.

If you encounter Replit-managed Clerk Auth documentation that mentions Express
middleware: that auto-wiring **does not apply to this template** (we're on
Spring Boot, not Node). Validate JWTs via `spring-boot-starter-oauth2-resource-server`
+ Clerk JWKS instead — same identity, different code path.

## Overview

This is a Replit Custom Template configuration repository, not a runnable app.
When forked by the Replit Agent it provides the rules, skills and canonical
artifacts the Agent uses to generate near-production MVPs for non-technical
users at the company.

Generated MVPs are designed to:
- run **on Replit** as a publishable demo (Replit-native PostgreSQL, port 5000),
- export cleanly **to a local developer machine** (`docker-compose --profile local`),
- be **handed off to engineering** with full quality gates.

Authoritative rules live in `custom_instruction/instructions.md`. Workflow
skills live in `.agents/skills/*/SKILL.md`. Canonical generated-project
artifacts (CI, plugin snippets, blueprints, structure rules) live in
`templates/generated-project/*`.

Agent keeps this file updated as the project evolves but must not remove the
links to instructions, skills, or canonical artifacts below.

## User Preferences

- Audience: non-technical product users; explanations stay business-focused.
- Default MVP path: dual-mode auth (Clerk + mock fallback), mocked or
  approved-only data, Replit Secrets for real keys.
- Code style: simple and maintainable over clever; prefer the canonical
  artifacts over hand-written boilerplate.
- Generation budget: token-efficient — reuse canonical files via reference,
  never paste them. See
  `templates/generated-project/generation/token-efficient-generation-rules.md`.
- When in doubt about scope, ship the smallest demo that publishes on Replit
  and document what engineering must replace before production.

## System Architecture

### Template repository organization

```
custom_instruction/instructions.md         # authoritative company rules
replit.md                                  # this file (living entrypoint)
.replit                                    # Replit workspace config
replit.nix                                 # Replit Nix packages
.agents/skills/<name>/SKILL.md             # on-demand workflows
templates/generated-project/*              # canonical artifacts copied into
                                           # generated projects
config/checkstyle.xml                      # Checkstyle config (Java backends)
config/checkstyle-suppressions.xml         # Checkstyle suppressions
.github/workflows/ci.yml                   # template integrity CI
```

### Generated project (default stack)

- **Backend**: Java 21 LTS (Java 25 LTS when Replit's nixpkgs channel supports it) + Spring Boot 3.x + Maven multi-module + PostgreSQL +
  Liquibase + HikariCP + Lombok + Checkstyle + JaCoCo. OpenAPI contract-first.
- **Frontend**: React + TypeScript + Vite + TanStack Query, typed via
  `openapi-typescript` + `openapi-fetch` from the backend OpenAPI YAML.
- **Auth**: dual-mode `AUTH_MODE=auto|sso|mock` — Clerk (Google social
  connection) for real, backend-signed JWT mock for fallback.
- **Observability**: structured JSON logs to stdout, Actuator (`health`,
  `prometheus`), Postgres `usage_events` table for usage estimation.
- **Runtime split**:
  - On Replit → profile `replit`, port `5000`, Replit SQL Database via `DATABASE_URL`.
  - On local-dev → profile `local`, port `8080`, `docker-compose --profile local`.

Canonical contracts to read before generating code:

| | File |
|---|---|
| Project structure | `templates/generated-project/structure/near-production-project-structure.md` |
| OpenAPI | `templates/generated-project/openapi/canonical-openapi-rules.md` |
| Frontend | `templates/generated-project/frontend/canonical-react-frontend-rules.md` |
| Auth | `templates/generated-project/auth/google-sso-clerk-blueprint.md` |
| Usage logging | `templates/generated-project/observability/usage-logging-rules.md` |
| Token efficiency | `templates/generated-project/generation/token-efficient-generation-rules.md` |
| CI | `templates/generated-project/.github/workflows/ci.yml` |
| Plugin snippets | `templates/generated-project/pom-snippets/*.xml` |
| Starter scaffold | `templates/generated-project/scaffold/` |
| Testing policy (phased) | `templates/generated-project/testing/testing-policy.md` |

## External Dependencies

Real dependencies are *optional* in MVP mode — the template ships with safe
fallbacks. Generated projects must run on Replit without any of them set.

| Service | Purpose | Activation |
|---|---|---|
| **Clerk** | Google SSO via social connection. | Set `CLERK_PUBLISHABLE_KEY` and `CLERK_SECRET_KEY` in Replit Secrets to switch from mock to real SSO. |
| **Google OAuth** (via Clerk) | Backing IdP. | Configured inside Clerk Dashboard, not as application secrets. |
| **Usage logging** | Anonymous user-action telemetry → app's PostgreSQL `usage_events` table. Manager queries it for usage estimation. | Set `USAGE_LOGGING_ENABLED=true` (default) and `USAGE_LOG_SERVICE_NAME`. See `templates/generated-project/observability/usage-logging-rules.md`. |
| **PostgreSQL** | Persistence when the app stores data. | Replit's **SQL Database** (the managed Postgres product — *not* the legacy key-value `Database`) is provisioned by the `postgresql-16` module declared in `.replit`. It injects a single `DATABASE_URL` env var (libpq URL with `sslmode=require`); the Java backend converts it to JDBC at start. On local-dev, `docker-compose --profile local` brings up Postgres. See `.agents/skills/backend-java-feature/references/database-url-translation.md`. |

### Required env placeholders (`.env.example` in generated projects)

Auth: `AUTH_MODE`, `AUTH_ISSUER_URI`, `AUTH_JWKS_URI`, `AUTH_AUDIENCE`,
`AUTH_MOCK_USER`, `AUTH_MOCK_JWT_SECRET`, `CLERK_PUBLISHABLE_KEY`,
`CLERK_SECRET_KEY`, `CLERK_SIGN_IN_FORCE_REDIRECT_URL`,
`CLERK_SIGN_UP_FORCE_REDIRECT_URL`.

Usage logging: `USAGE_LOGGING_ENABLED`, `USAGE_LOG_SERVICE_NAME`,
`USAGE_LOG_ENVIRONMENT`.

External provider setup (documented in README only):
`GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` — configured in Clerk Dashboard.

### Replit deployment notes

- Java + persistent Postgres = **Reserved VM** (`deploymentTarget = "gce"`),
  not Autoscale. Autoscale scales to zero and cold-starts every request,
  which breaks the JDBC pool and JVM warmup.
- Workspace `[env]` does not propagate to Deployments. Before publishing,
  copy `SPRING_PROFILES_ACTIVE`, `CLERK_*`, `AUTH_*`, `USAGE_LOG_*` (and
  any `DATABASE_URL` override) to the deployment Secrets pane.
- For demos that only ship on Replit and don't need to be exported,
  [Replit Auth](https://docs.replit.com/references/auth-and-identity/authentication)
  is a zero-config alternative to Clerk. The template defaults to Clerk
  because generated apps must also run on local-dev.

### Prohibited

- Production secrets or production data in generated MVPs.
- Frontend reaching the DB or service-account keys.
- Committing service account JSON.
- Hardcoded auth secrets.
