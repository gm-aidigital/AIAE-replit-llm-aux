---
name: backend-java-feature
description: Build production-ready Java backend modules with OpenAPI-first contracts, strict quality gates, canonical templates, and predictable runtime behavior.
argument-hint: "[feature-summary]"
user-invocable: true
---

# Backend Java Feature

Use this skill for Java/Spring backend work in generated project repositories.

## Non-Negotiable Baseline

- Java 25
- Spring Boot 4.x
- Maven multi-module with parent `pom.xml` and `dependencyManagement`
- OpenAPI contract-first
- Liquibase
- PostgreSQL
- HikariCP baseline
- Checkstyle from root `config`
- JaCoCo minimum line coverage 80%
- Structured JSON logs to stdout
- Actuator endpoints on standard route (`/actuator/*`) with context-path preserved
- Prometheus metrics endpoint enabled
- `git-commit-id-maven-plugin` configured

Do not replace Java backend with Node unless explicitly approved.

## Canonical Files to Copy

Copy from template repository to generated project when applicable:

- Checkstyle:
  - `config/check_style_config.xml`
  - `config/check_style_suppressions.xml`
- CI:
  - `templates/generated-project/.github/workflows/ci.yml`
- Maven plugin snippet:
  - `templates/generated-project/pom-snippets/git-commit-id-maven-plugin.xml`
  - `templates/generated-project/pom-snippets/openapi-generator-maven-plugin.xml`
- Auth blueprint:
  - `templates/generated-project/auth/google-sso-clerk-blueprint.md`
- OpenAPI rules:
  - `templates/generated-project/openapi/canonical-openapi-rules.md`
- Project structure:
  - `templates/generated-project/structure/near-production-project-structure.md`
- Token-efficient generation:
  - `templates/generated-project/generation/token-efficient-generation-rules.md`
- Usage logging:
  - `templates/generated-project/observability/usage-logging-bigquery-rules.md`
- HTTP logging:
  - `templates/generated-project/observability/logbook-http-logging-rules.md`

## Mandatory Files in Generated Java Backend

- root `pom.xml` with `<packaging>pom</packaging>`
- module POMs
- root `.gitignore`
- root `Dockerfile`
- root `docker-compose.yml`
- root `.github/workflows/ci.yml`
- root `lombok.config`
- root `config/check_style_config.xml`
- root `config/check_style_suppressions.xml`
- `README.md`
- `.env.example`
- OpenAPI contract files in project standard location
- `src/main/resources/application.yml`
- `src/main/resources/application-local.yml`
- `src/main/resources/logback-spring.xml`
- `src/main/resources/ehcache.xml` if L2 cache is enabled

`lombok.config` must contain:

```properties
lombok.addLombokGeneratedAnnotation = true
```

## Maven Parent POM Requirements

- Centralized versions in `<properties>`
- Centralized dependencies in `<dependencyManagement>`
- Centralized plugins in `<pluginManagement>` where useful
- Java:
  - `<java.version>25</java.version>`
  - `<maven.compiler.release>25</maven.compiler.release>`
- Spring Boot 4.x dependency management
- Lombok and MapStruct version management
- Checkstyle plugin bound to root `config`
- JaCoCo coverage check with threshold 0.80
- Surefire/Failsafe split for UT/IT
- `git-commit-id-maven-plugin` in `validate` phase

## `git-commit-id` Plugin (Mandatory)

Use canonical snippet from:
- `templates/generated-project/pom-snippets/git-commit-id-maven-plugin.xml`

Minimum expected behavior:
- groupId: `io.github.git-commit-id`
- artifactId: `git-commit-id-maven-plugin`
- goal: `revision`
- phase: `validate`
- `offline=true`
- `skipPoms=false`
- `gitDescribe.skip=false`

## Architecture and Layering

- `application`: controllers, generated API interfaces, API mappers, auth/user context boundary
- `service`: orchestration and business logic
- `domain`: entities, repositories, domain enums/exceptions
- `db`: Liquibase migrations
- `external-services`: adapters/clients (BigQuery, external APIs)
- `common`: shared helpers/utilities/errors

Rules:
- Controllers are thin.
- No business logic in controllers.
- Do not expose JPA entities as API models.
- Generated projects must follow `templates/generated-project/structure/near-production-project-structure.md`.

## OpenAPI-First Delivery

Before code changes for API behavior:

1. Update OpenAPI contract first.
2. Ensure every operation has:
   - unique `operationId`
   - request/response schemas
   - validation constraints
   - status codes
   - security requirements
   - examples for main flows and errors
3. Regenerate/reuse API interfaces and DTOs.
4. Implement generated interfaces.
5. Add/adjust tests for contract compliance.

Do not manually duplicate generated DTOs.

Mandatory OpenAPI generator contract:
- use static spec at `src/main/resources/static/api/v1/specs/openapi_3.0.3_spec.yaml` relative to the backend application module
- use `openapi-generator-maven-plugin`
- use generator `spring` with library `spring-boot`
- use `interfaceOnly=true`
- use `useSpringBoot4=true`
- use `openApiNullable=false`
- use `skipDefaultInterface=true`
- use `useTags=true`
- use `dateLibrary=java8-localdatetime`
- generate into `${project.build.directory}/generated-sources/openapi`
- keep generated code in dedicated generated packages

## Runtime Configuration Baseline

Use this baseline in local profile (adapt only app-specific values):

```yaml
spring:
  datasource:
    hikari:
      idle-timeout: 60000
      maximum-pool-size: 50
      connection-timeout: 2000
      minimum-idle: 1
      auto-commit: false
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
    database: postgresql
    properties:
      hibernate:
        cache:
          use_second_level_cache: true
          use_query_cache: true
          region_prefix: hibernate-cache
          region:
            factory_class: org.hibernate.cache.jcache.JCacheRegionFactory
        javax:
          cache:
            uri: ehcache.xml
            missing_cache_strategy: fail
            provider: org.ehcache.jsr107.EhcacheCachingProvider
        query:
          in_clause_parameter_padding: true
          mutation_strategy:
            global_temporary:
              create_tables: false
              drop_tables: false
          immutable_entity_update_query_handling_mode: exception
        criteria:
          literal_handling_mode: bind
        default_batch_fetch_size: 300
      jakarta:
        persistence:
          sharedCache:
            mode: ENABLE_SELECTIVE

management:
  endpoints:
    web:
      exposure:
        include: "health,prometheus,env,configprops"
  endpoint:
    env:
      show-values: always
    configprops:
      show-values: always

server:
  servlet:
    context-path: /some-path-by-app-name
```

Important:
- Replace `/some-path-by-app-name`.
- Keep standard actuator base path (`/actuator`) unless explicitly requested.
- Expected URLs:
  - `/<app-context-path>/actuator/health`
  - `/<app-context-path>/actuator/prometheus`

## Auth Policy (Dual-Mode, Mandatory)

For generated MVPs, auth must be implementation-ready:

- Primary mode: real Google SSO (Clerk preferred, equivalent Google OIDC allowed if project standards require it).
- Fallback mode: mock local user flow when SSO keys are absent.

Requirements:
- Auth selection must be config-driven, no code rewrites required.
- UI must include login form/screen that works in fallback mode.
- With valid SSO keys provided, same flow must switch to real SSO.
- All keys must be read from properties/env (never hardcoded).
- `AUTH_MODE=auto|sso|mock` must be supported:
  - `auto`: if required SSO settings exist, use real SSO; otherwise fallback to mock.
  - `sso`: fail fast on startup when required SSO settings are missing.
  - `mock`: run without external IdP and expose only mock login flow.

Google SSO flow contract:
1. Frontend obtains JWT through Google SSO provider (Clerk preferred).
2. Frontend sends `Authorization: Bearer <jwt>` for every protected backend API.
3. Backend is source of truth for auth status (`/api/v1/auth/me`), never frontend state alone.
4. `401` clears frontend auth state and returns to login; `403` shows access-denied state.

Backend validation is mandatory in real SSO mode:
- every protected backend endpoint validates Bearer JWT
- verify JWT signature with IdP JWKS
- validate `iss`, `aud`, `exp`, and `nbf` when present
- return `401` for missing/invalid token
- return `403` for authenticated but unauthorized principal
- never treat frontend session/UI login state as proof of authentication
- map trusted claims (`sub`, `email`, roles/groups) to backend principal and authorities
- keep issuer/audience provider-correct:
  - Clerk: issuer from Clerk domain, audience from Clerk token template
  - direct Google OIDC: Google issuer + audience equal to `GOOGLE_CLIENT_ID`

Recommended Spring implementation:
- `spring-boot-starter-oauth2-resource-server`
- configure `spring.security.oauth2.resourceserver.jwt.issuer-uri`
- optionally set `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`
- set `spring.security.oauth2.resourceserver.jwt.audiences`
- map trusted claims (`sub`, `email`, groups/roles) into app user context

Mock fallback contract:
- expose mock login endpoint in mock mode
- issue backend-signed short-lived mock JWT
- keep Bearer JWT contract identical between mock and real SSO modes
- protect mock login endpoint behind `AUTH_MODE=mock` (or `auto` without keys)

Recommended config placeholders in `.env.example` / properties:
- `AUTH_MODE` (for example: `auto|sso|mock`)
- `AUTH_MOCK_USER` (demo fallback user id/login)
- `AUTH_ISSUER_URI`
- `AUTH_JWKS_URI`
- `AUTH_AUDIENCE`
- `AUTH_MOCK_JWT_SECRET`
- `CLERK_PUBLISHABLE_KEY` and `CLERK_SECRET_KEY` (if Clerk is used)
- `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` (if direct Google OIDC is used)

Clarification:
- preferred flow is `Google -> Clerk -> application`
- in that flow, Google OAuth client credentials are configured in Clerk, not used as normal application runtime properties

Canonical auth blueprint:
- `templates/generated-project/auth/google-sso-clerk-blueprint.md`

## BigQuery Policy

If use case requires BigQuery:
- Add backend dependency and backend integration only.
- Keep frontend isolated from direct BigQuery access.
- Read all BigQuery credentials/config from properties/env.
- Never commit real credentials.

Recommended config placeholders:
- `BIGQUERY_PROJECT_ID`
- `BIGQUERY_DATASET`
- credential source key(s) used by your runtime profile

## L2 Cache Policy

Enable Ehcache only for explicit candidates:
- read-mostly dictionaries
- stable lookup tables
- expensive high-read low-write queries

Rules:
- Do not cache everything.
- Define cache regions explicitly in `ehcache.xml`.
- Use `hibernate-cache` prefix and `missing_cache_strategy: fail`.

## Database Types (Mandatory)

- IDs: Java `Long`, PostgreSQL `BIGINT`
- Strings: PostgreSQL `TEXT`
- Long text: PostgreSQL `TEXT`
- Do not use `VARCHAR`

## Database and Docker Compose Policy

If persistence is required, Replit must add PostgreSQL automatically.

DB is required when there is any:
- JPA entity
- repository
- Liquibase changelog
- SQL migration
- saved user data
- audit state
- metadata that survives process restart

When DB is required:
- add PostgreSQL service to `docker-compose.yml`
- use compose profile `local`
- configure `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`
- use named PostgreSQL volume
- add PostgreSQL healthcheck
- wire backend datasource env vars to the PostgreSQL service
- add Liquibase changelog skeleton before JPA entities
- local dry run must not require manual DB creation

## Logging and Observability

- Logs must be structured JSON to stdout.
- Plain text logs are not acceptable unless explicitly approved.
- Include request/correlation IDs where available.
- Do not log secrets/tokens/raw sensitive payloads.

HTTP request/response logging is mandatory:
- follow `templates/generated-project/observability/logbook-http-logging-rules.md`
- add `org.zalando:logbook-spring-boot-starter`
- log request and response bodies for application endpoints after filtering
- output Logbook logs as JSON
- mask sensitive headers and JSON body fields
- skip health, actuator, swagger, OpenAPI specs, static, OPTIONS, prefetch, and probe traffic

Usage logging is mandatory for generated backend services:
- follow `templates/generated-project/observability/usage-logging-bigquery-rules.md`
- add `com.google.cloud:google-cloud-bigquery`
- read credentials from `BQ_USAGE_CREDENTIALS_JSON`
- write to `BQ_USAGE_TABLE` when credentials exist
- use local PostgreSQL `usage_log_events` fallback in local/dev when credentials are absent and fallback is enabled
- set service name through `USAGE_LOG_SERVICE_NAME`
- set environment through `USAGE_LOG_ENVIRONMENT`
- no-op only when usage logging is disabled or tests intentionally disable it
- fire-and-forget; never block or fail user requests because usage logging failed
- skip health, actuator, static, OPTIONS, prefetch, and probe traffic
- log success and error paths for the same action

## Testing and Quality Gates

- JUnit 5 + Mockito + AssertJ + Spring Boot test tooling
- Given/When/Then naming and structure
- Integration tests with `IT` suffix
- REST tests for success, validation, auth, and error format
- JaCoCo line coverage gate: 80%

## Docker and Dry Run

Required commands:

```bash
docker compose --profile local config
docker compose --profile local up --build -d
curl -f http://localhost:8080/<app-context-path>/actuator/health
curl -f http://localhost:8080/<app-context-path>/actuator/prometheus
docker compose --profile local down -v
```

If dry run cannot execute in current environment, document reason and exact commands in `README.md`.
