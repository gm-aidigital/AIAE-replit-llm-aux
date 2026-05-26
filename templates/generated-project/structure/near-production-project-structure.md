# Near-Production Project Structure

Structured for engineering handoff. Runs on Replit (`postgresql-16` injects
Postgres env vars, backend 5000 → external 80, Replit Secrets) AND local-dev
(`docker-compose --profile local`). Docker is local-dev only.

## Root layout

```
<project-root>/
├── README.md
├── .env.example
├── .gitignore                            # excludes template control plane + Python files
├── .replit                               # Replit workspace config (run, ports, modules)
├── replit.nix                            # Replit Nix package deps
├── docker-compose.yml                    # local-dev only; orchestrates backend + frontend + postgres
├── .github/workflows/ci.yml
├── backend/                              # ALL Java/Maven artifacts live here
│   ├── pom.xml                           # parent POM, <packaging>pom</packaging>
│   ├── Dockerfile                        # backend image (local-dev only)
│   ├── lombok.config
│   ├── config/                           # Checkstyle config referenced by parent pom
│   │   ├── checkstyle.xml
│   │   └── checkstyle-suppressions.xml
│   ├── application/                      # REST + security + GlobalExceptionHandler + UsageLoggingAspect
│   ├── service/                          # Business logic + MapStruct Entity↔Record + AppException family + LogUsage
│   ├── domain/                           # JPA entities + repositories ONLY (leaf)
│   ├── db/                               # Liquibase changelogs only
│   └── external-services/                # OPTIONAL: external clients, only when non-empty
└── frontend/                             # React + TypeScript + Vite
    ├── package.json
    ├── vite.config.ts
    ├── tsconfig.json
    ├── Dockerfile                        # frontend image (local-dev only)
    ├── nginx.conf                        # SPA routing + /api proxy for nginx runtime
    └── src/
```

**Hard rules** (artifact lives where it's served; root = project-level only):

- Parent `pom.xml` at `backend/pom.xml`, NOT at root.
- `lombok.config` at `backend/lombok.config`.
- `config/checkstyle*.xml` at `backend/config/`.
- `Dockerfile` split: `backend/Dockerfile` + `frontend/Dockerfile`. No root Dockerfile.
- `docker-compose.yml` at root (orchestrates backend + frontend + Postgres).

Build: `mvn -f backend/pom.xml ...` from root, or `cd backend && mvn ...`.

## Backend modules

Maven parent + 4 required modules. NO `common` Maven module and NO empty
Maven modules — cross-cutting types (AppException, ErrorReason, LogUsage)
live under `service/common/`.

- `application` — Spring Boot entrypoint, REST controllers, generated API
  impls, security, GlobalExceptionHandler, observability glue.
- `service` — business orchestration, validation, tx boundaries. HOSTS
  AppException family + LogUsage under `<base>/service/common/`.
- `domain` — JPA entities + repositories ONLY. Leaf.
- `db` — Liquibase changelogs + seed data.
- `external-services` — OPTIONAL external API adapters. Add this module only
  when the project has real outbound integrations (HTTP APIs, queues, vendor
  SDKs, BigQuery clients, etc.). TRUE LEAF (no internal deps). Throws own
  `<Provider>ExternalException`; service wraps into `AppException`.

If there are no external integrations, do not create
`backend/external-services/`, do not list it in parent `<modules>`, do not add
it to `<dependencyManagement>`, and do not depend on it from `service`.
When adding it later, add the module in the same change as the first real
client/adapter source files. A POM-only module is rejected.

## Backend module dependency graph (REQUIRED edges)

Dropping an edge is the #1 cause of "build passes but runtime breaks".

```
application ──► service ──► domain
            └─► db                                (runtime-only — see below)

service     ──► domain
            └─► external-services                 (optional, only when needed)

domain                                            (LEAF)
external-services                                 (OPTIONAL LEAF)
db          ──► liquibase-core                    (LEAF internally)
```

**Critical edge: `application → db`.** Migrations run at RUNTIME via Spring
Boot's Liquibase auto-config; changelog XMLs only land in the fat jar if
`application` depends on `db`. Drop this edge → green build + runtime crash
`Liquibase: changelog 'db/changelog/master.xml' not found`.

**Optional edge: `service → external-services`.** Add it only when
`external-services` exists and contains real external client code. A POM-only
`external-services` module is worse than no module because it trains future
edits to preserve dead structure.

**Forbidden edges:**
- `application → domain` or `application → external-services` directly (via `service`).
- `service → application` — cycle.
- `domain → service` / `domain → application` — leaf.
- `external-services → service` / `external-services → domain` — leaf.
- `db → anything internal` — XML only.

Versions pinned in `backend/pom.xml` `<dependencyManagement>`; children
reference by `<artifactId>` only.

### Forbidden module-boundary anti-patterns (file-placement crimes)

Each row is a real past failure — files compile in isolation but break the
build because they sit in the wrong module:

| Anti-pattern | Why it breaks | Correct placement |
|---|---|---|
| `*ApiMapper.java` (or anything importing the generated `api.v1.*` package) in `service/` | `service` does NOT depend on `application` and therefore cannot see generated OpenAPI types. Build fails with `package <base>.api.v1 does not exist`. Agent then "fixes" by adding `application` as a dependency of `service` — reverse edge, cycle, build never recovers. | `application/<aggregate>/mappers/<X>ApiMapper.java` — converts ServiceRecord ↔ generated DTO |
| `*Mapper.java` (Entity ↔ Record) in `application/` | `application` doesn't import JPA entities (it sees only ServiceRecord through `service`). Mapper has nothing to map. | `service/<aggregate>/mappers/<X>Mapper.java` |
| `@RestController` in `service/` | `service` doesn't depend on `spring-web`. Compile error on `@RestController`. | `application/<aggregate>/controllers/<X>Controller.java` |
| `@Entity` in `service/` or `application/` | JPA annotations require `spring-data-jpa`, only in `domain`. Even if it compiles, Hibernate scans only `domain` packages. | `domain/<aggregate>/entities/<X>Entity.java` |
| `AppException` / `ErrorReason` / `LogUsage` in `application/` or per-aggregate folders | They are cross-cutting service contracts; controllers throw via service. Splitting them creates duplicate `ErrorReason` enums and ambiguous catches in `GlobalExceptionHandler`. | `service/<base>/service/common/error/` and `…/common/observability/` — SINGLE source |
| `AppUser` / `CurrentUser` / any authenticated-principal value object in `application/security/` | Service-impl methods need the principal (`@LogUsage`, audit, authorisation). If it lives in `application/`, service can't import it without a reverse-edge cycle. Past sessions tried to "fix" by extending the record (Java records are final) or re-exporting — both dead ends. | `service/<base>/service/common/security/AppUser.java` — declared in service, used by both application's `SecurityConfig` and service-impl methods. The minimal `spring-security-core` dep can be added to `service/pom.xml` if needed for `Authentication`/`GrantedAuthority` types. |
| `<Provider>Client` in `service/` | External HTTP-client deps live in `external-services` only. Service catches the provider's exception and wraps. | `external-services/<provider>/<Provider>Client.java` |
| Java source files in `db/src/main/java/` | `db` is changelog-only. Adding Java code there means you've reinvented something that should live in `domain`/`service`. | Delete; rebuild the concern in the correct module |
| Two `@ConfigurationProperties` classes with the same prefix, or duplicate `@ConditionalOnProperty` on the same bean | Spring fails at startup with "more than one bean of type X". Symptom of agent copy-pasting a config from one aggregate to another. | Single `@Configuration` per concern; conditional logic lives on one bean, not on duplicates |
| Two `@ExceptionHandler` methods in `GlobalExceptionHandler` for overlapping exception types (e.g. one for `Exception`, one for `RuntimeException`) | Spring resolves "ambiguous handler method" at startup or at first error — depends on classpath order. | One handler per leaf exception type; have the catch-all (`Exception.class`) sit alone as the last line of defence |

**Grep-able review check:**
```bash
# zero matches across all three checks; otherwise the build is structurally broken
grep -rn "api.v1\." backend/service/src/main/java/
grep -rn "@Entity\|@Table" backend/service/src/main/java/ backend/application/src/main/java/
grep -rn "@RestController\|@RequestMapping" backend/service/src/main/java/
```

## Backend package layout (strict — inside each module)

Plural = collections, singular = namespaces. Agent defaults to singular →
silently fragments. Always plural.

```
application/src/main/java/<base>/
  Application.java                                  # @SpringBootApplication
  config/                                           # Spring config beans
  security/                                         # SecurityConfig, JwtDecoders, AuthProperties
  error/                                            # GlobalExceptionHandler (@RestControllerAdvice)
  observability/usage/                              # UsageLoggingAspect, PostgresUsageLogger, UsageLoggingConfig
  <aggregate>/                                      # ONE folder per domain aggregate
    controllers/<X>Controller.java
    mappers/<X>ApiMapper.java                       # ServiceRecord ↔ V1 DTO, ONE per entity

service/src/main/java/<base>/service/
  common/                                           # cross-cutting, NOT per-aggregate
    error/
      AppException.java
      ErrorReason.java                              # THE single enum, replaces AppErrorReason interface
      ValidationMessage.java
      ValidationParameter.java
      ValidationMessageType.java
    observability/
      LogUsage.java                                 # annotation
      UsageEvent.java                               # value object
      UsageLogger.java                              # interface (impl in application/)
  <aggregate>/                                      # ONE folder per domain aggregate
    services/<X>Service.java                        # interface
    services/impl/<X>ServiceImpl.java               # @Service impl, @LogUsage on each public method
    mappers/<X>Mapper.java                          # Entity ↔ Record, ONE per entity, compose via uses=
    models/
      <X>Record.java                                # output record (immutable)
      <X>Update.java                                # write input
      <X>Query.java                                 # filter input (when needed)

domain/src/main/java/<base>/domain/
  <aggregate>/                                      # ONE folder per domain aggregate
    entities/<X>Entity.java                         # @Entity, JPA-mapped
    repositories/<X>Repository.java                 # extends JpaRepository

external-services/src/main/java/<base>/external/        # OPTIONAL; only when non-empty
  <provider>/                                       # ONE folder per external API
    <Provider>Client.java                           # interface
    <Provider>ClientImpl.java                       # Feign/RestClient impl
    <Provider>ExternalException.java                # thrown OUT — service module wraps it

db/src/main/resources/db/changelog/                 # Liquibase XML only, NO Java
```

## Testing layout (MVP safety suite)

Canonical policy: `templates/generated-project/testing/testing-policy.md`.
The generated project is not complete with zero tests. MVP requires a lean
safety suite; handoff adds strict coverage and deeper integration tests.

Backend tests live beside the Maven module that owns the behavior:

```
application/src/test/java/<base>/
  smoke/
    ApplicationSmokeTest.java                       # context + health endpoint
  <aggregate>/controllers/
    <X>ControllerTest.java                          # auth boundary + API contract
  error/
    GlobalExceptionHandlerTest.java                 # ApiErrorV1 mapping, when needed

service/src/test/java/<base>/service/
  <aggregate>/services/
    <X>ServiceImplTest.java                         # happy path + main AppException path

db/src/test/java/<base>/db/
  LiquibaseChangelogSmokeTest.java                  # master changelog applies, when DB exists
```

Frontend tests use Vitest and live with the UI/hook they verify:

```
frontend/src/
  app/
    App.test.tsx                                    # render/auth shell smoke
  features/<feature>/
    <feature-name>.test.tsx                         # critical user flow
  shared/
    api/client.test.ts                              # auth-aware client behavior, when customized
```

MVP minimum:
- backend: app/health smoke, auth boundary, main happy path, main error path,
  service unit tests, Liquibase smoke when persistence exists.
- frontend: main render smoke, auth/session behavior, loading/error/success
  states for primary server-backed surfaces, critical form/action behavior.

Handoff minimum:
- backend: Testcontainers integration tests, OpenAPI contract tests, edge
  cases, business invariants, `mvn -Phandoff verify` at 80% line coverage.
- frontend: expanded behavior coverage for critical flows and role-dependent
  states; avoid snapshot-only suites.

Folder-naming hard rules:

| Plural (collections) | Singular (namespaces) |
|---|---|
| `services/`, `services/impl/` | `common/` |
| `mappers/` | `error/` |
| `models/` | `observability/` |
| `entities/` | `security/` |
| `repositories/` | `config/` |
| `controllers/` | `usage/` |

Never write singular `service/`, `mapper/`, `entity/`, `repository/`, `model/`,
`controller/` — forbidden.

## Module-level rules

- Controllers implement generated OpenAPI interfaces; thin (≤6 lines, no conditionals).
- Only `*ServiceImpl` carries `@Service`; each public impl method has `@LogUsage(action = "...")`.
- Entities/repositories never appear in REST contracts.
- DB via Liquibase. MapStruct for all conversion. One entity = one mapper per layer; compose via shared mapper config + `uses=`.
- Business errors as `AppException(ErrorReason.X, ...)` — never per-domain enums.
- Usage logging: `templates/generated-project/observability/usage-logging-rules.md`.

## JPQL / PostgreSQL query rules (known footguns)

Apply to every repository method with a nullable filter parameter.

**Rule 1 — Never call `LOWER(:param)` on a nullable JPQL parameter.**
Postgres infers SQL `NULL` as `bytea` → runtime crash `function lower(bytea)
does not exist`. Hibernate's metamodel doesn't catch this.

```jpql
// FORBIDDEN — crashes when :namePattern is null
@Query("select e from EmployeeEntity e where :namePattern is null or LOWER(e.fullName) like LOWER(:namePattern)")

// CORRECT — pre-lowercase in the service
@Query("select e from EmployeeEntity e where :namePattern is null or LOWER(e.fullName) like :namePattern")
```

```java
String namePattern = query.fullNameLike() == null ? null
    : "%" + query.fullNameLike().toLowerCase(Locale.ROOT) + "%";
```

**Rule 2 — Case-insensitive search:** `LOWER(column) LIKE :param`. Never
`ILIKE` (not portable JPQL). Add a functional index on `lower(column)` via Liquibase.

**Rule 3 — Every nullable filter needs `:p is null or …`.** JPQL `=` against null is `unknown`.

**Rule 4 — Never cast a nullable parameter inline** (`CAST(:p AS text)` hits
the same `bytea` problem). Cast in service if needed.

Review: `grep -rn 'LOWER(:' domain/src/main/java/` → zero matches.

## Frontend layout

Feature-first React (Bulletproof React shape, see
`templates/generated-project/frontend/canonical-react-frontend-rules.md`):

```
frontend/src/app          # providers, router, global error boundary, shell
frontend/src/pages        # route-level composition only
frontend/src/features     # feature modules (UI + hooks + feature API)
frontend/src/entities     # reusable domain UI/models
frontend/src/shared/api   # generated OpenAPI types + auth-aware fetcher
frontend/src/shared/ui    # reusable design-system components
frontend/src/shared/lib   # framework-agnostic helpers
frontend/src/shared/config
```

## Runtime: Replit vs local-dev

| | Replit | Local-dev |
|---|---|---|
| PostgreSQL | `PGHOST`/`PGPORT`/`PGDATABASE`/`PGUSER`/`PGPASSWORD` → HikariDataSource; no forced SSL | docker-compose Postgres |
| Backend port | `5000` → external `80` | `8080` |
| Frontend dev | Vite `5173` (preview only) | Vite `5173` |
| Frontend in Deployment | Spring serves `frontend/dist/` from `src/main/resources/static/` | n/a |
| Secrets | Replit Secrets pane | `.env` (gitignored) |
| Profile | `replit` (Hikari `2–3`) | `local` (Hikari `50`) |

Both share `application.yml` as base. See
`.agents/skills/backend-java-feature/references/database-url-translation.md`.

## Local-dev dry-run (handoff only)

```bash
docker compose --profile local config
docker compose --profile local up --build -d
curl -f http://localhost:8080/<app-context-path>/actuator/health
curl -f http://localhost:8080/<app-context-path>/actuator/prometheus
docker compose --profile local down -v
```
