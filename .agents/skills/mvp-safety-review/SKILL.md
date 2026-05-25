---
name: mvp-safety-review
description: Safety and release-readiness checklist for a generated MVP before sharing, publishing on Replit, or recording a demo. Use when the user says the project is ready, is about to share/publish, or is preparing a stakeholder demo.
metadata:
  user-invocable: "true"
---

# MVP Safety Review

Run before any publish / share / demo. Pure gate — does not change code.
Every check is binary: pass or **reject publish**.

## Security and secret hygiene

- [ ] No real secrets committed — grep for `BEGIN PRIVATE KEY`, `"private_key"`, `"type": "service_account"`.
- [ ] No production datasets embedded; no production service-account files in repo.
- [ ] `.env.example` exists with placeholders only, covering auth and usage-logging.
- [ ] README lists data sources, env vars, run/deploy steps, MVP limitations.

## Auth (dual-mode)

Canonical: `templates/generated-project/auth/google-sso-clerk-blueprint.md`.

- [ ] `AUTH_MODE=auto|sso|mock` honored; default `auto` works without keys.
- [ ] Login UI usable in mock mode; switches to real SSO when keys present.
- [ ] Backend validates Bearer JWT (JWKS signature; `iss`, `aud`, `exp`, `nbf`).
- [ ] `401` missing/invalid token; `403` unauthorized.
- [ ] No hardcoded auth secrets.

Reject if README claims SSO with only mock impl, or backend trusts frontend
state without JWT validation.

## Usage logging

Canonical: `templates/generated-project/observability/usage-logging-rules.md`.

- [ ] `USAGE_LOGGING_ENABLED`, `USAGE_LOG_SERVICE_NAME`, `USAGE_LOG_ENVIRONMENT` in `.env.example`.
- [ ] Liquibase changelog for `usage_events` runs at startup.
- [ ] `spring-boot-starter-aop` declared (enables `UsageLoggingAspect`).
- [ ] `UsageLoggingAspect` + `@LogUsage` annotation files present.
- [ ] `PostgresUsageLogger` binds when enabled; `NoOpUsageLogger` otherwise.
- [ ] Logger never blocks / fails user requests; insert errors are swallowed.
- [ ] `app.usage-logging.service-name` resolves to a real, non-placeholder
      value (NOT empty, NOT `replit-mvp-template`). Empty service-name +
      `enabled=true` must FAIL FAST at startup, not silently bind NoOp:
      ```bash
      # service-name in application.yml must NOT default to "" or to the
      # template placeholder. Should be `${spring.application.name}` or a
      # concrete app identifier.
      grep -E 'service-name:.*\$\{USAGE_LOG_SERVICE_NAME:\}' \
        backend/application/src/main/resources/application*.yml \
        && echo "REJECT: empty default for USAGE_LOG_SERVICE_NAME"
      grep -E 'spring\.application\.name:\s*replit-mvp-template' \
        backend/application/src/main/resources/application*.yml \
        && echo "REJECT: spring.application.name still at template placeholder"
      ```
- [ ] Frontend never reaches `usage_events` directly.
- [ ] **`@LogUsage` on every `*ServiceImpl` public method** — past generations
      defined the aspect but annotated zero methods, silently breaking dashboards:
      ```bash
      for s in $(find backend/service/src/main/java -name '*ServiceImpl.java'); do
        grep -q '@LogUsage' "$s" || echo "MISSING @LogUsage in $s"
      done
      ```
- [ ] No direct `usageLogger.record(...)` in business code:
      ```bash
      grep -rEn 'usageLogger\.record\(' backend/service backend/application/src/main/java/*/[!observability]*
      ```

## Backend / frontend quality

- [ ] OpenAPI contract updated for any API change; spec at `src/main/resources/api/v1/specs/openapi.yaml` (NOT under `static/`).
- [ ] `openapi-generator-maven-plugin` configured.
- [ ] Frontend uses `openapi-typescript` + `openapi-fetch` + TanStack Query.
- [ ] `frontend/package-lock.json` exists and is committed — enterprise
      reproducibility requires a pinned lockfile, not just version ranges:
      ```bash
      test -f frontend/package-lock.json || echo "REJECT: frontend/package-lock.json missing — run 'cd frontend && npm install' and commit it"
      ```
- [ ] Structured JSON logs to stdout; Actuator `health` + `prometheus` reachable through context-path.
- [ ] Checkstyle and JaCoCo plugins wired (default JaCoCo gate `0.00` in MVP — see Tests).

## Tests (publish gate — hard reject if missing)

Canonical: `templates/generated-project/testing/testing-policy.md`. Past
runs skipped Phase 2 and shipped with zero tests.

- [ ] Test files exist (zero tests with controllers present = reject):
      ```bash
      find backend -path '*/src/test/java/*' -name '*Test.java' -o -name '*IT.java' | wc -l
      ```
- [ ] Every `*Controller.java` has a matching test:
      ```bash
      for c in $(find backend/application/src/main/java -name '*Controller.java'); do
        name=$(basename "$c" .java)
        find backend -name "${name}Test.java" | grep -q . || echo "MISSING: ${name}Test.java"
      done
      ```
- [ ] Every `*ServiceImpl.java` has a matching test:
      ```bash
      for s in $(find backend/service/src/main/java -name '*ServiceImpl.java'); do
        name=$(basename "$s" .java | sed 's/Impl$//')
        find backend -name "${name}Test.java" -o -name "${name}ImplTest.java" | grep -q . || echo "MISSING test for: $name"
      done
      ```
- [ ] At least one application/health smoke test exists:
      ```bash
      find backend -path '*/src/test/java/*' \( -iname '*Application*Test.java' -o -iname '*Health*Test.java' -o -iname '*SmokeTest.java' \) | wc -l
      ```
- [ ] At least one Liquibase smoke test (`@DataJpaTest` running master changelog).
- [ ] `mvn -f backend/pom.xml verify` passes locally.
- [ ] Frontend behavior tests exist when frontend logic exists:
      ```bash
      find frontend/src \( -name '*.test.ts' -o -name '*.test.tsx' \) | wc -l
      ```
- [ ] Frontend tests and build pass:
      ```bash
      cd frontend && npm test && npm run build
      ```
- [ ] No commit removed existing tests just to clear CI.

Phase 3 (`mvn -Phandoff verify`, `0.80`) is the handoff bar, not MVP-publish.

## Replit deployment readiness

Canonical: backend SKILL → "Port architecture lock" + replit.md → "Replit deployment model".

### Workspace + Deployment config

- [ ] `.replit` + `replit.nix` present and the workspace runs.
- [ ] `.replit` `[env]` sets `SPRING_PROFILES_ACTIVE = "replit"` and `PORT = "5000"`.
- [ ] `.replit` `[deployment].deploymentTarget = "gce"` (Reserved VM), not Autoscale.
- [ ] `.replit` `onBoot` runs `bash templates/generated-project/scaffold/scripts/setup-project.sh`.
- [ ] Build command (`mvn -DskipTests package` + frontend-maven-plugin build) succeeds in CI.
- [ ] Deployment Secrets pane has copies of `SPRING_PROFILES_ACTIVE`, `CLERK_*`, `AUTH_*`,
      `USAGE_LOG_*` (workspace `[env]` does NOT propagate).

### Port architecture lock (hard reject)

Past runs swapped Vite onto 5000 + Spring to 8080 → broke Reserved-VM Deployment.

- [ ] `.replit` `[[ports]]` has EXACTLY ONE entry: `5000 → 80`. Vite 5173
      is workspace-only; adding a `[[ports]]` for it exposes 5173 externally
      in deployment where Vite isn't running.
- [ ] `application-replit.yml` uses `server.port: ${PORT:5000}` — not hard-coded:
      ```bash
      grep 'server.port:' backend/application/src/main/resources/application-replit.yml
      ```
- [ ] `application-replit.yml` does NOT set
      `spring.datasource.url|username|password|driver-class-name` —
      `ReplitDatabaseUrlPostProcessor` populates them; yaml entries break it:
      ```bash
      grep -E 'spring\.datasource\.(url|username|password|driver-class-name)' \
        backend/application/src/main/resources/application-replit.yml
      ```
- [ ] No legacy `PG*` env refs (Replit `postgresql-16` exposes `DATABASE_URL` only):
      ```bash
      grep -rE '\$\{PG(HOST|PORT|USER|PASSWORD|DATABASE)[:}]' \
        backend/application/src/main/resources/
      ```
- [ ] No HARDCODED `sslmode=disable` in app config — the
      `ReplitDatabaseUrlPostProcessor` reads sslmode from the `DATABASE_URL`
      query string (Replit's production tier carries `require`; Helium dev
      omits). Hardcoding `disable` in `application-replit.yml` would
      override prod and break TLS:
      ```bash
      grep -rEn 'sslmode=disable' backend/application/src/main/resources/
      ```
- [ ] `vite.config.ts` uses `port: 5173 + strictPort: true` (NOT 5000):
      ```bash
      grep -E 'port: *5(000|173)' frontend/vite.config.ts
      ```
- [ ] `vite.config.ts` `server.allowedHosts` covers `.replit.dev`, `.repl.co`,
      `.kirk.replit.dev` (otherwise Vite 5+ returns "Blocked request"):
      ```bash
      grep -E 'allowedHosts|replit\.dev' frontend/vite.config.ts
      ```
- [ ] No `start.sh` at project root:
      ```bash
      [ -f start.sh ] && echo "REJECT" || echo "OK"
      ```

### Local-dev artifacts (handoff is template default)

- [ ] `backend/Dockerfile` (multi-stage Maven → JRE).
- [ ] `frontend/Dockerfile` (Node → nginx static).
- [ ] `frontend/nginx.conf` (SPA routing + `/api` proxy).
- [ ] `docker-compose.yml` at project root with `local` profile, `postgres + backend + frontend`
      services, build contexts at `./backend` and `./frontend`.
- [ ] Local dry-run commands documented in README.

### HikariCP pool sizes

- [ ] `replit` profile: HikariCP `maximum-pool-size: 2-3` (Replit SQL Database has a low conn ceiling).
- [ ] `local` profile: HikariCP `maximum-pool-size: ~50`.

## Template control-plane leak (hard reject)

Recent runs committed `.agents/`, `templates/`, `custom_instruction/`,
`AGENTS.md`, `replit.md` to the customer repo — exposed internal skills.

- [ ] These paths are NOT tracked by git (if anything prints, run
      `bash templates/generated-project/scaffold/scripts/setup-project.sh`):
      ```bash
      git ls-files .agents templates custom_instruction AGENTS.md replit.md 2>/dev/null
      ```
- [ ] `.gitignore` excludes control-plane + Replit Agent runtime workspace:
      ```bash
      grep -E '^(\.agents|templates|custom_instruction|AGENTS\.md|replit\.md|\.local|\.config|server)' .gitignore
      ```

## Stack lock check (hard reject)

Repo must NOT contain any of:

- `main.py`, `requirements.txt`, `Pipfile`, `pyproject.toml`, `uv.lock`, `poetry.lock`,
  `manage.py`, `wsgi.py`, `asgi.py` (Python or Replit's auto-injected defaults).
- Root-level `package.json` declaring `express` / `fastify` / `koa` / `@nestjs/core` / `next`.
- `go.mod`, `Gemfile`, `composer.json`, `Cargo.toml`, `mix.exs` (other backends).
- Backend code outside `backend/` Maven module.
- `.replit` `modules` containing `python-*`.
- `.replit` `[agent] integrations` containing `flask_*`, `django_*`, `fastapi_*`.

Any match → stack lock violated. Delete offending files/lines, regenerate from
`templates/generated-project/scaffold/`.

## Parent pom location

- [ ] Parent `pom.xml` at `backend/pom.xml` — **not** at project root.
- [ ] Module paths in `backend/pom.xml` are relative to `backend/`
      (e.g. `<module>application</module>`, not `<module>backend/application</module>`).
- [ ] Child module poms use `<relativePath>../pom.xml</relativePath>`.

## Error handling: single `ErrorReason` enum (hard reject)

Canonical: `templates/generated-project/errors/error-handling-pattern.md`.

- [ ] Exactly ONE `ErrorReason.java` at
      `backend/service/src/main/java/<base>/service/common/error/`:
      ```bash
      find backend -name 'ErrorReason.java' -type f   # expected: 1 line
      ```
- [ ] NO per-domain enums or legacy files:
      ```bash
      find backend -name '*ErrorReason.java' -type f | grep -v 'common/error/ErrorReason.java'
      find backend -name '*ErrorCode*.java' -o -name 'CommonErrorCodes.java'
      ```
- [ ] NO `AppErrorReason` interface (prior scaffold; removed):
      ```bash
      grep -rln 'interface AppErrorReason' backend
      ```
- [ ] NO `throw new ResponseStatusException` / `IllegalStateException` /
      `IllegalArgumentException` / `RuntimeException` in service/controller:
      ```bash
      grep -rEn 'throw new (ResponseStatus|IllegalState|IllegalArgument|Runtime)Exception' \
        backend/service backend/application
      ```
- [ ] Single `@RestControllerAdvice` (`GlobalExceptionHandler`) in `application/`
      maps every `AppException` → `ApiErrorV1`.
- [ ] No raw `Map<String,Object>` error bodies.

## Thin controllers (hard reject)

Controllers do ONLY: principal extraction → one service call → mapper → return.
Full rule in backend SKILL.

- [ ] No `*Repository` field in any controller:
      ```bash
      grep -rEn 'private final.*Repository' backend/application/src/main/java/**/controllers/
      ```
      Expected: empty.
- [ ] No `if`/`switch`/`for`/`while`/`try`/`catch` in controller method bodies:
      ```bash
      grep -rEn '^\s+(if|switch|for|while|try|catch)\b' backend/application/src/main/java/**/controllers/*.java
      ```
      Expected: empty (review false positives).
- [ ] No manual DTO construction:
      ```bash
      grep -rEn 'new .*V1\(\);' backend/application/src/main/java/**/controllers/
      ```
      Expected: empty. All DTO construction goes through `*ApiMapper`.
- [ ] Two MapStruct mappers per resource exist: `Entity ↔ ServiceRecord` in `service/`,
      `ServiceRecord ↔ ApiDto` in `application/`.
- [ ] Entities never appear as parameters or return types outside `service/`.

## Module isolation (architectural — hard reject)

- [ ] `backend/service/pom.xml` does NOT declare spring-security / oauth2 / web / servlet-api:
      ```bash
      grep -E 'spring-(security|boot-starter-(security|oauth2|web))|servlet-api' backend/service/pom.xml
      ```
      Expected: empty. Service is business orchestration — it does not know about HTTP/JWT.
- [ ] `backend/service/` source contains no `SecurityContextHolder`, `Authentication`,
      `Jwt`, or `@AuthenticationPrincipal`. Service methods that need the caller take an
      `AppUser` parameter; the controller does the JWT → AppUser conversion.
- [ ] `backend/external-services/pom.xml` declares no internal-module deps:
      ```bash
      grep -E '<artifactId>(domain|service|application|db)</artifactId>' backend/external-services/pom.xml
      ```
      Expected: empty. external-services is a true leaf.
- [ ] `backend/domain/pom.xml` does NOT depend on `service` / `application` / `external-services` / `db`.
- [ ] AppException family lives at `service/<base>/service/common/error/`, not `domain/common/error/`:
      ```bash
      find backend -path '*/domain/common/error/*' -name '*.java'
      ```
      Expected: empty.
- [ ] `LogUsage.java` lives at `service/<base>/service/common/observability/LogUsage.java`,
      not `application/observability/usage/`:
      ```bash
      find backend -name LogUsage.java
      ```
      Expected: one line, under `service/.../service/common/observability/`.

## Package layout (hard reject)

Plural = collections, singular = namespaces. Agent defaults to singular →
silently fragments codebase. Reject.

- [ ] Each domain aggregate in `service/` has `services/`, `services/impl/`, `mappers/`, `models/`:
      ```bash
      for d in backend/service/src/main/java/*/service/*/ ; do
        for sub in services services/impl mappers models; do
          [ -d "$d$sub" ] || echo "MISSING: $d$sub"
        done
      done | grep -v common
      ```
      Expected: no MISSING lines.
- [ ] No singular versions (`service/`, `mapper/`, `model/`, `entity/`, `repository/`, `controller/`) anywhere:
      ```bash
      find backend -type d \( -name 'service' -path '*/sample/service' \
          -o -name 'mapper' -o -name 'model' \
          -o -name 'entity' -o -name 'repository' \
          -o -name 'controller' \) | grep -v 'src/main/java/[^/]*$'
      ```
      Expected: empty.
- [ ] Each `domain/` aggregate has `entities/` + `repositories/`:
      ```bash
      for d in backend/domain/src/main/java/*/domain/*/ ; do
        [ -d "${d}entities" ]    || echo "MISSING entities/ under $d"
        [ -d "${d}repositories" ] || echo "MISSING repositories/ under $d"
      done
      ```
      Expected: no MISSING lines.

## Build flags (hard reject)

Past Agent runs typed `-Dcheckstyle.skip=true` and forgot to remove it.

- [ ] `.github/workflows/ci.yml` has no `-Dcheckstyle.skip`, `-Djacoco.skip`,
      `-Dmaven.test.skip`, `-Dopenapi.skip`.
- [ ] No Makefile / script invokes Maven with these flags.
- [ ] README doesn't recommend skipping gates:
      ```bash
      grep -rE 'checkstyle\.skip|jacoco\.skip|maven\.test\.skip|openapi\.skip' .
      # matches in templates/ .agents/ are rule-statements, not violations
      ```

## Database

- [ ] No `CREATE TYPE … AS ENUM` in changelogs. Dictionary data lives in
      `<thing>_status` / `<thing>_kind` tables with `BIGINT id` + `TEXT code` + `TEXT name` + FK.
- [ ] IDs are `BIGINT`, strings are `TEXT`, no `VARCHAR`.

## Non-publish conditions (any one blocks)

- Source contains tokens / passwords / private keys / service-account JSON.
- Production credentials required to run the local demo.
- No working auth path when SSO keys are absent.
- App relies on undocumented manual setup.
- Backend is not Java + Spring Boot + Maven (see Stack lock check above).
