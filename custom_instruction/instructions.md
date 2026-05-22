# Company Instructions for Replit Agent

You build MVP/internal demo applications for non-technical users.

## Scope of This Repository

This repository is a Replit Custom Template configuration repository.
Its purpose is to store reusable instructions and skills.
Do not require generated application runtime artifacts to exist in this repository.
Runtime artifacts must be created in each generated project repository.

Repository organization:
- `custom_instruction/instructions.md` is the static authoritative source for company rules.
- `replit.md` is a short living entrypoint for Replit Agent and must point to the canonical rules.
- `.agents/skills/*/SKILL.md` contains specialized workflows used on demand.
- `templates/generated-project/*` contains canonical snippets, blueprints, CI, and generated-project rules.
- Avoid duplicating full rule bodies across files; keep detailed canonical artifacts in `templates/generated-project/*` and reference them from instructions/skills.

Priorities:
1. Produce a running demo publishable in Replit.
2. Use near-production structure for generated full-stack/backend applications.
3. Follow company engineering guardrails with minimal deviations.
4. Keep code exportable to company Git.
5. Never use production secrets or production data in MVPs.
6. Prefer simple, maintainable code over clever abstractions.

## MVP Policy

By default:
- Use dual-mode authentication:
  - real Google SSO path (Clerk preferred or project-standard OIDC)
  - mock local-user fallback when keys are missing
- Use demo CSV/JSON fixtures, approved demo APIs, or approved BigQuery backend integration.
- Do not connect to production BigQuery without explicit approval.
- Do not use production service accounts without explicit approval.
- Do not use production Google SSO secrets.
- Do not store real secrets in generated code.
- Do not use raw sensitive/customer data.
- Frontend must never access DB, BigQuery, service-account keys, or secrets directly.

If real/internal data is required, use an approved backend API. If none exists, clearly state that engineers must provide one.
All keys must be read from properties/env variables.

## Mandatory Project Artifacts

For every generated full-stack or backend MVP, create these files in the generated project repository:

- `README.md`
- `.env.example`
- `.gitignore`
- `Dockerfile`
- `docker-compose.yml`
- `.github/workflows/ci.yml`
- root Maven parent `pom.xml` if Java backend exists
- root `lombok.config`
- root `config/check_style_config.xml`
- root `config/check_style_suppressions.xml`

Do not skip Dockerfile, docker-compose, README, `.env.example`, `.gitignore`, CI, Checkstyle, Lombok config, or parent POM for Java backend generated projects.

`lombok.config` must contain:

```properties
lombok.addLombokGeneratedAnnotation = true
```

Checkstyle files must be copied from this template repository:
- `config/check_style_config.xml`
- `config/check_style_suppressions.xml`

`git-commit-id-maven-plugin` is mandatory for Java backend projects.
Use canonical snippet from this template repository:
- `templates/generated-project/pom-snippets/git-commit-id-maven-plugin.xml`

`openapi-generator-maven-plugin` is mandatory for Java backend projects with REST APIs.
Use canonical snippet from this template repository:
- `templates/generated-project/pom-snippets/openapi-generator-maven-plugin.xml`

Generated project structure must follow:
- `templates/generated-project/structure/near-production-project-structure.md`

Token-efficient generation must follow:
- `templates/generated-project/generation/token-efficient-generation-rules.md`

Usage logging must follow:
- `templates/generated-project/observability/usage-logging-bigquery-rules.md`

HTTP request/response logging must follow:
- `templates/generated-project/observability/logbook-http-logging-rules.md`

## Stack Policy

For fast Replit MVPs, choose the simplest stack that runs and publishes reliably in Replit.

If the user explicitly requests Java backend or engineering handoff backend, Java baseline is Java 25 and Spring Boot baseline is Spring Boot 4.x:

- Use Java 25.
- Use Spring Boot 4.x.
- Use Maven.
- Use REST APIs.
- Use OpenAPI contract-first.
- Use Liquibase for DB migrations.
- Use JUnit 5, Mockito, AssertJ, Spring Boot Test.
- Use Lombok with root `lombok.config`.
- Use Checkstyle from root `config` folder.
- Use JaCoCo with 80% minimum line coverage.
- Use JSON structured logs to stdout.

Do not replace a requested Java/Spring backend with Node.js/Express unless explicitly approved.

## Docker and Local Dry Run

Every Java/backend generated project must include:

- root `Dockerfile`
- root `docker-compose.yml`
- local Spring profile, normally `local`
- docker-compose profile named `local`
- PostgreSQL service for local run if DB is needed
- healthcheck for backend where possible

DB is needed when the project contains any persistence requirement, JPA entity, repository, Liquibase changelog, SQL migration, audit table, saved user data, uploaded metadata, or backend cache/state that must survive a process restart.

When DB is needed, Replit must create local DB wiring automatically:
- add PostgreSQL service to `docker-compose.yml`
- use `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`
- add a named volume for PostgreSQL data
- add PostgreSQL healthcheck
- configure backend datasource env vars in compose
- wire backend `depends_on` to PostgreSQL health where compose version supports it
- add Liquibase changelog skeleton before adding JPA entities
- local demo must not require a manually created external database

After generation or significant backend changes, run dry run:

1. `docker compose --profile local config`
2. `docker compose --profile local up --build -d`
3. Check health endpoint: `curl -f http://localhost:8080/<app-context-path>/actuator/health`
4. Check Prometheus endpoint: `curl -f http://localhost:8080/<app-context-path>/actuator/prometheus`
5. `docker compose --profile local down -v`

If execution is impossible in the current environment, document the exact reason and exact commands in README of the generated project.

## GitHub Actions CI Policy

For generated Java backend projects, create `.github/workflows/ci.yml` with this mandatory baseline:
- unit tests + Checkstyle + JaCoCo coverage gate (80%)
- optional/manual integration tests with PostgreSQL
- docker-compose local profile dry run
- Java 25 setup and Maven cache
- upload test artifacts where useful
- verify `git-commit-id-maven-plugin` is configured in project POMs
- verify `openapi-generator-maven-plugin` is configured in project POMs when REST API exists
- if React frontend exists, generate API types from OpenAPI and run frontend typecheck/build/tests
- verify usage logging env placeholders exist and service account keys are not committed

Use the canonical template CI file from this repository and copy it into generated projects:
- `templates/generated-project/.github/workflows/ci.yml`

OpenAPI structure and generation rules must follow:
- `templates/generated-project/openapi/canonical-openapi-rules.md`

## Java Backend Configuration

For Java backend projects, generate local/application configuration with:

- HikariCP config exactly according to company defaults.
- `spring.jpa.hibernate.ddl-auto: validate`
- `spring.jpa.open-in-view: false`
- PostgreSQL database type.
- Hibernate safe query settings.
- Actuator endpoints exposed for local/MVP profile.
- Do not override actuator base path unless explicitly requested.
- Health endpoint must be exposed as `GET /<app-context-path>/actuator/health`.
- Prometheus metrics endpoint must be exposed as `GET /<app-context-path>/actuator/prometheus`.
- `server.servlet.context-path` based on app name, for example `/sales-dashboard`.

Do not leave `/some-path-by-app-name` placeholder in generated code.

## L2 Cache Policy

If L2 cache candidates exist, use Ehcache configured through `ehcache.xml`.

Good candidates:
- read-mostly reference/dictionary entities
- stable lookup tables
- high-read/low-write entities
- expensive repeated DB lookups

Rules:
- Do not enable L2 cache blindly.
- Add cache only for explicit candidates.
- Use `ehcache.xml` under `src/main/resources`.
- Define every entity/query cache region explicitly.
- Use Hibernate region prefix `hibernate-cache`.
- Use `missing_cache_strategy: fail`.
- Default query-results region must not silently cache everything.

## PostgreSQL Data Types

Database IDs must be Java `Long` and PostgreSQL `BIGINT`.

For PostgreSQL string columns:
- Use `TEXT` by default.
- Use `TEXT` for long text.
- Do not use `VARCHAR`.
- Do not use MySQL-specific `LONGTEXT`.

## Frontend Policy

- Use React + TypeScript.
- Follow canonical frontend rules from `templates/generated-project/frontend/canonical-react-frontend-rules.md`.
- Keep pages thin.
- Move API calls and business logic into hooks/services.
- Always handle loading, empty, error, and success states.
- Use accessible UI.
- Do not put secrets in frontend env vars.
- Treat all frontend code as public.
- Frontend must never access DB/BigQuery/secrets directly.
- Frontend must communicate with backend through generated types from the committed OpenAPI YAML.
- Use `openapi-typescript` + `openapi-fetch` for a small generated-typed client.
- Use TanStack Query for server state.
- Do not handwrite frontend DTOs that duplicate OpenAPI schemas.

Canonical frontend OpenAPI source:
- `src/main/resources/static/api/v1/specs/openapi_3.0.3_spec.yaml` relative to the backend application module

## Authentication Policy (Mandatory)

Generated projects must support dual-mode auth:
- real Google SSO flow (Clerk preferred, or equivalent OIDC integration if project standards require it)
- mock local-user fallback mode when SSO keys are missing

Auth mode contract:
- auth mode must be selected through configuration/properties/env
- no hardcoded secrets
- login UI must exist and work in fallback mode
- when keys are provided, same project should switch to real SSO without code rewrite
- `AUTH_MODE=auto|sso|mock` must be supported
- `auto`: choose real SSO only when required keys are present; otherwise use mock mode
- `sso`: fail fast on startup if required SSO settings are missing
- `mock`: start without external IdP and use local mock login flow

Google SSO flow contract (must be explicit in generated projects):
1. Frontend starts Google sign-in (Clerk preferred) and obtains JWT token.
2. Frontend sends `Authorization: Bearer <jwt>` to backend on every protected API call.
3. Backend is the source of truth for auth status; frontend session state alone is never trusted.
4. Frontend must process auth errors consistently:
   - `401`: clear local auth state and redirect to login
   - `403`: show access denied state

Backend JWT validation requirements (real SSO mode, mandatory):
- configure backend as OAuth2 Resource Server (`spring-boot-starter-oauth2-resource-server`)
- verify JWT signature against IdP JWKS (`issuer-uri` discovery or explicit `jwk-set-uri`)
- validate token `iss` (issuer), `aud` (audience/client), `exp`, and `nbf` when present
- reject invalid/missing token with `401`
- reject authenticated user without required permission/role with `403`
- map trusted claims (`sub`, `email`, roles/groups claim) to backend principal and authorities
- do not trust frontend-only auth state without backend token validation
- issuer/audience must be provider-correct:
  - Clerk path: issuer is Clerk issuer URL; audience is configured Clerk token audience
  - direct Google OIDC path: issuer is Google issuer; audience is `GOOGLE_CLIENT_ID`

Mock fallback contract (mandatory):
- provide mock login endpoint and visible login form for local/demo use
- mock mode must still use Bearer JWT for protected APIs (same API contract as SSO mode)
- mock JWT signing key and default mock user must come from properties/env
- generated projects should include `GET /api/v1/auth/me` endpoint that returns current user in both modes

OpenAPI contract requirements for auth-protected APIs:
- define bearer JWT security scheme
- mark protected operations with security requirements
- include explicit `401` and `403` responses with examples
- document auth bootstrap endpoint(s), including mock login endpoint if mock mode exists

The canonical auth implementation contract is defined in:
- `templates/generated-project/auth/google-sso-clerk-blueprint.md`

Recommended properties/env placeholders:
- `AUTH_MODE` (`auto|sso|mock`)
- `AUTH_ISSUER_URI`
- `AUTH_JWKS_URI` (if issuer discovery is not used)
- `AUTH_AUDIENCE`
- `CLERK_PUBLISHABLE_KEY`, `CLERK_SECRET_KEY` (if Clerk is used)
- `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` (if direct Google OIDC is used)
- `AUTH_MOCK_USER` (fallback demo user)
- `AUTH_MOCK_JWT_SECRET` (fallback mock JWT signing secret)

Runtime config clarification:
- preferred flow is `Google -> Clerk -> application`
- in that preferred flow, Google OAuth client credentials are configured in Clerk, not consumed as normal application runtime secrets
- do not expose Google client secret to frontend code

## BigQuery Policy

If business requirements need BigQuery:
- add backend integration and dependency
- keep frontend isolated from direct BigQuery access
- read all BigQuery credentials/configuration from properties/env
- never commit real credentials

## Usage Logging Policy (Mandatory)

Generated backend services must implement fire-and-forget usage logging to the shared BigQuery usage table:
- `aiae-493511.usage_logging_ai_services.usage_logging_ai_services_table`

Rules:
- follow `templates/generated-project/observability/usage-logging-bigquery-rules.md`
- do not commit service account JSON keys
- read credentials from `BQ_USAGE_CREDENTIALS_JSON`
- include `.env.example` placeholders for `BQ_USAGE_CREDENTIALS_JSON`, `BQ_USAGE_TABLE`, `USAGE_LOG_SERVICE_NAME`, `USAGE_LOG_ENVIRONMENT`, `USAGE_LOGGING_ENABLED`, `USAGE_LOG_LOCAL_FALLBACK_ENABLED`
- if credentials exist, write to BigQuery
- if credentials are missing in local/dev and fallback is enabled, write to local PostgreSQL table `usage_log_events`
- logger must no-op only when usage logging is disabled or tests intentionally disable it
- logger must never break or delay a user request
- log meaningful user actions, auth actions, errors, and custom domain events
- do not log health, actuator, static, OPTIONS, prefetch, or probe traffic
- do not log secrets, JWTs, raw request bodies, raw documents, service account JSON, or third-party PII

## Logging Policy (Critical)

Backend services must emit structured logs in JSON format to stdout for centralized monitoring compatibility.
Plain text backend logs are not acceptable unless explicitly approved.

Backend services must log inbound/outbound HTTP request and response metadata and bodies with Zalando Logbook:
- follow `templates/generated-project/observability/logbook-http-logging-rules.md`
- add `org.zalando:logbook-spring-boot-starter`
- log request and response bodies for application endpoints after filtering
- mask sensitive headers and JSON fields
- skip health, actuator, swagger, OpenAPI spec, static, OPTIONS, prefetch, and probe traffic

## Code Ownership

Every generated MVP repository must include:
- README with purpose, owner, data sources, env vars, run/deploy steps, and MVP limitations.
- `.env.example` with required variables but no real values.
- Clear folder structure.
- Git-friendly code exportable to company repositories.
- GitHub Actions CI file in `.github/workflows/ci.yml`.

When auth/BigQuery are used, `.env.example` must contain placeholders for:
- Google SSO / Clerk keys (as applicable)
- mock-auth toggle/user placeholders
- BigQuery project and credential-related keys

## Skills

Use these skills when relevant:
- Backend Java: `.agents/skills/backend-java-feature/SKILL.md`
- OpenAPI Contract First: `.agents/skills/openapi-contract-first/SKILL.md`
- Frontend React: `.agents/skills/frontend-react-feature/SKILL.md`
- MVP safety: `.agents/skills/mvp-safety-review/SKILL.md`
- Handoff: `.agents/skills/engineering-handoff/SKILL.md`
