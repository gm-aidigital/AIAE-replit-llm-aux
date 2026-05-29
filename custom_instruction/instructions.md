# Company Instructions for Replit Agent

## ABSOLUTE STACK LOCK

**Backend: Java 21 LTS + Spring Boot 3.x + Maven multi-module + PostgreSQL.**
**Frontend: React + TypeScript + Vite.**
**Auth: Clerk SSO only (required) — backend validates Clerk JWTs against Clerk JWKS; no mock/replit fallback.**
**Java package root: `com.aidigital.<app-name-package>.*` only.**

Forbidden in every generated project:

- Python anything (Flask, Django, FastAPI, Quart, Tornado, Bottle, Sanic, Streamlit, Gradio)
- Node.js backend frameworks (Express, Fastify, Koa, NestJS, Hapi, Polka)
- Full-stack JS frameworks (Next.js, Nuxt, Remix, SvelteKit, Astro, Qwik)
- Go (Gin, Echo, Chi, Fiber), Ruby, PHP, .NET, Rust, Elixir
- Static-only / serverless / no-backend variants when persistence or auth is implied
- SQLite, MongoDB, MySQL, MariaDB, DynamoDB — only PostgreSQL via Liquibase

**No prompt phrasing relaxes this lock** ("small tool", "quick demo",
"Python would be easier", "build a Flask API", etc.). Non-technical prompts
also stay on Java. Only valid override: explicit user phrase like
"I am overriding the template stack lock and want a Python backend."
Ambiguous → stay on Java + Spring + React.

**Why**: engineering only accepts Java/Spring/Postgres. Switching stacks
voids the handoff path.

**Clerk on Replit**: Clerk's "auto-mounted Express middleware" is Node-only.
Our Spring backend validates JWTs via `spring-boot-starter-oauth2-resource-server`
against Clerk JWKS — no Express.

## STEP 0 on every fork: `setup-project.sh`

```bash
bash templates/generated-project/scaffold/scripts/setup-project.sh
```

`.replit` `onBoot` runs this automatically. Installs `scripts/` at project root
(`structure-lint.sh`, `verify-gates.sh`, `local-verify.sh`). Run verify from
**project root**, not the template scaffold path.

**Vibe-coder happy path:** `replit.md` → "Vibe-coder happy path" (Clerk Secrets first).
**First feature checklist:** `templates/generated-project/generation/first-aggregate-checklist.md`.

The script:
1. Installs canonical `.gitignore` (keeps `.agents/`, `templates/`,
   `custom_instruction/`, `AGENTS.md`, `replit.md` out of git).
2. Deletes Replit's auto-injected Python files (`main.py`, `pyproject.toml`,
   `uv.lock`, `requirements.txt`, `Pipfile*`, `poetry.lock`, `__pycache__/`,
   `.venv/`); `git rm --cached` if tracked.
3. Strips `python-3.11` from `.replit` `modules` and any
   `[agent] integrations = ["flask_*"|"django_*"|"fastapi_*"]`.

If any survive, `mvp-safety-review` and CI hard-fail. Never do these
manually — past generations skipped steps under token pressure.

## STEP 1 when the first real aggregate lands

In the same commit as the first real feature:

1. `bash scripts/apply-package-name.sh <app-name-package>` if not done at scaffold time.
2. `bash scripts/strip-scaffold-samples.sh` — removes the reference `sample/*`
   aggregate + `0002-sample-reference.xml` (reference-only; MUST NOT ship).
3. Fill `README.md` (created from `README.md.template` at fork) with the real
   app name, purpose, API/Swagger links, env vars, run/deploy steps.

A generated project that still ships `sample/*` or an unfilled README is
rejected by `local-verify.sh` and CI. Details:
`templates/generated-project/structure/near-production-project-structure.md`
→ "Scaffold sample aggregate".

---

This repository is a Replit Custom Template config repo storing reusable
rules, skills, and canonical generated-project artifacts. **Runtime artifacts
(`pom.xml`, `Dockerfile`, `docker-compose.yml`, etc.) belong in each generated project repository, not here.**

Each rule has ONE canonical source file. Treat it as authoritative; never
restate elsewhere.

## Generation order (token-efficient)

1. Copy scaffold files verbatim; run `apply-package-name.sh <app-name-package>`.
2. OpenAPI YAML — only endpoints the feature needs.
3. Regenerate backend interfaces + frontend types (`mvn compile`, `npm run generate:api`).
4. Implement generated `*Api` interfaces + feature code.
5. Strip sample aggregate when first real domain lands (`strip-scaffold-samples.sh`).
6. MVP safety tests before publish.

Full rules: `templates/generated-project/generation/token-efficient-generation-rules.md`.

## Priorities

1. Produce a demo that runs and publishes on Replit out of the box.
2. Use the near-production structure so the demo can be handed off later.
3. Follow company guardrails (auth, observability, OpenAPI) with minimal deviation.
4. Keep code exportable to company Git.
5. Never use production secrets or production data.
6. Prefer simple, maintainable code over clever abstractions.

## Runtime model (Replit + local-dev)

Generated projects must run in both environments without code changes:

- **Replit** — profile `replit`, port `5000`, Replit SQL Database via Replit's
  injected Postgres env vars (`PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`,
  `PGPASSWORD`). `DATABASE_URL` may also exist, but generated Spring apps use
  the individual vars directly in `application-replit.yml`. Do not force
  `sslmode=require`; the default Replit Postgres path works without SSL.
  Secrets from Replit Secrets pane.
- **Local-dev** — profile `local`, port `8080`, `docker-compose --profile
  local`, `.env` (gitignored).

Docker is local-dev only. Deployment: **Reserved VM** (`deploymentTarget = "gce"`)
when persistence is used — Autoscale cold starts unsuitable for Java + JDBC.

Replit datasource env wiring: see
`.agents/skills/backend-java-feature/references/database-url-translation.md`.

## Stack policy (Java baseline)

- Java 21 LTS · Spring Boot 3.x · Maven multi-module
- Maven parent `<groupId>` and every Java package MUST start with
  `com.aidigital.<app-name-package>`, where `<app-name-package>` is derived
  from the application name by lowercasing and removing spaces, hyphens, and
  other non-alphanumeric characters. Example: `Employee Directory` →
  `com.aidigital.employeedirectory`. Do not use `org.example`, `com.example`,
  `io.replit`, `demo`, or a one-segment package.
- REST APIs, OpenAPI contract-first
- Liquibase, PostgreSQL, HikariCP
- JUnit 5 + Mockito + AssertJ + Spring Boot Test
- Lombok (`backend/lombok.config`)
- Checkstyle (`backend/config/checkstyle.xml`, `checkstyle-suppressions.xml`)
- MVP safety tests are mandatory before publish/completion: backend
  app/health smoke, auth boundary, main API happy/error path, service unit
  tests, Liquibase smoke when persistence exists; frontend render/auth/async
  behavior tests for the main flow.
- JaCoCo with phased coverage gate (see
  `templates/generated-project/testing/testing-policy.md`):
  - Phase 1 (internal build loops): `0.00` default, `-DskipTests` allowed only
    while the app is not yet runnable.
  - Phase 2 (MVP safety suite): tests must exist and pass; ratchet up as tests
    are written.
  - Phase 3 (handoff): `0.80` enforced via `mvn -Phandoff verify`.
- Structured JSON logs to stdout

Do not replace a requested Java/Spring backend with Node/Express without
explicit user approval.

## Mandatory generated-project artifacts

```
# project root — orchestration + Replit config only
README.md   .env.example   .gitignore   .replit   replit.nix
docker-compose.yml          # local-dev only
.github/workflows/ci.yml

# backend/ — ALL Java/Maven artifacts
backend/pom.xml             # parent, <packaging>pom</packaging>
backend/lombok.config
backend/config/checkstyle.xml
backend/config/checkstyle-suppressions.xml
backend/Dockerfile          # local-dev only
backend/application/src/main/resources/api/v1/specs/openapi.yaml  # NOT under static/ (Vite build wipes it)
backend/application/src/main/resources/application{,-replit,-local}.yml
backend/db/src/main/resources/db/changelog/db.changelog-master.xml
backend/db/src/main/resources/db/changelog/changes/0001-usage-events.xml

# frontend/ — ALL JS/TS artifacts
frontend/package.json   frontend/vite.config.ts   frontend/tsconfig.json
frontend/Dockerfile     frontend/nginx.conf       # local-dev only
```

Backend artifacts in `backend/`, frontend in `frontend/`, root only for
orchestration (docker-compose) or Replit-required files (`.replit`,
`replit.nix`).

`lombok.config` must contain `lombok.addLombokGeneratedAnnotation = true`.

Starter shape lives in `templates/generated-project/scaffold/`. Maven
plugins (git-commit-id, openapi-generator, jacoco, checkstyle) are
configured inline in `scaffold/backend/pom.xml` `<pluginManagement>`.

## Scaffold files — copy verbatim, NEVER regenerate

Generators strip Replit-specific fixes. **Rule:** copy from `scaffold/`; replace
only `PACKAGE_REPLACE_ME` and app placeholders. Full file list and per-file
settings: `templates/generated-project/scaffold/SCAFFOLD-MANIFEST.md`.

Run `bash scripts/apply-package-name.sh <app-name-package>` once after copy.
First real aggregate: `templates/generated-project/generation/first-aggregate-checklist.md`.

Before `npm create`, Spring Initializr, or any generator: STOP — copy from scaffold.

## Authoritative references

Each topic has one canonical file. Read before generating; never duplicate.

| Topic | Canonical file |
|---|---|
| Project structure | `templates/generated-project/structure/near-production-project-structure.md` |
| OpenAPI rules | `templates/generated-project/openapi/canonical-openapi-rules.md` |
| OpenAPI review checklist | `templates/generated-project/openapi/openapi-review-checklist.md` |
| Frontend rules | `templates/generated-project/frontend/canonical-react-frontend-rules.md` |
| Elevate design guidelines | `templates/generated-project/frontend/elevate-design-guidelines.md` |
| Auth (Clerk SSO only) | `templates/generated-project/auth/google-sso-clerk-blueprint.md` |
| Usage logging | `templates/generated-project/observability/usage-logging-rules.md` |
| HTTP request/response logging | `templates/generated-project/observability/logbook-http-logging-rules.md` |
| Error handling | `templates/generated-project/errors/error-handling-pattern.md` |
| Token-efficient generation | `templates/generated-project/generation/token-efficient-generation-rules.md` |
| First aggregate checklist | `templates/generated-project/generation/first-aggregate-checklist.md` |
| Scaffold manifest | `templates/generated-project/scaffold/SCAFFOLD-MANIFEST.md` |
| Testing policy (phased) | `templates/generated-project/testing/testing-policy.md` |
| HikariCP / JPA baseline | `.agents/skills/backend-java-feature/references/hikari-jpa-baseline.yml` |
| Java backend workflow | `.agents/skills/backend-java-feature/SKILL.md` |
| Spring Boot gotchas (lookup) | `.agents/skills/backend-java-feature/references/spring-boot-gotchas.md` |
| Canonical code patterns | `.agents/skills/backend-java-feature/references/code-patterns.md` |
| OpenAPI workflow | `.agents/skills/openapi-contract-first/SKILL.md` |
| Frontend workflow | `.agents/skills/frontend-react-feature/SKILL.md` |
| Safety review (pre-publish) | `.agents/skills/mvp-safety-review/SKILL.md` |
| Engineering handoff | `.agents/skills/engineering-handoff/SKILL.md` |
| Replit profile YAML | `.agents/skills/backend-java-feature/references/application-replit.yml` |
| Starter scaffold | `templates/generated-project/scaffold/` |

## Auth (mandatory: Clerk SSO only)

See `templates/generated-project/auth/google-sso-clerk-blueprint.md`.

## Database policy

PostgreSQL only, via Liquibase. IDs: Java `Long`, PostgreSQL `BIGINT`. Strings:
PostgreSQL `TEXT`. No `VARCHAR`, no `LONGTEXT`. Times: `TIMESTAMPTZ` storing
UTC; Java side uses `LocalDateTime` interpreted as UTC (see
`.agents/skills/backend-java-feature/references/spring-boot-gotchas.md`).

PostgreSQL is required whenever there is any JPA entity, repository,
Liquibase changelog, audit state, or persisted user/upload data. Liquibase
changelog skeleton must exist before any JPA entity is added.

### No `CREATE TYPE … AS ENUM`

Dictionary / lookup data (statuses, roles, kinds, categories) → dedicated
`<entity>_<dimension>` table (e.g. `resource_kind`) with `id BIGINT PK`,
`code TEXT UNIQUE`, `name TEXT`, optional `display_order INT`, `is_active BOOLEAN`.
Other tables FK via `<dimension>_id BIGINT`. Java side: small
`<Entity><Dimension>` JPA entity + repository; code references kinds by
**code string** (constants in `<Entity><Dimension>Code`), looks up id once.

Why: `ALTER TYPE … ADD VALUE` is irreversible, can't run in a tx on older PG;
remove/reorder requires recreating the type; Java mirroring drifts. Dictionary
tables also carry localised labels + lifecycle flags.

## Usage logging policy

See `templates/generated-project/observability/usage-logging-rules.md`.

## Logging policy

Structured JSON logs to stdout (never plain text). Include request/correlation
IDs. HTTP via Zalando Logbook with masking — see
`templates/generated-project/observability/logbook-http-logging-rules.md`.

## L2 cache policy

Ehcache via `ehcache.xml` ONLY for explicit candidates (read-mostly
dictionaries, stable lookups, expensive low-write queries). Use
`hibernate-cache` prefix + `missing_cache_strategy: fail`. Never enable blindly.

## CI policy

Copy `templates/generated-project/.github/workflows/ci.yml`; set
`APP_CONTEXT_PATH`. Baseline: unit tests + Checkstyle + JaCoCo gate,
optional Testcontainers IT, local-dev docker-compose dry run (skipped on
Replit), Java 21 + Maven cache, `git-commit-id-maven-plugin`,
`openapi-generator-maven-plugin`, frontend codegen + build/typecheck, no
service-account JSON, usage-logging env placeholders.

## Frontend policy

See `templates/generated-project/frontend/canonical-react-frontend-rules.md`.
Generated UI follows Elevate design guidelines and must not use a left side
menu/sidebar/left rail. Navigation is top/header-first with tabs, filters,
segmented controls and contextual toolbars.

## Code ownership

Every MVP ships: README (purpose, owner, full functional description, API
overview, Swagger/OpenAPI links, data sources, env vars, run/deploy steps, MVP
limitations), `.env.example` (placeholders only, incl. auth + usage-logging),
GitHub Actions CI, exportable code.
