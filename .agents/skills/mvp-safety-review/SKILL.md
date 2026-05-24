---
name: mvp-safety-review
description: Safety and release-readiness checklist for a generated MVP before sharing, publishing on Replit, or recording a demo. Use when the user says the project is ready, is about to share/publish, or is preparing a stakeholder demo.
metadata:
  user-invocable: "true"
---

# MVP Safety Review

Run before any publish / share / demo of a generated MVP. Pure gate — does
not change code.

## Security and secret hygiene

- [ ] No real secrets committed (`grep` for `BEGIN PRIVATE KEY`, `"private_key"`, `"type": "service_account"`).
- [ ] No production datasets embedded.
- [ ] No production service-account files in repo.
- [ ] `.env.example` exists with placeholders only.
- [ ] `.env.example` covers auth and usage-logging placeholders.
- [ ] README lists data sources, env vars, run/deploy steps, MVP limitations.

## Auth (dual-mode)

Canonical contract: `templates/generated-project/auth/google-sso-clerk-blueprint.md`.

- [ ] `AUTH_MODE=auto|sso|mock` honored; default `auto` works without keys.
- [ ] Login UI usable in mock mode.
- [ ] Same flow switches to real SSO when Clerk keys present.
- [ ] Backend validates Bearer JWT (signature via JWKS; `iss`, `aud`, `exp`, `nbf` when present).
- [ ] `401` for missing/invalid token; `403` for unauthorized.
- [ ] No hardcoded auth secrets anywhere.

Reject publish if any auth claim is unsupported (e.g., README claims SSO but
only mock implementation exists, or backend trusts frontend state without
validating JWT).

## Usage logging

Canonical contract: `templates/generated-project/observability/usage-logging-rules.md`.

- [ ] `USAGE_LOGGING_ENABLED`, `USAGE_LOG_SERVICE_NAME`, `USAGE_LOG_ENVIRONMENT`
      are set in `.env.example`.
- [ ] Liquibase changelog for `usage_events` exists and runs at startup.
- [ ] `spring-boot-starter-aop` dependency is present (enables `UsageLoggingAspect`).
- [ ] `UsageLoggingAspect` + `@LogUsage` annotation files exist in the project.
- [ ] **Every `*ServiceImpl` public method that represents a user action carries `@LogUsage(action = "...")`**.
      Spot-check: count methods on each `*ServiceImpl`, count `@LogUsage`
      annotations — they should be approximately equal. Endpoints without
      `@LogUsage` produce no usage events and silently break the dashboard.
- [ ] Business code contains **no** direct `usageLogger.record(...)` calls.
      All recording flows through the aspect.
- [ ] `PostgresUsageLogger` is bound when enabled; `NoOpUsageLogger` otherwise.
- [ ] Logger never blocks/fails user requests; insert errors are logged and swallowed.
- [ ] Frontend never reaches `usage_events` directly.

## Backend / frontend quality

- [ ] OpenAPI contract updated for any API change; spec at
      `src/main/resources/static/api/v1/specs/openapi.yaml`.
- [ ] `openapi-generator-maven-plugin` configured when REST API exists.
- [ ] Frontend uses `openapi-typescript` + `openapi-fetch` + TanStack Query.
- [ ] Structured JSON logs to stdout.
- [ ] Actuator `health` and `prometheus` reachable through context-path.
- [ ] Checkstyle gate wired.
- [ ] JaCoCo plugin wired (default gate is `0.00` in MVP — see Tests below).

## Tests (Phase 2 gate — required before publish)

Canonical: `templates/generated-project/testing/testing-policy.md`.

Publish is **blocked** if the project is still in Phase 1 — i.e. zero
tests exist while the app already runs. Phase 1 → Phase 2 transition is
triggered by:
- `mvn -f backend/pom.xml -DskipTests package` succeeds.
- Replit Run boots cleanly; `/api/v1/auth/me` returns 200 with a mock JWT.
- At least one feature endpoint reaches the DB end-to-end.

Once those are true (i.e. now, at safety review time), Phase 2 demands:

- [ ] At least one `*Test.java` exists for every `@Service` public method
      (happy-path + main `AppException` branch).
- [ ] At least one `@WebMvcTest` per controller covering 2xx, 401, 403.
- [ ] At least one Liquibase smoke test (`@DataJpaTest` that runs master changelog).
- [ ] `mvn -f backend/pom.xml verify` passes locally (default gate at `0.00`
      or wherever Phase 2 has ratcheted it — never increases the threshold
      beyond what the suite currently delivers).
- [ ] No commit removed existing tests just to make CI green.

Phase 3 (`mvn -Phandoff verify`, `0.80`) is NOT required at MVP publish —
it's the engineering-handoff bar.

## Replit vs local-dev runtime

- [ ] `.replit` + `replit.nix` present and the workspace runs.
- [ ] `.replit` `[env]` sets `SPRING_PROFILES_ACTIVE = "replit"`.
- [ ] `.replit` `[deployment]` sets `deploymentTarget = "gce"` (Reserved VM),
      not `"cloudrun"` / Autoscale, when persistence is used.
- [ ] App binds port `5000` and uses Replit's SQL Database via `DATABASE_URL`
      (NOT the legacy `PGHOST`/`PGUSER`/`PGPASSWORD`/`PGDATABASE` vars).
- [ ] `ReplitDatabaseUrlPostProcessor` (or equivalent) is the source of the
      `DataSource` bean on profile `replit`; YAML does not configure the
      datasource on that profile.
- [ ] HikariCP `maximum-pool-size` is `2–3` on the `replit` profile.
- [ ] **Local-dev Docker artifacts present** (handoff is the template default):
      - `backend/Dockerfile` (multi-stage Maven → JRE)
      - `frontend/Dockerfile` (Node → nginx static)
      - `frontend/nginx.conf` (SPA routing + `/api` proxy)
      - `docker-compose.yml` at project root with profile `local`,
        services `postgres` + `backend` + `frontend`, build contexts
        pointing at `./backend` and `./frontend`.
      - Local dry-run commands documented in README.

## Replit Deployment readiness (before clicking Deploy)

- [ ] Workspace Secrets are copied to the Deployment Secrets pane:
      `SPRING_PROFILES_ACTIVE`, `CLERK_*`, `AUTH_*`, `USAGE_LOG_*`,
      and any `DATABASE_URL` override. Workspace `[env]` does NOT propagate.
- [ ] Reserved VM target chosen (not Autoscale) when persistence is used.
- [ ] Build command (`mvn -DskipTests package` + frontend build) succeeds in CI.
- [ ] `application-replit.yml` correctly translates `DATABASE_URL` to JDBC
      with `sslmode=require` enforced.

## Auth alternative (informational)

For Replit-only demos that don't need to be exported to local-dev,
[Replit Auth](https://docs.replit.com/references/auth-and-identity/authentication)
is a zero-config alternative to Clerk. The template defaults to Clerk because
generated apps must also run locally, but the user can swap to Replit Auth
when handoff/export isn't a requirement.

## Template control plane is NOT tracked by git

Template-only files must stay in the Replit workspace but never enter
the company git repo. Verify with:

```bash
git ls-files .agents templates custom_instruction AGENTS.md replit.md 2>/dev/null
```

Expected output: empty. If anything prints, run
`bash templates/generated-project/scaffold/scripts/setup-project.sh` to
untrack them and **redo the commit**.

## Stack lock check (hard reject)

The repo must NOT contain any of:

- `main.py`, `requirements.txt`, `Pipfile`, `pyproject.toml`, `uv.lock`, `poetry.lock`, `manage.py`, `wsgi.py`, `asgi.py` (Python backend or Replit's auto-injected Python defaults)
- Root-level `package.json` declaring `express`/`fastify`/`koa`/`@nestjs/core`/`next` (Node backend)
- `go.mod`, `Gemfile`, `composer.json`, `Cargo.toml`, `mix.exs` (other backends)
- Backend code in any directory that isn't `backend/` Maven module
- `.replit` `modules` containing `python-*`
- `.replit` `[agent] integrations` containing `flask_*`, `django_*`, `fastapi_*`

If any of the above are present, either the template's stack lock was
violated by Agent, or Replit's default Python scaffolding survived
generation. Either way: delete the offending files/lines and regenerate
the backend from `templates/generated-project/scaffold/`.

## Parent pom location

- [ ] Parent `pom.xml` lives at `backend/pom.xml` — **not** at project root.
      Root only holds project-level files (README, docker-compose, .replit, etc).
- [ ] Module paths in `backend/pom.xml` are relative to `backend/` (e.g.
      `<module>application</module>`, not `<module>backend/application</module>`).
- [ ] Child module poms use `<relativePath>../pom.xml</relativePath>`.

## Error handling

Canonical contract: `templates/generated-project/errors/error-handling-pattern.md`.

- [ ] `AppException` + `AppErrorReason` interface + per-domain reason enums exist in `backend/common/error/`.
- [ ] `ValidationMessage` and `ValidationParameter` shape matches the canonical (code + formatted message + type + params).
- [ ] Single `@RestControllerAdvice` in `application/` converts all exceptions to the OpenAPI `ApiError` DTO.
- [ ] No `ResponseStatusException`, no raw `Map<String,Object>` error bodies.
- [ ] Services throw `AppException(reason, params)`; controllers never catch or wrap.

## Controllers and mappers

- [ ] Every controller method does: get principal → call ONE service method → MapStruct map → return.
- [ ] Zero `if`/`switch` on business state in controllers.
- [ ] Zero repository or `EntityManager` references in controllers.
- [ ] MapStruct mappers exist for `Entity ↔ ServiceRecord` (in `service/`) and `ServiceRecord ↔ ApiDto` (in `application/`).
- [ ] No handwritten `new SomeDto(); dto.setX(...)` chains for OpenAPI-generated DTOs.
- [ ] Entities never appear as method parameters or return types outside `service/`.

## Database

- [ ] No `CREATE TYPE … AS ENUM` anywhere in changelogs. Dictionary data lives in `<thing>_status` / `<thing>_kind` tables with `BIGINT id` + `TEXT code` + `TEXT name` + FK from owning table.
- [ ] IDs are `BIGINT`, strings are `TEXT`, no `VARCHAR`.

## Non-publish conditions (any one blocks)

- Source contains tokens / passwords / private keys / service-account JSON.
- Production credentials required to run the local demo.
- No working auth path when SSO keys are absent.
- App relies on undocumented manual setup.
- **Backend is not Java + Spring Boot + Maven** (see stack lock check above).
