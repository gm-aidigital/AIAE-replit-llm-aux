# Spring Boot common gotchas (real failures from past generations)

Read BEFORE writing controllers, security config, or any DataSource hook.
The main `SKILL.md` has a one-line summary; long fixes live here.

## `EnvironmentPostProcessor` runs before profiles are resolved

`postProcessEnvironment()` runs BEFORE `SPRING_PROFILES_ACTIVE` is resolved
— `env.getActiveProfiles()` returns empty. Any `if (!profiles.contains("replit")) return;`
silently no-ops everywhere.

→ Gate on env-var presence (`DATABASE_URL`), not profile. The provided
`ReplitDatabaseUrlPostProcessor` does this — don't "improve" it back.

## OAuth2 Resource Server auto-config triggers on empty properties

`spring-boot-starter-oauth2-resource-server` on classpath → auto-config triggers
even when `spring.security.oauth2.resourceserver.jwt.issuer-uri` is empty.
Tries to fetch JWKS from empty URL → startup crash.

Fix (both required):
1. Always provide `@Bean JwtDecoder`. Scaffolded `SecurityConfig` has a
   hard-fallback branch that guarantees a decoder exists.
2. Do NOT put `spring.security.oauth2.resourceserver.jwt.*` in `application.yml`.
   Scaffolded `application.yml` omits them — don't add "for completeness".

## Spring Security `requestMatchers` does NOT include the context path

`server.servlet.context-path: /my-app` + `requestMatchers("/my-app/api/v1/...")`
→ matches nothing. Spring Security strips context path BEFORE matching.

→ Path literals in `requestMatchers` look like `/api/v1/auth/me`, never
`/<context-path>/api/v1/auth/me`. See `AuthConstants.PUBLIC_PATHS`.

## OpenAPI `servers: [{ url: /api/v1 }]` is NOT applied to controllers

The OpenAPI generator does NOT prepend `servers` URL to `@RequestMapping`
on generated interfaces. Interface declares `/auth/me`; frontend client sends
to `/api/v1/auth/me`; Spring MVC 404s.

Three remedies (scaffold uses #1):

1. **Class-level `@RequestMapping("/api/v1")` on every controller.** Required
   if `/api/v1` stays the wire URL without changing `application.yml`.
2. **`server.servlet.context-path: /api/v1`** + strip `/api/v1` from `servers`.
   Cleanest with one API base path.
3. **Inline `/api/v1` in every YAML path** + remove `servers`. Verbose YAML, no Java trickery.

Stay consistent across the project.

## `openapi-fetch` baseUrl must not duplicate `/api/v1`

The frontend schema keys are the literal OpenAPI paths. If the spec has
`/api/v1/auth/mock/login`, this call:

```ts
createClient<paths>({ baseUrl: "/api/v1" })
apiClient.POST("/api/v1/auth/mock/login", ...)
```

hits `/api/v1/api/v1/auth/mock/login`. Mock login looks broken even though
curling the backend endpoint works.

Fix: `baseUrl` is empty by default. Use it only for a host or servlet context
prefix, e.g. `/employee-directory`, never `/api/v1`.

## `LazyInitializationException` — single rule that ends it forever

JPA closes session at end of `@Transactional`; lazy fields touched after
throw. Past Agent runs crashed on `mapper.toDto(entity)` after service
returned an entity.

**Canonical rule**: put `@Transactional` on the **controller**, not the service.

- Class-level `@Transactional(readOnly = true)` as read default.
- Write methods (POST/PUT/PATCH/DELETE) override with method-level `@Transactional`.
- Services have NO `@Transactional` by default (`REQUIRED` joins the
  controller's tx). Add service-level only for different propagation
  (`REQUIRES_NEW` for outbox, `NEVER` for guards).

```java
@RestController
@RequestMapping("/api/v1/<resources>")
@RequiredArgsConstructor
@Transactional(readOnly = true)
class <Domain>Controller implements <Domain>Api {
    private final <Domain>Service service;
    private final <Domain>ApiMapper mapper;

    @Override
    public ResponseEntity<<Domain>V1> get<Domain>(Long id) {
        return ResponseEntity.ok(mapper.toDto(service.findById(id)));
    }

    @Override
    @Transactional
    public ResponseEntity<<Domain>V1> update<Domain>(Long id, Update<Domain>RequestV1 req) {
        return ResponseEntity.ok(mapper.toDto(service.update(id, req)));
    }
}
```

Tx spans service call + MapStruct mapping + MVC serialisation → lazy crash
structurally impossible. We tried tx-on-service; Agent failed to keep
annotate-service + map-inside-service + return-Record consistent — entity
escaped, lazy crash. One annotation on controller class is harder to forget.

`@EntityGraph` / `JOIN FETCH` / `EAGER` are PERFORMANCE tools (kill N+1).
Not lazy fixes — the rule above already prevents that.

## Services still return Records, not Entities

Independent of `@Transactional` location: service signatures return
`ServiceRecord` (immutable), never JPA entities. Reason now is module
boundaries (not lazy safety) — entities leaking from `service/` lets
`application/` import JPA-aware types, breaking the dependency matrix.

## JPQL `LOWER(CONCAT('%', :search, '%'))` Postgres `bytea` crash

When `:search` is `null`/untyped, Hibernate may bind as `bytea` → Postgres
rejects the concat. Build the pattern in Java, pass as single `String`:

```java
String pattern = (search == null || search.isBlank()) ? "%" : "%" + search.toLowerCase() + "%";
return repo.findByNameLike(pattern);
```

JPQL becomes `WHERE LOWER(e.name) LIKE :pattern`.

## Don't mix `JdbcTemplate` with JPA

Whole stack is JPA. `JdbcTemplate` for "fast inserts" = two persistence
paths. Use `JpaRepository.save` from `@Async` (bounded `TaskExecutor`) for
fire-and-forget. See `observability/usage-logging-rules.md`.

## `com.sun.tools.javac.code.TypeTag :: UNKNOWN` (Lombok/JDK mismatch)

Build dies with `ExceptionInInitializerError: com.sun.tools.javac.code.TypeTag :: UNKNOWN`
→ old Lombok on new JDK. Lombok hooks internal javac APIs.

Fix sequence:

1. `mvn -version` — Replit's `pkgs.jdk21` puts JDK 21 on PATH. Different JDK?
   ```bash
   export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
   ```
2. JDK is correct → bump `${lombok.version}` in `backend/pom.xml`. Compatibility:
   1.18.30+ → JDK 21, 1.18.36+ → JDK 23, 1.18.40+ → JDK 24.
3. Also seeing MapStruct crashes? Add `lombok-mapstruct-binding` to the
   annotation processor path (parent pom `maven-compiler-plugin` config).

Do NOT:
- Delete `@RequiredArgsConstructor`/`@Getter`/`@Builder` (bug is in Lombok wiring).
- Switch to a different annotation processor.
- `<skip>true</skip>` annotation processors — MapStruct DTOs vanish.

## Time types: `LocalDateTime` only — do NOT change `dateLibrary`

Generator locked to `<dateLibrary>java8-localdatetime</dateLibrary>` (parent
pom). Every `format: date-time` → `java.time.LocalDateTime`. Project-wide,
non-negotiable.

Past failure: Agent saw `OffsetDateTime` vs `LocalDateTime` compile mismatch
and "fixed" by changing generator to `<dateLibrary>java8</dateLibrary>`.
**Wrong direction.** Generator is source of truth; hand-written code adapts.

On `incompatible types: LocalDateTime cannot be converted to OffsetDateTime`:
1. Change hand-written code, replace `OffsetDateTime`/`ZonedDateTime`/`Instant`
   field declarations with `LocalDateTime`.
2. Do NOT touch `<dateLibrary>` in `backend/pom.xml`.
3. Do NOT add `<typeMappings>OffsetDateTime=LocalDateTime</typeMappings>` —
   same anti-pattern, finer grain.

UTC convention (safety):
- DB: `TIMESTAMPTZ` storing UTC.
- Service/controller signatures: `LocalDateTime` interpreted as UTC.
- JSON wire: ISO-8601 string without offset (`2026-05-24T10:00:00`); document
  UTC in each OpenAPI field description.
- Frontend: treats as UTC, converts to local only at display.

User locale → separate timezone field; never embed in the timestamp.

## Don't keep `git-commit-id-maven-plugin` blocking in Replit shell

Replit workspaces don't always have `.git`; plugin fails `mvn package`.
Scaffolded parent pom keeps `failOnNoGitDirectory=false` at plugin level so
both the named execution and Maven's default execution inherit it. In synthetic
CI copies without `.git`, also pass `-Dgit-commit-id.skip=true`.

Do NOT put `failOnNoGitDirectory=false` only inside one `<execution>` block:
Maven may still run a `default` execution in child modules and fail before the
rest of the reactor builds.

## Frontend path/method drift vs OpenAPI

Symptoms:
- Mock login works in the spec but UI hits `/api/v1/auth/mock-login`.
- Admin usage is implemented at `/api/v1/admin/usage` but UI calls
  `/api/v1/usage/summary`.
- Spec says `put`, UI sends `PATCH`.

Root cause: frontend bypassed the generated OpenAPI path/method types, or Vite
started before `npm run generate:api`.

Fix:
1. Use only `shared/api/client.ts` (`openapi-fetch`) under `frontend/src`.
2. Run `npm run generate:api` before Vite/typecheck/build.
3. Let TypeScript reject invalid `apiClient.METHOD("/path")` combinations.
4. Keep local/CI grep guards forbidding raw `fetch`, `axios`, and
   `XMLHttpRequest`.
