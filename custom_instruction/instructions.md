# Company Instructions for Replit Agent

## ABSOLUTE STACK LOCK

**Backend: Java 21 LTS + Spring Boot 3.x + Maven multi-module + PostgreSQL.**
**Frontend: React + TypeScript + Vite.**
**Auth: dual-mode (Clerk SSO + mock fallback) with backend JWT validation.**

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

`.replit` `onBoot` runs this automatically; idempotent. The script:
1. Installs canonical `.gitignore` (keeps `.agents/`, `templates/`,
   `custom_instruction/`, `AGENTS.md`, `replit.md` out of git).
2. Deletes Replit's auto-injected Python files (`main.py`, `pyproject.toml`,
   `uv.lock`, `requirements.txt`, `Pipfile*`, `poetry.lock`, `__pycache__/`,
   `.venv/`); `git rm --cached` if tracked.
3. Strips `python-3.11` from `.replit` `modules` and any
   `[agent] integrations = ["flask_*"|"django_*"|"fastapi_*"]`.

If any survive, `mvp-safety-review` and CI hard-fail. Never do these
manually — past generations skipped steps under token pressure.

---

This repository is a Replit Custom Template config repo storing reusable
rules, skills, and canonical generated-project artifacts. **Runtime artifacts
(`pom.xml`, `Dockerfile`, `docker-compose.yml`, etc.) belong in each generated project repository, not here.**

Each rule has ONE canonical source file. Treat it as authoritative; never
restate elsewhere.

## Priorities

1. Produce a demo that runs and publishes on Replit out of the box.
2. Use the near-production structure so the demo can be handed off later.
3. Follow company guardrails (auth, observability, OpenAPI) with minimal deviation.
4. Keep code exportable to company Git.
5. Never use production secrets or production data.
6. Prefer simple, maintainable code over clever abstractions.

## Runtime model (Replit + local-dev)

Generated projects must run in both environments without code changes:

- **Replit** — profile `replit`, port `5000`, Replit SQL Database via
  `DATABASE_URL` (libpq URL; `PG*` legacy vars NOT provided by
  `postgresql-16`). `sslmode` is NOT forced — `ReplitDatabaseUrlPostProcessor`
  respects whatever the URL carries (Replit's production-grade tier sets
  `sslmode=require`; Helium dev tier omits it). Secrets from Replit Secrets pane.
- **Local-dev** — profile `local`, port `8080`, `docker-compose --profile
  local`, `.env` (gitignored).

Docker is local-dev only. Deployment: **Reserved VM** (`deploymentTarget = "gce"`)
when persistence is used — Autoscale cold starts unsuitable for Java + JDBC.

`DATABASE_URL` → JDBC: see
`.agents/skills/backend-java-feature/references/database-url-translation.md`.

## Stack policy (Java baseline)

- Java 21 LTS · Spring Boot 3.x · Maven multi-module
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

Generators (`npm create vite`, `spring initializr`, etc.) strip Replit-specific
fixes and force re-deriving every fix from failure logs ("db not in fat jar",
"lower(bytea)", "Blocked request not allowed", "spring-boot:run on parent pom",
"allowedHosts: 'all'").

**Rule:** files in the table below — `cp` from `scaffold/`, replace ONLY
documented placeholders (`PACKAGE_REPLACE_ME`, `/some-path-by-app-name`,
project name). Never overwrite from a generator.

Before `npm create`, `spring initializr`, `mvn archetype`, `npx create-*`:
STOP, check the table, copy from scaffold. New requirements get additive
`Edit`s — preserve every existing block.

**Each row carries Replit-specific fixes that generators strip.**

| File | Replit-specific settings that disappear on regenerate |
|---|---|
| `.replit` (root) | Backend workflow MUST be `mvn -f backend/pom.xml -DskipTests -Dskip.frontend=true install && mvn -f backend/application/pom.xml -DskipTests -Dskip.frontend=true spring-boot:run` — never `spring-boot:run` on the parent reactor; frontend workflow MUST run `npm run generate:api` before Vite; `[deployment]` GCE Reserved-VM with `scripts/replit-build.sh` + `scripts/replit-run.sh`; one public port `5000` → externalPort `80`; Vite `5173` is workspace-only, not a `[[ports]]` entry; `onBoot` runs `setup-project.sh`; workflow has FATAL guard against Python files |
| `replit.nix` (root) | Pinned `pkgs.jdk21` + `pkgs.nodejs_22` + `pkgs.postgresql_16`; channel `stable-24_11` |
| `docker-compose.yml` (root) | `--profile local` gating, port `8080` for backend (local), standard `POSTGRES_*` env wiring to the compose Postgres service, health-check depends_on |
| `.env.example` (root) | Full enumerated set of `AUTH_*`, `USAGE_LOG_*`, `CLERK_*`, `BACKEND_DEV_PORT`, `VITE_API_BASE_URL`, `VITE_API_CONTEXT_PATH` placeholders |
| `.gitignore` (root) | Control-plane excludes (`.agents/`, `templates/`, `custom_instruction/`, `AGENTS.md`, `replit.md`) — without these the company repo gets polluted on `git push` |
| `backend/pom.xml` | `<dependencyManagement>` for all internal modules (`domain`, `db`, `external-services`, `service`); `<pluginManagement>` for spring-boot-maven-plugin, openapi-generator, frontend-maven-plugin, jacoco, checkstyle, git-commit-id; Java 21 + Spring Boot 3.4.0 pinning; lombok 1.18.40 (forward-safe with newer JDKs) |
| `backend/application/pom.xml` | `<start-class>${project.groupId}.Application`; the REQUIRED `db` dependency (changelogs on fat-jar classpath); spring-boot-maven-plugin activation; openapi-generator activation; PostgreSQL runtime driver; jjwt + logbook + commons-csv + observability deps |
| `backend/service/pom.xml` | Edges to `domain` + `external-services` only — never to `application`; jakarta-validation for `ValidationMessage`; FORBIDDEN: web/security/servlet/JWT deps |
| `backend/domain/pom.xml` | `spring-boot-starter-data-jpa` + lombok; LEAF (no internal deps) |
| `backend/external-services/pom.xml` | LEAF for internal modules; HTTP-client deps live here |
| `backend/db/pom.xml` | `liquibase-core` only — no Java code |
| `backend/Dockerfile` | Multi-stage Java 21 build, `mvn -B -DskipTests package`, exec form `java -jar /app/app.jar`, port `8080` (local-dev image) |
| `backend/lombok.config` | `lombok.addLombokGeneratedAnnotation = true` (required for jacoco to exclude generated code) |
| `backend/config/checkstyle.xml` and `checkstyle-suppressions.xml` | Project-tuned ruleset; the parent POM references both files by relative path |
| `frontend/vite.config.ts` | `allowedHosts: ['.replit.dev', '.repl.co', '.kirk.replit.dev', 'localhost', '127.0.0.1']`, `resolve.alias` for `@/*`, `host: '0.0.0.0'`, port `5173` (NEVER `5000`), `/api` proxy to `localhost:${BACKEND_DEV_PORT:-5000}${VITE_API_CONTEXT_PATH}`, build `outDir: ../backend/application/src/main/resources/static`, identical `preview` block. FORBIDDEN: `allowedHosts: 'all'`, `allowedHosts: '*'` |
| `frontend/package.json` | scripts `generate:api` + `check:api` + `test`; React + Vite + TanStack Query + Clerk + Vitest/Testing Library version pins |
| `frontend/tsconfig.json` | Strict mode + path alias `@/*` → `./src/*` |
| `frontend/src/shared/api/client.ts` | Single backend HTTP boundary using `openapi-fetch`; raw `fetch`, `axios`, and `XMLHttpRequest` are forbidden in `frontend/src` so path/method drift is caught by generated OpenAPI types |
| `frontend/Dockerfile` | nginx-based multi-stage (build → nginx serve), correct `nginx.conf` location |
| `frontend/nginx.conf` | SPA fallback (`try_files $uri /index.html`), `/api` proxy to backend service in local-dev compose, gzip + cache headers |
| `scripts/setup-project.sh` | Runs on `onBoot`; installs the canonical `.gitignore`, removes Replit's Python auto-injection, untracks the control plane from git index, strips `python-*` from `.replit` `modules` |

**If you must regenerate** (major Vite/Spring upgrade): diff against scaffold
MUST preserve every right-column setting. Scaffold is the floor, not ceiling.

## Authoritative references

Each topic has one canonical file. Read before generating; never duplicate.

| Topic | Canonical file |
|---|---|
| Project structure | `templates/generated-project/structure/near-production-project-structure.md` |
| OpenAPI rules | `templates/generated-project/openapi/canonical-openapi-rules.md` |
| OpenAPI review checklist | `templates/generated-project/openapi/openapi-review-checklist.md` |
| Frontend rules | `templates/generated-project/frontend/canonical-react-frontend-rules.md` |
| Auth (dual-mode) | `templates/generated-project/auth/google-sso-clerk-blueprint.md` |
| Usage logging | `templates/generated-project/observability/usage-logging-rules.md` |
| HTTP request/response logging | `templates/generated-project/observability/logbook-http-logging-rules.md` |
| Error handling | `templates/generated-project/errors/error-handling-pattern.md` |
| Token-efficient generation | `templates/generated-project/generation/token-efficient-generation-rules.md` |
| Testing policy (phased) | `templates/generated-project/testing/testing-policy.md` |
| HikariCP / JPA baseline | `.agents/skills/backend-java-feature/references/hikari-jpa-baseline.yml` |
| Java backend workflow | `.agents/skills/backend-java-feature/SKILL.md` |
| Spring Boot gotchas (lookup) | `.agents/skills/backend-java-feature/references/spring-boot-gotchas.md` |
| Canonical code patterns | `.agents/skills/backend-java-feature/references/code-patterns.md` |
| OpenAPI workflow | `.agents/skills/openapi-contract-first/SKILL.md` |
| Frontend workflow | `.agents/skills/frontend-react-feature/SKILL.md` |
| Safety review (pre-publish) | `.agents/skills/mvp-safety-review/SKILL.md` |
| Engineering handoff | `.agents/skills/engineering-handoff/SKILL.md` |
| Replit DataSource post-processor | `.agents/skills/backend-java-feature/references/ReplitDatabaseUrlPostProcessor.java` |
| Replit profile YAML | `.agents/skills/backend-java-feature/references/application-replit.yml` |
| Starter scaffold | `templates/generated-project/scaffold/` |

## Auth (mandatory dual-mode)

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

## Code ownership

Every MVP ships: README (purpose, owner, data sources, env vars, run/deploy
steps, MVP limitations), `.env.example` (placeholders only, incl. auth +
usage-logging), GitHub Actions CI, exportable code.
