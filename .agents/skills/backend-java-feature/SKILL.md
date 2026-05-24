---
name: backend-java-feature
description: Build the backend for ANY generated project in this template. The template's backend is ALWAYS Java 21 LTS + Spring Boot 3.x + Maven + PostgreSQL — never Python/Flask/Django, never Node/Express, never anything else. Use for every backend feature regardless of how simple the user prompt sounds.
metadata:
  user-invocable: "true"
---

# Backend Java Feature

Use for **every** backend change. Template is Java/Spring exclusively — no
other backend stack is allowed. Prompt sounding like Flask/Express/Next →
**still generate Java + Spring Boot**. Stack lock: `custom_instruction/instructions.md`.

## Baseline (non-negotiable)

Java 21 LTS · Spring Boot 3.x · Maven multi-module · OpenAPI contract-first ·
Liquibase · PostgreSQL · HikariCP · Lombok · Checkstyle (`backend/config/`) ·
JaCoCo (phased gate) · JSON logs to stdout · Actuator on `/actuator/*` preserving
context-path · `git-commit-id-maven-plugin`.

Never replace Java with Node without explicit user approval.

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
| Checkstyle | `templates/generated-project/scaffold/backend/config/checkstyle.xml`, `checkstyle-suppressions.xml` |
| CI baseline | `templates/generated-project/.github/workflows/ci.yml` |
| `git-commit-id` plugin (inline in parent pom) | `templates/generated-project/scaffold/backend/pom.xml` |
| OpenAPI generator plugin (inline in parent pom) | `templates/generated-project/scaffold/backend/pom.xml` |
| Spring Boot gotchas (lookup) | `references/spring-boot-gotchas.md` |
| Canonical code patterns | `references/code-patterns.md` |
| HikariCP / JPA runtime config | `references/hikari-jpa-baseline.yml` |
| Replit profile snippet | `references/application-replit.yml` |
| DATABASE_URL → JDBC translation | `references/database-url-translation.md` |
| Replit DataSource post-processor | `references/ReplitDatabaseUrlPostProcessor.java` |

## Mandatory files

Files list: `custom_instruction/instructions.md` → "Mandatory generated-project
artifacts". OpenAPI spec at `backend/application/src/main/resources/api/v1/specs/openapi.yaml`
(NOT under `static/` — Vite build wipes it; `OpenApiSpecConfig` WebMvcConfigurer
re-exposes it). `ehcache.xml` only when L2 cache enabled.

## Maven parent POM

All versions in `<properties>`, deps in `<dependencyManagement>`, plugins in
`<pluginManagement>`. Java 21, Spring Boot 3.x, JaCoCo line-coverage check,
Surefire (UT) + Failsafe (`*IT` suffix), `git-commit-id-maven-plugin`
(`validate`), `openapi-generator-maven-plugin` (`generate-sources`, activated
in `application/pom.xml`). Canonical inline config:
`templates/generated-project/scaffold/backend/pom.xml`.

## Architecture (5 modules + strict package layout)

Full layout: `templates/generated-project/structure/near-production-project-structure.md`
→ "Backend package layout" + "Backend module dependency graph" + "Forbidden
module-boundary anti-patterns".

Hard recap:
- 5 Maven modules: `application`, `service`, `domain`, `db`, `external-services`.
  No `common` Maven module — cross-cutting types live under
  `service/<base>/service/common/{error,observability,security}/`.
- Plural folders (`services/`, `mappers/`, `models/`, `entities/`,
  `repositories/`, `controllers/`) hold many artifacts of one kind. Singular
  (`common/`, `error/`, `observability/`, `security/`, `config/`, `usage/`)
  are namespaces. NEVER write singular variants of the plural ones — Agent
  defaults to singular and silently fragments the codebase.

### Dependency matrix (enforced by poms)

| From ↓ depends on → | application | service | domain | db | external-services |
|---|---|---|---|---|---|
| `application` | — | ✓ | ✗ (transitive via service) | ✓ | ✗ |
| `service` | ✗ | — | ✓ | ✗ | ✓ |
| `domain` | ✗ | ✗ | — | ✗ | ✗ |
| `db` | ✗ | ✗ | ✗ | — | ✗ |
| `external-services` | ✗ | ✗ | ✗ | ✗ | — |

- `application` calls only `service`; depends on `db` for runtime Liquibase
  changelogs (must land in the fat jar).
- `service` calls `domain` repos OR `external-services` clients.
- `domain`, `external-services`, `db` are leaves. External clients throw own
  `<Provider>ExternalException`; service wraps via `AppException`.

### Forbidden dependencies per module (hard rule)

| Module | NEVER depend on | Why |
|---|---|---|
| `service` | `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server`, `spring-boot-starter-web`, `spring-security-*`, `jakarta.servlet-api`, anything `org.springframework.web.*` | Service is business orchestration — no HTTP/JWT/caller knowledge. |
| `service` | `spring-boot-starter-data-jpa` (direct) | Uses repos from `domain` (transitive JPA is fine). Entities never escape service. |
| `domain` | `service`, `application`, `external-services`, anything except JPA + validation + Lombok | Pure leaf. |
| `external-services` | `domain`, `service`, `application`, `spring-boot-starter-data-jpa`, any `*Repository` | True leaf, NO internal deps. Throws own `<Provider>ExternalException`; service wraps. No DB. |
| `db` | Anything beyond `liquibase-core` | XML changelogs only. |

`domain` and `external-services` are LEAVES — internal `<dependency>` in
either pom → reject.

**Compile error `cannot find symbol: SecurityContextHolder` in `service/`:**
do NOT add spring-security to `service/pom.xml`. Remove `SecurityContextHolder`
from service code; add `AppUser caller` parameter; extract from
`@AuthenticationPrincipal Jwt jwt` in the controller; pass `AppUser` built
from JWT claims.

Examples: `references/code-patterns.md` → "AppUser pattern" + "Service that
needs DB + external call".

### Thin controllers (hard rule)

Controllers do ONLY four things:
1. Receive the generated API interface call.
2. Resolve authenticated user from `SecurityContext` (one line).
3. Call exactly ONE service method.
4. MapStruct → generated DTO; return.

No `if`/`switch`, no DB calls, no business validation, no error wrapping,
no manual DTO construction.

**Hard limits (Agent has violated all):**
- Method body ≤ 6 lines (incl. braces).
- ZERO `if`/`else`/`switch`/`try`/`catch`/`for`/`while`/`?:` in any method.
  Exception: one-line ternary in `ResponseEntity` builder.
- ZERO `*Repository` injections (services + api-mappers only).
- ZERO direct `entityManager`/`JdbcTemplate`/`DataSource`.

Safety-review grep:
```bash
grep -rn 'private final.*Repository' application/src/main/java/**/controllers/  # expected: empty
```

**`@Transactional` lives on the controller, not the service** (kills
`LazyInitializationException` structurally — controller tx spans service +
mapper + MVC serialisation). Class-level `@Transactional(readOnly = true)`;
write methods override with `@Transactional`.

```java
@RestController
@RequiredArgsConstructor
@Transactional(readOnly = true)
class <Domain>Controller implements <Domain>Api {
    private final <Domain>Service service;
    private final <Domain>ApiMapper mapper;

    @Override
    @Transactional
    public ResponseEntity<<Domain>V1> update<Domain>(Long id, Update<Domain>RequestV1 req) { ... }
}
```

Services may still add `@Transactional` for different propagation
(`REQUIRES_NEW` for outbox) — not required by default.

Canonical + anti-pattern: `references/code-patterns.md` → "Thin controller".

### Service interface + impl (hard rule)

Interface in `services/`, impl in `services/impl/`. Inject the interface,
never the impl. Only `*Impl` carries `@Service`. `@LogUsage`/`@Transactional`
on impl methods. Layout: `references/code-patterns.md` → "Service interface + impl".

### MapStruct between layers (hard rule)

Two mappers per resource: `Entity ↔ ServiceRecord` in `service/`,
`ServiceRecord ↔ ApiDto` in `application/`. Never handwrite
`new <Domain>V1(); dto.setX(...)` chains — parent pom sets
`unmappedTargetPolicy=ERROR`. Entities never escape `domain`.

### One entity = one MapStruct mapper per layer (hard rule)

Exactly two mapper files per resource: `<Domain>Mapper` (service/) covers
all directions/list/page; `<Domain>ApiMapper` (application/) covers all V1
schemas for this domain. Compose via `@Mapper(uses = ...)`.

Reject:
- **Mega-mapper** bundling multiple aggregates (Order + OrderItem + Customer).
- **Two mappers per entity per layer** (e.g. `EmployeeReadMapper` + `EmployeeWriteMapper`).
- **Inline nested conversion** when a dedicated mapper exists — use `uses = OrderItemMapper.class`.

Examples: `references/code-patterns.md` → "Two MapStruct mappers" + "MapStruct composition via `uses`".

## Error handling: one global `ErrorReason` enum (hard rule)

Full contract: `templates/generated-project/errors/error-handling-pattern.md`.

Exactly ONE enum at `<base>.service.common.error.ErrorReason`. Every
`AppException` is built from it:

```java
throw new AppException(ErrorReason.C001, id);                    // not found
throw new AppException(ErrorReason.C006, cause, "duplicate sku"); // conflict
```

**Forbidden** (Agent has done all):
- Per-domain enums (`EmployeeErrorReason`, `OrderErrorReason`).
- Recreating `AppErrorReason` interface — `AppException` takes the concrete enum.
- Splitting into `CommonErrorCodes` + `<Domain>ErrorCodes`.
- Throwing `ResponseStatusException`/`IllegalStateException`/`IllegalArgumentException`/
  `RuntimeException` — they bypass `GlobalExceptionHandler`'s code-prefix mapping.

Naming: enum constant IS the code, format `<L>nnn`. `C` is cross-cutting;
each domain claims a letter on first use (registry in `ErrorReason.java` docblock).
Non-400 status mappings → add branch in `statusForCode()`, never sprinkle status
logic in service/controller code.

## MapStruct mandatory — no manual mapping (hard rule)

```java
// FORBIDDEN
ResourceV1 dto = new ResourceV1();
dto.setId(record.id()); dto.setName(record.name());

// REQUIRED
return mapper.toDto(record);
```

Parent pom sets `unmappedTargetPolicy=ERROR`. Unmapped target →
`@Mapping(target = "...", ignore = true)` or `constant = "..."` (both keep
compile-time check).

## Javadoc on private methods (hard rule)

Public interface/override/self-documenting methods may skip Javadoc. **Private
methods MUST carry Javadoc** — that's where complexity hides and future Agent
edits drift silently. Doc: what isn't obvious from signature, edge cases,
sentinel returns, what it does NOT do. Comment WHY, not WHAT.

## Port architecture lock (hard reject if violated)

ONE public port: backend `5000` → externalPort `80`. Vite is workspace
preview only. Prior runs swapped Vite onto 5000 + Spring to 8080 — breaks
Reserved-VM Deployment (one port only); user sees dev server instead of
Spring serving the production React bundle.

| Surface | Port | Notes |
|---|---|---|
| Spring Boot | **5000** | `${PORT:5000}` in `application-replit.yml`; `.replit` `[env]` `PORT="5000"` |
| `.replit` `[[ports]]` primary | `5000 → 80` | Single public surface |
| Vite dev server | **5173** | Workspace preview only |
| `.replit` `[[ports]]` secondary | `5173 → 5173` | Preview pane |
| Production frontend | served by backend | `npm run build` → `backend/application/src/main/resources/static/` |

**Hard rules:**
- Never Vite=`5000`, never Spring=`8080` on `replit` profile.
- Never invent root `start.sh` — `.replit` parallel tasks run `mvn spring-boot:run` + `npm run dev`.
- Never hard-code `server.port: 8080` in `application-replit.yml` — keep `${PORT:5000}`.
- Never bypass `ReplitDatabaseUrlPostProcessor`. Don't set `spring.datasource.url`
  from `PGHOST`/`PGPORT`/etc. — the post-processor parses `DATABASE_URL` (single
  source on `replit`). `PG*` are legacy, may be unset by `postgresql-16`; Replit
  managed Postgres needs `sslmode=require` (`sslmode=disable` breaks). Delete
  `spring.datasource.url:` from `application-replit.yml` if present.

Tempted to "swap Vite onto 5000 so the webview shows the UI"? Stop. On
Deployment the UI is Spring serving the BUILT React from `/static/`.

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
| Spring profile | `replit` | `local` |
| PostgreSQL | Replit SQL DB via `DATABASE_URL` (`sslmode=require`); Hikari `2–3` | docker-compose; Hikari `50` |
| Server port | `5000` → external `80` | `8080` |
| Build | Maven via `.replit` Run | Maven or `docker compose --profile local up --build` |
| Deployment | Reserved VM (`gce`) | n/a |

Baseline: `references/hikari-jpa-baseline.yml` — copy into `application.yml`,
override only context-path and profile-specific DS vars. Replace
`/some-path-by-app-name` with the real context path.

## Auth, usage logging, L2 cache

This skill enforces:
- Backend deps `spring-boot-starter-oauth2-resource-server` (SSO/auto) +
  `spring-boot-starter-aop` (usage-logging aspect).
- Usage logging writes to `usage_events` via bundled Liquibase changelog;
  `NoOpUsageLogger` binds when `USAGE_LOGGING_ENABLED=false`.
- **`@LogUsage(action = "<dotted.lowercase>")` on EVERY `*ServiceImpl` public
  method representing a user action.** Manual `logger.record(...)` in business
  code is forbidden. AOP auto-wired; annotation coverage is your discipline.
- L2 cache opt-in per region in `ehcache.xml` with `missing_cache_strategy: fail`.

Full contracts: references table above.

## Database types

See `custom_instruction/instructions.md` → "Database policy".

## No magic values (mandatory)

Business literals come from ONE of:
1. `public static final` constant (e.g. `AuthConstants.AUTH_MODE_SSO = "sso"`).
2. `@ConfigurationProperties` bean. Property names in `*Constants` class so
   annotation parameters match.
3. OpenAPI YAML for HTTP status + error shapes.

Forbidden:
- `if (mode.equals("sso"))` — use the constant.
- Inline HTTP status numbers — use `HttpStatus.NOT_FOUND`, not `404`.
- Magic `@Table(name = "employees")` — define `static final String TABLE = "employees"`.
- Magic timeouts — externalise via `@Value` or properties bean.

## Testing (MVP safety suite + handoff)

Full policy: `templates/generated-project/testing/testing-policy.md`.

Stack: JUnit 5 + Mockito + AssertJ + Spring Boot Test. Given/When/Then.
`IT` suffix for integration tests. REST via `@WebMvcTest` or `@SpringBootTest`
with mock JWT.

Phases:
1. **Building** — no final test requirement yet; `-DskipTests` allowed only
   for internal loops while the app is not runnable, JaCoCo `0.00`.
2. **MVP safety suite** — Phase 1 exit = build succeeds + app boots +
   `/auth/me` 200 + one endpoint reads DB + frontend renders. Before final
   response/publish, add backend safety tests: app/health smoke, auth boundary,
   main happy path, main error contract, service unit tests, Liquibase smoke
   when persistence exists. Coverage ratchets after tests exist.
3. **Handoff** — `mvn -Phandoff verify` enforces `0.80` + Testcontainers IT.

**Phase 1→2 transition is non-negotiable.** Prior generations skipped Phase 2,
shipped with zero tests. The MOMENT exit criteria are met, switch mode; don't
accept further feature prompts until Phase 2 done. Min bar: every controller
has an auth/contract test AND every `*ServiceImpl` public method has one unit
test. `mvp-safety-review` REJECTS publish if backend tests are missing while
controllers/services exist.

User says "ship without tests, demo Friday"? Surface the risk; proceed only
after written confirmation.

### Phase 2 minimums per endpoint

- Service happy-path + main negative `AppException` branch (unit + Mockito).
- Controller success + 401 (no token) + 403 (wrong role) + 400 (validation) via `@WebMvcTest`.
- One Liquibase smoke (`@DataJpaTest` or Testcontainers) running master changelog.
- One application/health smoke for the generated backend.

## Build flags: what you may and may not skip

| Flag | Allowed? | Why |
|---|---|---|
| `-DskipTests` | ✅ Phase 1 only | Tests rewritten on implementation churn |
| `-Dcheckstyle.skip=true` | ❌ NEVER | Runs in <2s; fix violation |
| `-Djacoco.skip=true` | ❌ NEVER | Disables coverage gathering |
| `-Dmaven.test.skip=true` | ❌ NEVER | Skips COMPILATION — broken tests vanish. Use `-DskipTests` |
| `-Dopenapi.skip=true` | ❌ NEVER | Controllers implement generated interfaces |
| `-Dgit-commit-id.skip=true` | ✅ Anytime without `.git` | Scaffold default |

CI runs `mvn verify` without forbidden flags; local bypass delays failure.

## Spring Boot gotchas (lookup table)

Fixes in `references/spring-boot-gotchas.md` — open the moment you hit the
symptom. Do NOT improvise alternative fixes.

| Symptom | Where to look |
|---|---|
| Build fails with `TypeTag :: UNKNOWN` | TypeTag UNKNOWN (Lombok/JDK mismatch) |
| OAuth2 resource-server crashes at startup with empty `issuer-uri` | OAuth2 Resource Server auto-config |
| `requestMatchers("/<context-path>/...")` matches nothing | Spring Security `requestMatchers` |
| Frontend hits `/api/v1/...` and gets 404 from Spring | OpenAPI `servers` not applied to controllers |
| `LazyInitializationException` in `mapper.toDto(...)` | Lazy init — single rule (`@Transactional` on controller) |
| Postgres `bytea` error on JPQL `LOWER(CONCAT('%', :search, '%'))` | JPQL bytea crash |
| Tempted to use `JdbcTemplate` "for speed" | Don't mix JdbcTemplate with JPA |
| `EnvironmentPostProcessor` profile check no-ops | EnvironmentPostProcessor before profiles |
| Compile mismatch `OffsetDateTime` vs `LocalDateTime` | Time types — `LocalDateTime` only |
| `git-commit-id` plugin failing in workspace without `.git` | git-commit-id blocking in Replit shell |
| Service signature returns `Entity` (lazy safe but module-bad) | Services still return Records, not Entities |

## Local-dev dry run

```bash
docker compose --profile local config
docker compose --profile local up --build -d
curl -f http://localhost:8080/<app-context-path>/actuator/health
curl -f http://localhost:8080/<app-context-path>/actuator/prometheus
docker compose --profile local down -v
```

Skip on Replit. If blocked, document exact reason + commands in README.
