# Backend Java Project Standards

Prefer Java 25, Spring Boot 4.x, Maven, REST APIs, OpenAPI contract-first, Liquibase, PostgreSQL, HikariCP, Lombok, Checkstyle, JaCoCo and JSON structured logs.

Do not generate Node.js backend unless explicitly requested.

## Mandatory Artifacts

Every Java backend project must include:

- root parent `pom.xml` with `<packaging>pom</packaging>`
- `<dependencyManagement>` and centralized versions
- `Dockerfile`
- `docker-compose.yml`
- `.github/workflows/ci.yml`
- `lombok.config`
- `config/check_style_config.xml`
- `config/check_style_suppressions.xml`
- `README.md`
- `.env.example`

`lombok.config` must contain:

```properties
lombok.addLombokGeneratedAnnotation = true
```

Checkstyle files must be copied from this template repository:
- `config/check_style_config.xml`
- `config/check_style_suppressions.xml`

## Maven Parent POM

Parent POM must include:
- Java 25 properties
- Spring Boot 4.x dependency management
- Lombok and MapStruct dependency management
- maven-compiler-plugin with Lombok/MapStruct/Spring configuration processor
- maven-checkstyle-plugin referencing `/config/check_style_config.xml` and `/config/check_style_suppressions.xml`
- jacoco-maven-plugin with 80% line coverage check
- surefire/failsafe setup for unit and integration tests
- `io.github.git-commit-id:git-commit-id-maven-plugin` execution in `validate` phase
- `org.openapitools:openapi-generator-maven-plugin` execution in `generate-sources` phase for REST APIs

## Architecture

Use layered Spring structure:
- `application`: REST controllers, generated API interfaces, request/response mapping, security/user context, application use-case boundaries.
- `service`: business logic, orchestration, validation, domain operations, async task creation.
- `domain`: JPA entities, repositories, persistence logic, enums, domain exceptions.
- `db`: Liquibase migrations/changelogs.
- `external-services`: external clients/adapters.
- `common`: shared utilities, paging, errors, helpers.

Controllers must be thin. No business logic in controllers. Do not expose JPA entities via REST.

Generated project structure must follow:
- `templates/generated-project/structure/near-production-project-structure.md`

## REST and OpenAPI

All REST APIs are contract-first. Update OpenAPI before implementation. Define paths, methods, schemas, status codes, examples, validation, errors, security, operation IDs. Generate/reuse Java API interfaces and DTOs. Do not manually duplicate generated DTOs. Do not expose JPA entities.

Canonical OpenAPI rules are defined in:
- `templates/generated-project/openapi/canonical-openapi-rules.md`

Canonical generator snippet is:
- `templates/generated-project/pom-snippets/openapi-generator-maven-plugin.xml`

Use resource URLs, correct methods/status codes, JSON, ISO-8601, `/api/v1`.

Each changed operation must have:
- unique `operationId`
- request/response schemas
- validation constraints
- security requirements
- success and error examples

Do not implement endpoint behavior first and retrofit spec later.

Mandatory generation rules for Java backend REST APIs:
- static spec file under `src/main/resources/static/api/v1/specs/openapi_3.0.3_spec.yaml` relative to the backend application module
- generator `spring`
- library `spring-boot`
- `interfaceOnly=true`
- `useSpringBoot4=true`
- `openApiNullable=false`
- `skipDefaultInterface=true`
- `useTags=true`
- `dateLibrary=java8-localdatetime`
- generated sources must be placed under `${project.build.directory}/generated-sources/openapi`
- generated API/model/invoker packages must be explicit and separate from hand-written packages

## Required Spring Configuration

Use HikariCP and JPA baseline from company rules:
- idle-timeout 60000
- maximum-pool-size 50
- connection-timeout 2000
- minimum-idle 1
- auto-commit false
- ddl-auto validate
- open-in-view false
- PostgreSQL database
- Hibernate parameter padding and bind literal handling
- Actuator endpoints exposed in local/MVP profile
- default actuator base path (`/actuator`) unless explicitly requested otherwise
- Health endpoint exposed as `GET /<app-context-path>/actuator/health`
- Prometheus endpoint exposed as `GET /<app-context-path>/actuator/prometheus`
- app-specific `server.servlet.context-path`

## L2 Cache

If L2 cache candidates exist, use Ehcache via `ehcache.xml`.

Candidates are read-mostly reference/dictionary data, stable lookup tables and high-read/low-write entities. Do not enable cache blindly. Define all regions explicitly. Use `hibernate-cache` prefix and `missing_cache_strategy: fail`.

## PostgreSQL Types

Use Java `Long` and PostgreSQL `BIGINT` for IDs. Use PostgreSQL `TEXT` for strings and long text. Do not use `VARCHAR`. Do not use MySQL `LONGTEXT`.

## Docker and Local Run

Create Dockerfile and docker-compose. Compose must have profile `local`, backend service, PostgreSQL service when needed, env vars, ports and healthcheck where possible.

DB is required when the generated backend has persistence requirements, JPA entities, repositories, Liquibase changelogs, SQL migrations, saved user data, audit state, or metadata that must survive process restart.

When DB is required:
- add PostgreSQL to `docker-compose.yml`
- create DB through `POSTGRES_DB`
- add `POSTGRES_USER` and `POSTGRES_PASSWORD`
- use a named volume
- add PostgreSQL healthcheck
- configure backend datasource env vars in compose
- add Liquibase changelog skeleton before JPA entities
- local demo must not depend on a manually created external DB

Dry run required:

```bash
docker compose --profile local config
docker compose --profile local up --build -d
curl -f http://localhost:8080/<app-context-path>/actuator/health
curl -f http://localhost:8080/<app-context-path>/actuator/prometheus
docker compose --profile local down -v
```

## GitHub Actions CI

Create `.github/workflows/ci.yml` in generated Java backend projects.

Mandatory baseline:
- unit tests + Checkstyle + JaCoCo 80% coverage gate
- optional/manual integration tests with PostgreSQL
- docker-compose local profile dry run
- Java 25 setup and Maven cache
- verify `git-commit-id-maven-plugin` in project POMs
- verify `openapi-generator-maven-plugin` and generator options when REST API exists
- verify PostgreSQL service exists in compose when persistence is used
- verify frontend OpenAPI codegen/build when React frontend exists
- verify usage logging placeholders exist and service account keys are not committed

Use the canonical template CI file from this repository and copy it into generated projects:
- `templates/generated-project/.github/workflows/ci.yml`

Use canonical `git-commit-id-maven-plugin` snippet from this repository:
- `templates/generated-project/pom-snippets/git-commit-id-maven-plugin.xml`

## Observability

Services must emit structured JSON logs to stdout. Plain text backend logs are not acceptable unless explicitly approved. Include request/correlation IDs where available. Do not log secrets, tokens, personal data, raw sensitive payloads or private URLs.

Services must log inbound/outbound HTTP request and response bodies with Zalando Logbook after filtering sensitive data.

Canonical HTTP logging rules:
- `templates/generated-project/observability/logbook-http-logging-rules.md`

Java backend requirements:
- add `org.zalando:logbook-spring-boot-starter`
- output Logbook entries as JSON
- log request and response bodies for application endpoints
- mask sensitive headers and JSON body fields
- exclude actuator, health, swagger, OpenAPI specs, static, OPTIONS, and probe traffic

Services must also implement fire-and-forget usage logging to BigQuery for meaningful user actions.

Canonical usage logging rules:
- `templates/generated-project/observability/usage-logging-bigquery-rules.md`

Java backend requirements:
- add `com.google.cloud:google-cloud-bigquery`
- read service account JSON only from `BQ_USAGE_CREDENTIALS_JSON`
- write to `BQ_USAGE_TABLE` when credentials exist
- use local PostgreSQL `usage_log_events` fallback in local/dev when credentials are absent and fallback is enabled
- set stable service name through `USAGE_LOG_SERVICE_NAME`
- set environment through `USAGE_LOG_ENVIRONMENT`
- no-op only when usage logging is disabled or tests intentionally disable it
- never throw usage logging failures into request flow
- never commit service account JSON

## Authentication and BigQuery

Generated projects must support dual-mode authentication:
- real Google SSO path (Clerk preferred, or project-standard OIDC equivalent)
- mock local-user fallback when auth keys are missing

Auth and BigQuery configuration must be loaded from properties/env only.

Auth mode contract:
- `AUTH_MODE=auto|sso|mock`
- `auto`: use SSO only when required SSO keys are present; otherwise fallback to mock
- `sso`: fail startup if required SSO settings are missing
- `mock`: run without external IdP and use local mock login flow

Google SSO backend contract:
- frontend sends Bearer JWT on every protected backend request
- backend validates JWT for every protected request; frontend state is never trusted alone
- backend exposes `GET /api/v1/auth/me` for canonical authenticated-user payload

Backend JWT validation is mandatory in real SSO mode:
- validate Bearer token signature via IdP JWKS
- validate issuer (`iss`) and audience (`aud`)
- validate lifetime (`exp`) and `nbf` when present
- return `401` for missing/invalid token
- return `403` for insufficient permissions
- map trusted claims (`sub`, `email`, roles/groups) to backend principal/authorities
- keep issuer/audience provider-specific:
  - Clerk: issuer from Clerk domain, audience from configured Clerk token audience
  - direct Google OIDC: Google issuer + audience equal to `GOOGLE_CLIENT_ID`

Recommended Spring Security baseline:
- OAuth2 Resource Server with JWT (`spring-boot-starter-oauth2-resource-server`)
- configure:
  - `spring.security.oauth2.resourceserver.jwt.issuer-uri`
  - optional `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`
  - `spring.security.oauth2.resourceserver.jwt.audiences`
- keep protected endpoints under Spring Security authorization rules
- keep mock-login endpoint separate and enabled only for `mock` mode

OpenAPI must reflect auth:
- define bearer security scheme
- declare security requirements per protected endpoint
- document auth error responses (`401`/`403`)
- document auth bootstrap endpoint(s), including mock login endpoint when mock mode exists

Canonical auth blueprint:
- `templates/generated-project/auth/google-sso-clerk-blueprint.md`

If BigQuery is required:
- integration lives in backend only
- required dependency is added in backend module
- frontend calls backend API only
- `.env.example` includes placeholders for auth and BigQuery keys/config
- include placeholders: `AUTH_MODE`, `AUTH_ISSUER_URI`, `AUTH_JWKS_URI`, `AUTH_AUDIENCE`, `AUTH_MOCK_USER`, `AUTH_MOCK_JWT_SECRET`

Runtime config clarification:
- preferred auth flow is `Google -> Clerk -> application`
- for that preferred flow, Google OAuth client credentials are configured in Clerk and are not standard application runtime secrets
- do not push Google client secret into frontend runtime

## Testing

JUnit 5 and Spring Boot test tooling. One test verifies one behavior. Given-When-Then. Descriptive names. Integration tests use `IT` suffix. REST tests cover success mapping, validation failures, auth/authz, error format, edge cases, contract compatibility.

## Same-Class Method Calls

If a method calls another non-trivial method from the same class, write dedicated tests for the called method. In caller tests, use Mockito `spy` only to isolate orchestration/branching/validation/error handling. Use `doReturn`/`doThrow`. Do not duplicate full test matrix. If many methods need spying, refactor.

## JavaDoc

Write JavaDoc for public/protected business methods/classes, non-trivial package-private methods, custom exceptions, config/property classes, external clients, custom query methods, independently tested methods. Include `@param`, `@return`, `@throws`, and side effects. Avoid useless JavaDoc.

## Delivery

Every backend feature includes OpenAPI update, generated API/DTO update if needed, existing-layer implementation, validation, security, tests, DB migration if needed, JavaDoc, OpenAPI examples, Docker/local run support and CI.
