---
name: backend-java-feature
description: Build the backend for ANY generated project in this template. The template's backend is ALWAYS Java 21 LTS + Spring Boot 3.x + Maven + PostgreSQL — never Python/Flask/Django, never Node/Express, never anything else. Use for every backend feature regardless of how simple the user prompt sounds.
metadata:
  user-invocable: "true"
---

# Backend Java Feature

Use for **every** backend change in a generated project. The template
generates Java/Spring backends exclusively — there is no other backend skill
because no other backend stack is allowed.

If the user prompt sounds like Flask/Express/Next/etc would fit better:
**still generate Java + Spring Boot**. The lock is documented in `replit.md`
and `custom_instruction/instructions.md`; this skill does not override it.

## Baseline (non-negotiable in MVPs that need a Java backend)

Java 21 LTS (Java 25 LTS once available in Replit's nixpkgs) · Spring Boot 3.x · Maven multi-module · OpenAPI contract-first ·
Liquibase · PostgreSQL · HikariCP · Lombok · Checkstyle (root `config/`) ·
JaCoCo 80% line coverage · JSON logs to stdout · Actuator on `/actuator/*`
preserving context-path · `git-commit-id-maven-plugin`.

Do not replace a Java backend with Node unless the user explicitly approves.

## Canonical references (do not duplicate)

| Topic | Canonical file |
|---|---|
| Project structure | `templates/generated-project/structure/near-production-project-structure.md` |
| Testing (phased) | `templates/generated-project/testing/testing-policy.md` |
| OpenAPI rules | `templates/generated-project/openapi/canonical-openapi-rules.md` |
| OpenAPI review checklist | `templates/generated-project/openapi/openapi-review-checklist.md` |
| Auth (dual-mode) | `templates/generated-project/auth/google-sso-clerk-blueprint.md` |
| Usage logging | `templates/generated-project/observability/usage-logging-rules.md` |
| HTTP request/response logging | `templates/generated-project/observability/logbook-http-logging-rules.md` |
| Token-efficient generation | `templates/generated-project/generation/token-efficient-generation-rules.md` |
| Checkstyle | `config/checkstyle.xml`, `config/checkstyle-suppressions.xml` |
| CI baseline | `templates/generated-project/.github/workflows/ci.yml` |
| `git-commit-id` plugin | `templates/generated-project/pom-snippets/git-commit-id-maven-plugin.xml` |
| OpenAPI generator plugin | `templates/generated-project/pom-snippets/openapi-generator-maven-plugin.xml` |
| HikariCP / JPA runtime config | `references/hikari-jpa-baseline.yml` |
| Replit profile snippet | `references/application-replit.yml` |
| DATABASE_URL → JDBC translation | `references/database-url-translation.md` |

## Mandatory files in a generated Java backend

```
pom.xml (parent, <packaging>pom</packaging>)   .env.example
lombok.config                                   .gitignore
config/checkstyle.xml                           README.md
config/checkstyle-suppressions.xml              Dockerfile           (local-dev only)
.github/workflows/ci.yml                        docker-compose.yml   (local-dev only)
.replit                                         replit.nix
src/main/resources/application.yml
src/main/resources/application-replit.yml       src/main/resources/application-local.yml
src/main/resources/logback-spring.xml
src/main/resources/ehcache.xml (when L2 cache is used)
src/main/resources/static/api/v1/specs/openapi.yaml
```

`lombok.config` must contain `lombok.addLombokGeneratedAnnotation = true`.

## Maven parent POM

- Centralized versions in `<properties>`, deps in `<dependencyManagement>`,
  plugins in `<pluginManagement>` where useful.
- `<java.version>25</java.version>`, `<maven.compiler.release>25</maven.compiler.release>`.
- Spring Boot 3.x dependency management; Lombok + MapStruct versions managed.
- Checkstyle plugin → root `config/checkstyle.xml`.
- JaCoCo with 0.80 line-coverage check.
- Surefire (UT) + Failsafe (IT with `IT` suffix).
- `git-commit-id-maven-plugin` in the `validate` phase
  (see `pom-snippets/git-commit-id-maven-plugin.xml`).
- `openapi-generator-maven-plugin` in `generate-sources`
  (see `pom-snippets/openapi-generator-maven-plugin.xml`).

## Architecture (layering — strict, 5 modules)

```
backend/
  application/         REST controllers, OpenAPI-generated interfaces,
                       MapStruct mappers (ServiceRecord ↔ ApiDto), security
                       (SecurityConfig, MockJwtDecoder), GlobalExceptionHandler.
                       Controllers are THIN — see below.

  service/             Business orchestration, validation, workflow,
                       transaction boundaries (@Transactional lives here).
                       Owns service records (immutable value objects).
                       MapStruct mappers (Entity ↔ ServiceRecord).

  domain/              JPA entities, repositories, domain enums.
                       AppException family lives under domain/common/error/
                       (AppException, AppErrorReason, ValidationMessage,
                       ValidationParameter, ValidationMessageType, CommonErrorCodes).
                       Entities NEVER appear in REST.

  db/                  Liquibase changelogs ONLY. NO Java code.

  external-services/   External API / queue / message-broker clients.
                       NO database access. NO JPA dependency on this module.
                       Only the service layer calls into here.
```

**There is no separate `common` Maven module.** The AppException family lives
inside `domain` under the `.domain.common.error` package — matches CLS layout
(`ru.mos.emias.laboratory.domain.common`). Drop a `common` module if Agent
generates it.

### Dependency matrix (Maven-level, enforced via module poms)

| From ↓ depends on → | application | service | domain | db | external-services |
|---|---|---|---|---|---|
| `application` | — | ✓ | ✗ (transitive via service is fine) | ✗ | ✗ |
| `service` | ✗ | — | ✓ | ✗ | ✓ |
| `domain` | ✗ | ✗ | — | ✗ | ✗ |
| `db` | ✗ | ✗ | ✗ | — | ✗ |
| `external-services` | ✗ | ✗ | ✗ | ✗ | — |

Reading the table:
- `application` (controllers/MapStruct) calls **only** `service`.
- `service` calls **only** `domain` repositories OR `external-services` clients.
- `domain` is a leaf: entities + repos + AppException family. No imports of
  service, application, db, or external-services types.
- `external-services` is a leaf for HTTP/queue clients. **No JPA, no
  repository injections.** If an external client needs to persist something,
  it returns the data to its caller (`service`) which then writes via
  repositories.
- `db` is a leaf with no Java.

The matrix is enforced by Maven `<dependencies>` in each module's pom —
Maven won't compile a module that imports something it doesn't declare.

If a service method needs a DB row AND an external call:
```java
@Service
@RequiredArgsConstructor
public class <Domain>Service {
    private final <Domain>Repository repo;          // domain module
    private final <External>Client externalClient;   // external-services module
    private final <Domain>Mapper mapper;

    @Transactional
    public <Domain>Record syncFromExternal(Long id) {
        <Domain>Entity entity = repo.findById(id)
            .orElseThrow(() -> new AppException(<Domain>ErrorReason.E001, id));
        <External>Data fresh = externalClient.fetch(entity.getExternalId());
        entity.applyFresh(fresh);
        return mapper.toRecord(repo.save(entity));   // domain again
    }
}
```

The external client (external-services module) does NOT inject any
repository. If it needs to record the external response in DB, it returns
the data to service, and service writes via the repository.

### Thin controllers (hard rule)

Controllers do **only** four things:

1. Receive the generated API interface call (the OpenAPI-generated method).
2. Resolve the authenticated user from `SecurityContext` (one line).
3. Call exactly **one** service method.
4. Run the result through a MapStruct mapper into the generated DTO and return.

That's it. No `if`/`switch` on input, no DB calls, no business validation,
no error wrapping (the `@RestControllerAdvice` handles all errors), no
manual DTO construction with `new ApiEmployeeDto(...)`.

**`@Transactional` lives on the controller, not the service** (see the
"LazyInitializationException — single rule" gotcha below for the full
reasoning). The minimal change: declare the transaction at class level on
every controller as `@Transactional(readOnly = true)` (the read default),
then mark write methods with method-level `@Transactional` to override.

```java
@RestController
@RequestMapping("/api/v1/<resources>")
@RequiredArgsConstructor
@Transactional(readOnly = true)                  // ← class default: read tx
class <Domain>Controller implements <Domain>Api {
    private final <Domain>Service service;
    private final <Domain>ApiMapper mapper;

    @Override                                     // GET — inherits readOnly tx
    public ResponseEntity<<Domain>V1> get<Domain>(Long id) { ... }

    @Override
    @Transactional                                // ← override: writable tx
    public ResponseEntity<<Domain>V1> update<Domain>(Long id, Update<Domain>RequestV1 req) { ... }
}
```

This single annotation makes lazy crashes structurally impossible: the
controller's transaction stays open through the MapStruct call AND through
Spring MVC's response serialisation. Services can still add their own
`@Transactional` if they need a different propagation (REQUIRES_NEW for
outbox patterns, etc.), but it's not required by default.

Anti-pattern (DO NOT do this):
```java
@RestController
class <Domain>Controller implements <Domain>Api {
    public ResponseEntity<<Domain>V1> get<Domain>(Long id) {
        var entity = repo.findById(id).orElseThrow(...);   // ✗ repository in controller
        if (entity.getOwnerId() != currentUser.getId() &&  // ✗ business logic
            !currentUser.isAdmin()) {
            throw new ResponseStatusException(FORBIDDEN);  // ✗ wrong exception type
        }
        var dto = new <Domain>V1();                        // ✗ manual DTO build
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        return ResponseEntity.ok(dto);
    }
}
```

Canonical version:
```java
@RestController
@RequiredArgsConstructor
class <Domain>Controller implements <Domain>Api {
    private final <Domain>Service service;
    private final <Domain>ApiMapper mapper;

    @Override
    public ResponseEntity<<Domain>V1> get<Domain>(Long id) {
        var caller = SecurityContextHolder.getContext().getAuthentication();
        <Domain>Record record = service.findById(id, AppUser.from(caller));
        return ResponseEntity.ok(mapper.toDto(record));
    }
}
```

The service does the business work (department check, status validation,
etc.) and throws `AppException` on any failure. The mapper does the
shape translation only.

### Service layer convention: `XxxService` interface + `XxxServiceImpl`

Every service is **two types**, not one. The contract is an interface,
the behaviour is an implementation. Both live in the same package inside
`backend/service/`:

```
backend/service/src/main/java/<base>/service/<domain>/
  ResourceService.java          ← public interface, what callers depend on
  ResourceServiceImpl.java       ← @Service implementation
  ResourceMapper.java            ← MapStruct Entity ↔ Record (in the same package)
```

```java
// ResourceService.java — interface
public interface ResourceService {
    ResourceRecord findById(Long id);
    ResourceRecord update(Long id, ResourceUpdate update, AppUser caller);
    Page<ResourceRecord> search(ResourceQuery query, Pageable pageable);
}

// ResourceServiceImpl.java — implementation, ONLY type carrying @Service
@Service
@RequiredArgsConstructor
public class ResourceServiceImpl implements ResourceService {

    private final ResourceRepository repo;
    private final ResourceMapper mapper;

    @Override
    public ResourceRecord findById(Long id) {
        return repo.findById(id)
            .map(mapper::toRecord)
            .orElseThrow(() -> new AppException(ResourceErrorReason.E001, id));
    }
    // ...
}
```

Hard rules:

- The interface is what controllers and other services inject by type
  (`private final ResourceService service;`). Never inject `ResourceServiceImpl`.
- Only the `*Impl` class carries `@Service`. The interface has no Spring
  annotations.
- One file per type. Don't put interface + impl in the same `.java` file.
- The interface lives in the same package as the impl — no separate `api`
  sub-package for "service contracts". The package boundary
  (`backend/service/`) is the abstraction; an extra `api` sub-folder is
  noise.
- `@LogUsage`, `@Transactional` (when explicitly needed), and other
  annotations go on the **impl** methods, not on the interface. They are
  behaviour, not contract.

Why interface + impl rather than just the impl class:

1. **Testability** — controllers in unit tests mock `ResourceService`, not
   `ResourceServiceImpl`. Mockito handles both, but the typed boundary
   makes intent obvious.
2. **Refactor safety** — adding a second implementation (a `MockResourceService`
   for a feature flag, a `CachingResourceServiceImpl` decorator) doesn't
   require ripping out all the `ResourceServiceImpl` references at call
   sites.
3. **Self-invocation + AOP** — Spring AOP proxies must be applied at the
   interface seam. If a caller injects the impl class directly, certain
   proxy modes degrade. Interfaces sidestep the issue.

### MapStruct between layers (hard rule)

Two MapStruct mappers per resource:

| Direction | Location | Purpose |
|---|---|---|
| `Entity ↔ ServiceRecord` | `service/` module | Hide JPA, return immutable records to controllers/other services |
| `ServiceRecord ↔ ApiDto` (generated by openapi-generator) | `application/` module | Convert between business records and wire format |

```java
// service/.../mapper/<Domain>Mapper.java
@Mapper(componentModel = "spring")
public interface <Domain>Mapper {
    <Domain>Record toRecord(<Domain>Entity entity);
    <Domain>Entity toEntity(<Domain>Record record);
    List<<Domain>Record> toRecords(List<<Domain>Entity> entities);
}

// application/.../mapper/<Domain>ApiMapper.java
@Mapper(componentModel = "spring")
public interface <Domain>ApiMapper {
    <Domain>V1            toDto(<Domain>Record record);                 // V1 DTO from openapi-generator
    <Domain>Record        toRecord(Create<Domain>RequestV1 req);        // V1 request from openapi-generator
}
```

Do **not** handwrite `new <Domain>V1(); dto.setX(...)` chains. MapStruct
generates that code; you get a compile error if a field doesn't match,
which is what we want.

Entities never escape the `domain` module — they're not arguments or
return types of any controller, mapper-to-DTO, or external-service call.

## OpenAPI-first delivery

1. Update `openapi.yaml` first.
2. Make each changed operation tick the `openapi-review-checklist.md`.
3. Regenerate via `openapi-generator-maven-plugin`; implement generated interfaces.
4. Regenerate frontend types via `npm run generate:api` when a frontend exists.
5. Add tests for contract compliance.

Never handwrite DTOs that duplicate generated schemas.

## Runtime: Replit vs local-dev

| | Replit | Local-dev |
|---|---|---|
| Spring profile | `replit` (set via `.replit` `[env]`) | `local` |
| PostgreSQL | Replit SQL Database via `DATABASE_URL` (libpq URL, `sslmode=require`); convert to JDBC + set Hikari `max-pool-size: 2–3` | docker-compose service, named volume, healthcheck, Hikari `max-pool-size: 50` |
| Server port | `5000` (Replit maps to external `80`) | `8080` |
| Build | Maven directly (Replit Run workflow) | Maven or `docker compose --profile local up --build` |
| Deployment | Reserved VM (`deploymentTarget = "gce"`) | n/a |

Docker assets exist for local-dev only and are not invoked on Replit.

Baseline runtime config snippet lives in `references/hikari-jpa-baseline.yml`.
Copy it into `application.yml` and override only context-path and profile-specific
data source vars.

Important: replace `/some-path-by-app-name` with the real context-path.

## Auth, usage logging, L2 cache

See the canonical files in the table above. Do not restate the contracts here.
The skill enforces only that:

- backend depends on `spring-boot-starter-oauth2-resource-server` in SSO/auto modes
- backend depends on `spring-boot-starter-aop` (drives the usage-logging aspect)
- usage logging writes to the app's `usage_events` Postgres table via the
  bundled Liquibase changelog; `NoOpUsageLogger` binds when
  `USAGE_LOGGING_ENABLED=false`
- **`@LogUsage(action = "<dotted.lowercase>")` is on EVERY service-impl public
  method that represents a user action**. The aspect (`UsageLoggingAspect`)
  records each call automatically; manual `logger.record(...)` calls in
  business code are forbidden. The plumbing is auto-wired
  (`spring-boot-starter-aop` enables AspectJ proxies), but the **coverage**
  (which methods are annotated) is the developer's discipline — see
  `SampleServiceImpl` in scaffold for the canonical shape.
- L2 cache is opt-in per entity/region in `ehcache.xml` with
  `missing_cache_strategy: fail`

## Database types (mandatory)

IDs: Java `Long`, PostgreSQL `BIGINT`. Strings: PostgreSQL `TEXT`. No `VARCHAR`,
no MySQL `LONGTEXT`.

Dictionary / lookup data lives in its own table with `BIGINT id` + `TEXT code` +
`TEXT name` and foreign-key references. NO `CREATE TYPE … AS ENUM`. See the
"Database policy" section in `custom_instruction/instructions.md`.

## No magic values (mandatory)

Every string/number literal that has business meaning must come from ONE of:

1. **`public static final` constant** in a dedicated class (e.g.
   `AuthConstants.AUTH_MODE_SSO = "sso"`), used both in annotation parameters
   (`@Profile`, `@ConditionalOnProperty`) and runtime checks.
2. **`@ConfigurationProperties`** bean reading from `application.yml`
   (e.g. `app.usage-logging.service-name`). Constants for the property
   *names* go in a `*Constants` class so annotation parameters match.
3. **OpenAPI YAML** for HTTP status codes (decoded by Spring) and error
   response shapes.

Forbidden:
- Inline string literals like `if (mode.equals("sso"))` — use `AuthConstants.AUTH_MODE_SSO`.
- Inline numeric literals for HTTP status — use `HttpStatus.NOT_FOUND`,
  not `404`.
- Magic table/column names in `@Table(name = "employees")` — define
  `static final String TABLE = "employees"` on the entity and reference it.
- Magic timeout/limit numbers in service code — externalise via `@Value` or
  a properties bean, never `Thread.sleep(5000)`.

When refactoring discovers a literal, the fix is always: lift it into the
nearest constants class or properties bean, replace all callsites, commit.

## Testing and quality (PHASED — see testing-policy.md)

Canonical: `templates/generated-project/testing/testing-policy.md`.

Stack: JUnit 5 + Mockito + AssertJ + Spring Boot Test. Given/When/Then.
`IT` suffix for integration tests. REST tests use `@WebMvcTest` or
`@SpringBootTest` with a mock JWT.

**Tests are PHASED so token budget isn't burned on tests that the next
implementation iteration throws away.** Three phases:

1. **Phase 1 — Building**: no tests required, `-DskipTests` allowed,
   JaCoCo gate at `0.00`. Get the app running end-to-end first.
2. **Phase 2 — Post-working**: Agent **switches into test-writing mode**
   the moment Phase 1's exit criteria are met. Best-effort but mandatory
   — every endpoint gets at least one happy-path test + 401/403 +
   error-mapping test. Coverage ratchet (gate set to whatever the suite
   delivers, never decreases).
3. **Phase 3 — Engineering handoff**: `mvn -Phandoff verify` enforces
   `0.80` line coverage + integration tests with Testcontainers Postgres.

### Phase 1 → Phase 2 trigger (Agent stops adding features and writes tests)

ALL of these must be true:
- [ ] `mvn -f backend/pom.xml -DskipTests package` succeeds.
- [ ] Replit Run boots without unhandled exceptions in logs.
- [ ] `curl /api/v1/auth/me` with a mock JWT returns 200.
- [ ] At least one feature endpoint reads from the DB and returns data.
- [ ] Frontend renders without console errors.

When all five are green, **Agent's next batch of actions is writing
tests**. No new features until tests catch up to the existing endpoints.

### Phase 2 — what to write (per endpoint, minimum)

- Service happy-path + main negative `AppException` branch (unit, with
  Mockito-stubbed repository).
- Controller success + 401 (no token) + 403 (wrong role) + 400
  (validation) — `@WebMvcTest` with `MockMvc`.
- Liquibase smoke (`@DataJpaTest` or Testcontainers) that runs the master
  changelog cleanly.

Same-class non-trivial method calls get dedicated tests; use Mockito
`spy` only to isolate caller branching.

## Spring Boot common gotchas (real failures from past generations)

These are the issues that caused the most wasted iterations in prior Agent
runs. Read this section **before** writing controllers, security config, or
any DataSource hook.

### `EnvironmentPostProcessor` runs before profiles are resolved

`postProcessEnvironment()` is called BEFORE Spring resolves
`SPRING_PROFILES_ACTIVE`. `env.getActiveProfiles()` is empty at this point.
Any `if (!profiles.contains("replit")) return;` check silently no-ops in
every environment.

→ Gate on env-var presence (`DATABASE_URL`), not on profile. The provided
`ReplitDatabaseUrlPostProcessor` already does this; do not "improve" it
back into a profile check.

### OAuth2 Resource Server auto-config triggers on empty properties

As soon as `spring-boot-starter-oauth2-resource-server` is on the
classpath, Spring Boot's auto-config triggers — even if
`spring.security.oauth2.resourceserver.jwt.issuer-uri` is **the empty
string**. It will try to fetch JWKS from an empty URL and crash at startup.

Two-part fix (both required):
1. Always provide a `@Bean JwtDecoder`. The scaffolded `SecurityConfig`
   has a hard-fallback branch that fires when no other `@ConditionalOnProperty`
   match — guarantees a decoder always exists.
2. Do NOT put `spring.security.oauth2.resourceserver.jwt.*` in
   `application.yml`. The scaffolded `application.yml` already omits them.
   Don't add them back "for completeness".

### Spring Security `requestMatchers` does NOT include the context path

`server.servlet.context-path: /my-app` + `requestMatchers("/my-app/api/v1/...")`
→ matches nothing. Spring Security strips the context path **before**
matching. `requestMatchers` is application-relative, not server-relative.

→ Path literals in `requestMatchers` look like `/api/v1/auth/me`, never
`/<context-path>/api/v1/auth/me`. The scaffolded `AuthConstants.PUBLIC_PATHS`
follows this convention.

### OpenAPI `servers: [{ url: /api/v1 }]` is NOT applied to controllers

Servers URL in `openapi.yaml` documents where clients should send requests,
but the OpenAPI generator does **not** prepend it to the `@RequestMapping`
paths it emits on the generated API interfaces. The interface declares
`/auth/me`, not `/api/v1/auth/me`. The frontend's generated client uses the
servers URL and sends to `/api/v1/auth/me`. Spring MVC then 404s because
its mapping is `/auth/me`.

Pick ONE of three remedies (the scaffold uses #1):

1. **Class-level `@RequestMapping("/api/v1")` on every controller** that
   implements a generated interface. Verbose but explicit. Required if you
   want `/api/v1` to remain the wire URL without changing `application.yml`.
2. **`server.servlet.context-path: /api/v1`** — then strip `/api/v1` from
   `servers` in `openapi.yaml`. Cleanest if you have only one API base path.
3. **Put `/api/v1` inline in every path in `openapi.yaml`** (`/api/v1/auth/me`)
   and remove the `servers` entry. Verbose YAML but no Java-side trickery.

The scaffolded controllers use option #1. Stay consistent across the project.

### `LazyInitializationException` — single rule that ends it forever

JPA closes the session at the end of `@Transactional`. Lazy fields touched
after that throw `LazyInitializationException`. Past Agent runs hit this
crashing on `mapper.toDto(entity)` after a service returned an entity.

**Single canonical rule**: put `@Transactional` on the **controller**, not
the service.

Mechanically:
- Every controller class carries class-level `@Transactional(readOnly = true)`
  as the read default.
- Write methods (POST / PUT / PATCH / DELETE) override with method-level
  `@Transactional`.
- Services do **not** carry `@Transactional` by default. They run inside the
  controller's transaction (default propagation `REQUIRED` joins it). Add
  service-level `@Transactional` only when a service needs different
  propagation (e.g. `REQUIRES_NEW` for outbox writes, `NEVER` for guards).

```java
@RestController
@RequestMapping("/api/v1/<resources>")
@RequiredArgsConstructor
@Transactional(readOnly = true)                       // ← single switch
class <Domain>Controller implements <Domain>Api {

    private final <Domain>Service service;
    private final <Domain>ApiMapper mapper;

    @Override                                          // GET — read tx
    public ResponseEntity<<Domain>V1> get<Domain>(Long id) {
        return ResponseEntity.ok(mapper.toDto(service.findById(id)));
    }

    @Override
    @Transactional                                     // ← write tx overrides
    public ResponseEntity<<Domain>V1> update<Domain>(Long id, Update<Domain>RequestV1 req) {
        return ResponseEntity.ok(mapper.toDto(service.update(id, req)));
    }
}
```

Why this works. The tx spans:
- the service call (lazy fields load freely),
- the MapStruct mapping (whether it's in the service or the controller),
- Spring MVC's response serialisation (no more lazy access after this — the DTO is plain Java).

The whole controller method including HTTP response writing happens inside
the same `@Transactional` proxy invocation. `LazyInitializationException`
is structurally impossible.

Why we don't put it on the service. We tried; it forces Agent to remember
three things at once (annotate service, call mapper inside service, return
Record not Entity), and screenshot 21 of a real generation shows Agent
fails to keep all three consistent — entity escapes, lazy crash. One
annotation on the controller class is harder to forget.

`@EntityGraph` / `JOIN FETCH` / `EAGER` are **performance** tools (kill
N+1 when the mapper realises many associations at once). They are NOT
fixes for `LazyInitializationException` — the rule above already prevents
that. Reach for them only when profiling shows N+1 traffic.

### Services still return Records, not Entities (architectural, not lazy-safety)

Independent of where `@Transactional` lives: service signatures still
return `ServiceRecord` (immutable Java records / DTOs), never JPA entities.
The reason is no longer lazy safety (the controller transaction handles
that) — it's **module boundaries**. Entities leaking out of `service/` would
let `application/` import JPA-aware types, breaking the dependency matrix
in the Architecture section above. Keep the convention; it costs one
MapStruct call inside the service.

### JPQL `LOWER(CONCAT('%', :search, '%'))` Postgres `bytea` crash

When `:search` is `null` or untyped, Hibernate's parameter binder may pick
`bytea` and Postgres rejects the concat. Build the search pattern in Java
and pass it as a single `String`:

```java
String pattern = (search == null || search.isBlank()) ? "%" : "%" + search.toLowerCase() + "%";
return repo.findByNameLike(pattern);
```

JPQL becomes `WHERE LOWER(e.name) LIKE :pattern`.

### Don't mix `JdbcTemplate` with JPA

The whole stack uses JPA. Reaching for `JdbcTemplate` for "fast inserts" is
a smell — two persistence paths to maintain. Use `JpaRepository.save` from
an `@Async` method (with a bounded thread-pool `TaskExecutor`) when you
need fire-and-forget writes. See `observability/usage-logging-rules.md`.

### Don't keep `git-commit-id-maven-plugin` blocking in Replit shell

Replit workspaces don't always have `.git`. The plugin will fail
`mvn package` in that environment. The scaffolded parent pom sets
`git-commit-id.skip=true` AND `failOnNoGitDirectory=false` — keep both.
Re-enable the skip only in CI where the checkout is a real git repo.

## Local-dev dry run

```bash
docker compose --profile local config
docker compose --profile local up --build -d
curl -f http://localhost:8080/<app-context-path>/actuator/health
curl -f http://localhost:8080/<app-context-path>/actuator/prometheus
docker compose --profile local down -v
```

Skip on Replit. If execution is blocked in the current environment, document
exact reason and exact commands in the project README.
