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

**No prompt phrasing relaxes this lock**, including but not limited to:
- "It's a small tool" / "Just a quick demo" / "Sounds simple"
- "Looks like a Flask app" / "Python would be easier" / "Node is faster"
- "We need it embeddable" / "Just a static page"
- A prompt that names a forbidden stack ("build a Flask API")
- A prompt that's entirely non-technical (the stack is the template's job, not the user's)

The only valid override is an explicit user phrase in the current message like:
> "I am overriding the template stack lock and want a Python backend."

Anything ambiguous → stay on Java + Spring + React.

### Why this lock exists

The template is the company's hand-off shape. Every demo built from it has to
move to engineering ownership. Engineering only accepts Java/Spring/Postgres.
Switching stacks inside the demo voids the handoff path, which voids the
entire reason this template was created. There is no point shipping a
"working Flask demo" — engineering will throw it away.

### Common misread: Replit-managed Clerk Auth

Replit's managed Clerk docs say "auto-mounts Express middleware". That
auto-wiring is for Node apps; we are NOT a Node app. Our Spring backend
validates JWTs via `spring-boot-starter-oauth2-resource-server` against
Clerk's JWKS endpoint — same identity, no Express anywhere. Reading the
Clerk docs is fine; following the Express path is the wrong inference.

### STEP 0 — first action on every fork: `setup-project.sh`

Before writing any application code, Agent runs the canonical setup script
**once**:

```bash
bash templates/generated-project/scaffold/scripts/setup-project.sh
```

This script (idempotent, safe to re-run):

1. Installs the canonical `.gitignore` at project root (overwrites the
   template's gitignore). This is **the** mechanism that keeps the
   template control plane (`.agents/`, `templates/`, `custom_instruction/`,
   `AGENTS.md`, `replit.md`) **out** of git pushes — those files stay in
   the Replit workspace because Agent reads them every conversation, but
   they never reach the company's git repo.
2. Deletes Replit's auto-injected Python files (`main.py`, `pyproject.toml`,
   `uv.lock`, `requirements.txt`, `Pipfile*`, `poetry.lock`, `__pycache__/`,
   `.venv/`).
3. `git rm --cached` any of the above if git already grabbed them.
4. Strips `python-3.11` from `.replit` `modules`.
5. Strips `[agent] integrations = ["flask_*" | "django_*" | "fastapi_*"]`
   from `.replit`.

If any of those survive into the generated project, the safety review
(`mvp-safety-review/SKILL.md`) and the generated-project CI hard-fail.

#### Why a script and not "Agent does it manually"

Past generation attempts (see `templates/generated-project/scaffold/scripts/setup-project.sh`'s
companion notes in commits) showed Agent skipped one or two cleanup steps
under token pressure. The script removes the choice — one command, all
five steps, can't be partially done.

---

You build MVP / internal-demo applications for non-technical product users.

This repository is a Replit Custom Template configuration repository. It
stores reusable rules, skills, and canonical generated-project artifacts.
**Runtime artifacts (`pom.xml`, `Dockerfile`, `docker-compose.yml`, etc.)
belong in each generated project repository, not here.**

Each rule below has a single canonical source file. Treat the canonical file
as authoritative; never restate its content in another file.

## Priorities

1. Produce a demo that runs and publishes on Replit out of the box.
2. Use the near-production structure so the demo can be handed off later.
3. Follow company guardrails (auth, observability, OpenAPI) with minimal deviation.
4. Keep code exportable to company Git.
5. Never use production secrets or production data.
6. Prefer simple, maintainable code over clever abstractions.

## Runtime model (Replit + local-dev)

Generated projects must run in two environments without code changes:

- **Replit workspace** — Spring profile `replit`, port `5000`, Replit SQL
  Database via `DATABASE_URL` (the only env var the managed DB injects;
  PG* are legacy Neon-only and not provided). Workspace `[env]` in `.replit`
  sets `SPRING_PROFILES_ACTIVE=replit`. Secrets come from the Replit Secrets pane.
- **Local-dev machine** — Spring profile `local`, port `8080`,
  `docker-compose --profile local` for Postgres, `.env` (gitignored).

Docker and `docker-compose.yml` are generated **only for the local-dev path**.
They are not invoked on Replit.

Deployment: when persistence is used, the deployment target is **Reserved VM**
(`deploymentTarget = "gce"` in `.replit`). Autoscale is unsuitable for Java +
JDBC + JVM warmup.

`DATABASE_URL` → JDBC translation, SSL handling and pool sizing:
`.agents/skills/backend-java-feature/references/database-url-translation.md`.

Canonical structure: `templates/generated-project/structure/near-production-project-structure.md`.

## Stack policy

For fast Replit MVPs choose the simplest stack that runs and publishes on
Replit. If the user requests a Java backend, or engineering handoff requires
one, use the Java baseline:

- Java 21 LTS · Spring Boot 3.x · Maven multi-module
  (Java 25 LTS is the long-term target; switch when Replit's pinned
  nixpkgs channel adds `pkgs.jdk25`)
- REST APIs, OpenAPI contract-first
- Liquibase, PostgreSQL, HikariCP
- JUnit 5 + Mockito + AssertJ + Spring Boot Test
- Lombok (root `lombok.config`)
- Checkstyle (root `config/checkstyle.xml`, `config/checkstyle-suppressions.xml`)
- JaCoCo with phased coverage gate:
  - Phase 1 (initial build) / Phase 2 (post-working MVP): `0.00` default —
    Agent writes tests AFTER the app runs end-to-end, then ratchets the
    gate to whatever the suite delivers.
  - Phase 3 (handoff): `0.80` enforced via `mvn -Phandoff verify`.
  - Full policy: `templates/generated-project/testing/testing-policy.md`
- Structured JSON logs to stdout

Do not replace a requested Java/Spring backend with Node.js/Express without
explicit user approval.

## Mandatory generated-project artifacts

For every generated full-stack or backend MVP, the project repository must contain:

```
# project root — project-level concerns only
README.md                .env.example                .gitignore
.replit                  replit.nix
docker-compose.yml       # local-dev only; orchestrates everything
.github/workflows/ci.yml

# backend/ — ALL Java/Maven artifacts (parent pom, configs, modules, Dockerfile)
backend/pom.xml                                # parent, <packaging>pom</packaging>
backend/lombok.config
backend/config/checkstyle.xml
backend/config/checkstyle-suppressions.xml
backend/Dockerfile                             # backend image (local-dev only)
backend/application/src/main/resources/static/api/v1/specs/openapi.yaml
backend/application/src/main/resources/application.yml
backend/application/src/main/resources/application-replit.yml
backend/application/src/main/resources/application-local.yml
backend/db/src/main/resources/db/changelog/db.changelog-master.xml
backend/db/src/main/resources/db/changelog/changes/0001-usage-events.xml

# frontend/ — ALL JS/TS artifacts
frontend/package.json    frontend/vite.config.ts    frontend/tsconfig.json
frontend/Dockerfile      frontend/nginx.conf        # local-dev only
```

**Layout principle**: everything that serves the backend lives in
`backend/`; everything that serves the frontend lives in `frontend/`.
Root only holds files that orchestrate both (docker-compose) or that
Replit requires at root (.replit, replit.nix).

`lombok.config` must contain:
```properties
lombok.addLombokGeneratedAnnotation = true
```

Canonical files to copy from this template (all live under
`templates/generated-project/`):
- Checkstyle:
  `scaffold/backend/config/checkstyle.xml`,
  `scaffold/backend/config/checkstyle-suppressions.xml`
  → copy into generated `backend/config/`.
- Lombok:
  `scaffold/backend/lombok.config` → copy into generated `backend/`.
- CI: `.github/workflows/ci.yml` → copy into generated `.github/workflows/`.
- Maven plugin snippets: `pom-snippets/*.xml` (referenced from parent pom).
- Dockerfiles: `scaffold/backend/Dockerfile`, `scaffold/frontend/Dockerfile`,
  `scaffold/frontend/nginx.conf`, `scaffold/docker-compose.yml`.
- **Starter files (most-recently-canonical shape of each mandatory file):**
  `templates/generated-project/scaffold/` — parent `pom.xml`, `Application.java`,
  `ReplitDatabaseUrlPostProcessor.java`, `application*.yml`, `logback-spring.xml`,
  `lombok.config`, Liquibase master + `usage_events` changelog,
  `.env.example`, `Dockerfile`, `docker-compose.yml`, `frontend/`
  (package.json, vite.config.ts, tsconfig.json, runtime.ts, client.ts),
  `README.md.template`. Replace `PACKAGE_REPLACE_ME` and
  `/some-path-by-app-name` placeholders.

## Authoritative references

Each topic has one canonical file. Read it before generating; do not duplicate.

| Topic | Canonical file |
|---|---|
| Project structure | `templates/generated-project/structure/near-production-project-structure.md` |
| OpenAPI rules | `templates/generated-project/openapi/canonical-openapi-rules.md` |
| OpenAPI review checklist | `templates/generated-project/openapi/openapi-review-checklist.md` |
| Frontend rules | `templates/generated-project/frontend/canonical-react-frontend-rules.md` |
| Auth (dual-mode) | `templates/generated-project/auth/google-sso-clerk-blueprint.md` |
| Usage logging | `templates/generated-project/observability/usage-logging-rules.md` |
| HTTP request/response logging | `templates/generated-project/observability/logbook-http-logging-rules.md` |
| Error handling (AppException + GlobalExceptionHandler) | `templates/generated-project/errors/error-handling-pattern.md` |
| Token-efficient generation | `templates/generated-project/generation/token-efficient-generation-rules.md` |
| Testing policy (phased) | `templates/generated-project/testing/testing-policy.md` |
| HikariCP / JPA runtime baseline | `.agents/skills/backend-java-feature/references/hikari-jpa-baseline.yml` |
| Java backend workflow | `.agents/skills/backend-java-feature/SKILL.md` |
| OpenAPI workflow | `.agents/skills/openapi-contract-first/SKILL.md` |
| Frontend workflow | `.agents/skills/frontend-react-feature/SKILL.md` |
| Safety review (pre-publish) | `.agents/skills/mvp-safety-review/SKILL.md` |
| Engineering handoff | `.agents/skills/engineering-handoff/SKILL.md` |
| Replit DataSource Java config | `.agents/skills/backend-java-feature/references/ReplitDatabaseUrlPostProcessor.java` |
| Replit profile YAML snippet | `.agents/skills/backend-java-feature/references/application-replit.yml` |
| Starter scaffold (copy into generated app) | `templates/generated-project/scaffold/` |

## Auth (mandatory dual-mode)

Generated projects must support `AUTH_MODE=auto|sso|mock` and run in both
modes without code changes.

- `auto` (default): use Clerk SSO when its keys exist; otherwise mock.
- `sso`: fail fast if Clerk keys are missing.
- `mock`: skip external IdP, expose local mock login.

Backend validates Bearer JWT (signature via IdP JWKS; `iss`, `aud`, `exp`,
`nbf` when present). `401` for missing/invalid token; `403` for unauthorized.
Frontend never proves auth on its own state; backend is source of truth.

Full contract and env placeholders:
`templates/generated-project/auth/google-sso-clerk-blueprint.md`.

## Database policy

PostgreSQL only. IDs: Java `Long`, PostgreSQL `BIGINT`. Strings: PostgreSQL
`TEXT`. No `VARCHAR`, no MySQL `LONGTEXT`. DDL through Liquibase.

### No PostgreSQL `CREATE TYPE … AS ENUM`

Dictionary / lookup data (statuses, roles, departments, kinds, categories,
etc.) **must** live in a dedicated table with `BIGINT id` primary key and
`TEXT code`/`TEXT name` columns. Other tables reference the dictionary by
`*_id BIGINT` foreign key.

Reasons we forbid Postgres enums:
- Adding a new value requires `ALTER TYPE … ADD VALUE` which is fragile
  inside Liquibase changesets (cannot run in a transaction in older PG;
  irreversible).
- Removing or reordering values requires recreating the type — every
  dependent column has to be migrated.
- Java side either hardcodes Java enum (mirror drift) or uses `String` with
  no DB-level constraint.
- Dictionary tables let you carry localised labels, `display_order`,
  `is_active`, `valid_from`/`valid_to`, etc. — features that Postgres enums
  cannot express.

Canonical shape (using a generic `<entity>_<dimension>` naming — replace
`resource` / `kind` with the actual domain term):

```xml
<changeSet id="0010-resource-kind-dict" author="agent">
  <createTable tableName="resource_kind">
    <column name="id"   type="BIGINT" autoIncrement="true">
      <constraints primaryKey="true" nullable="false"/>
    </column>
    <column name="code" type="TEXT">
      <constraints nullable="false" unique="true"/>
    </column>
    <column name="name" type="TEXT"><constraints nullable="false"/></column>
    <column name="display_order" type="INT" defaultValueNumeric="0"/>
    <column name="is_active" type="BOOLEAN" defaultValueBoolean="true"/>
  </createTable>
  <insert tableName="resource_kind"><column name="code" value="KIND_A"/><column name="name" value="Kind A"/></insert>
  <insert tableName="resource_kind"><column name="code" value="KIND_B"/><column name="name" value="Kind B"/></insert>
</changeSet>

<changeSet id="0011-resource" author="agent">
  <createTable tableName="resource">
    <column name="id" type="BIGINT" autoIncrement="true">
      <constraints primaryKey="true" nullable="false"/>
    </column>
    <!-- ... -->
    <column name="kind_id" type="BIGINT">
      <constraints nullable="false" foreignKeyName="fk_resource_kind"
                   references="resource_kind(id)"/>
    </column>
  </createTable>
</changeSet>
```

Java side: a small `ResourceKind` JPA entity
(`@Entity @Table(name = "resource_kind")`) + `ResourceKindRepository`. Java
code references kinds by **code string** (constants in `ResourceKindCode`)
and looks up the id once at startup or per call. The DB column is
`kind_id BIGINT FK` — never `kind TEXT`, never `kind resource_kind_enum`.

PostgreSQL is required whenever the project has any JPA entity, repository,
Liquibase changelog, audit state, or persisted user/upload data. When required:
- **Replit run** uses Replit's SQL Database via the `postgresql-16` module
  declared in `.replit`. Replit injects one env var, `DATABASE_URL`
  (libpq format, `sslmode=require`). The Java backend converts it to JDBC
  at startup and sets HikariCP `maximum-pool-size: 2–3` on the `replit`
  profile. See `.agents/skills/backend-java-feature/references/database-url-translation.md`
  and `application-replit.yml`.
- **Local-dev run** adds a PostgreSQL service to `docker-compose.yml` under
  profile `local`, with named volume, healthcheck, and backend `depends_on`.

Liquibase changelog skeleton must exist before any JPA entity is added.

## Usage logging policy

One event per meaningful user action → the app's own PostgreSQL `usage_events`
table. Manager runs SQL for estimation. `PostgresUsageLogger` is bound by
default; `NoOpUsageLogger` takes over when `USAGE_LOGGING_ENABLED=false` or
required settings are missing.

Full contract, schema, and Liquibase changelog:
`templates/generated-project/observability/usage-logging-rules.md`.

Never:
- log health, actuator, static, OPTIONS, prefetch, probe traffic,
- log secrets, JWTs, raw request bodies, raw documents, third-party PII.

## Logging policy

Backend emits structured JSON logs to stdout. Plain text is not acceptable
unless explicitly approved. Include request/correlation IDs where available.

Inbound and outbound HTTP traffic is logged via Zalando Logbook with masking
applied. Full contract:
`templates/generated-project/observability/logbook-http-logging-rules.md`.

## L2 cache policy

Ehcache via `ehcache.xml` only for explicit candidates (read-mostly
dictionaries, stable lookup tables, expensive low-write queries). Define
regions explicitly. Use `hibernate-cache` prefix and
`missing_cache_strategy: fail`. Never enable cache blindly.

## CI policy (generated Java backend)

Copy `templates/generated-project/.github/workflows/ci.yml` into each
generated project and set `APP_CONTEXT_PATH`. Baseline checks:
- unit tests + Checkstyle + JaCoCo 0.80 gate
- optional integration tests with PostgreSQL
- local-dev docker-compose dry run (skipped on Replit)
- Java 21 setup + Maven cache
- `git-commit-id-maven-plugin` configured
- `openapi-generator-maven-plugin` configured with canonical options when
  REST API exists
- frontend OpenAPI codegen + build/typecheck when frontend exists
- service account JSON not committed
- usage logging env placeholders present in `.env.example`

## Frontend policy

React + TypeScript + Vite. Pages stay thin; API calls and business logic
live in feature hooks/services. Always handle loading / empty / error /
success. Accessible UI. Frontend never accesses the DB or secrets directly.
Use the typed `openapi-typescript` + `openapi-fetch` client and TanStack Query.

Full rules: `templates/generated-project/frontend/canonical-react-frontend-rules.md`.

## Code ownership

Every generated MVP includes:
- README with purpose, owner, data sources, env vars, run/deploy steps,
  MVP limitations.
- `.env.example` with placeholders only.
- Clear folder structure.
- Git-friendly code exportable to company repositories.
- GitHub Actions CI in `.github/workflows/ci.yml`.

`.env.example` always carries the auth placeholders and the three
usage-logging vars; see the canonical auth and usage-logging files for the
exact list.
