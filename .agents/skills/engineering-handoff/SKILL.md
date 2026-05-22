---
name: engineering-handoff
description: Final handoff checklist for moving MVP code to long-term engineering ownership.
argument-hint: "[project-or-branch]"
user-invocable: true
---

# Engineering Handoff

Use when MVP is accepted and must be transferred for long-term ownership.

## Handoff Package

Prepare:
- README with purpose, owner, run/deploy steps, known limitations
- `.env.example` with safe placeholders
- architecture summary
- list of mocked components and required replacements
- data source list (including BigQuery usage if any)
- usage logging configuration and validation notes

## Mandatory Java Backend Files

- `Dockerfile`
- `docker-compose.yml`
- `.github/workflows/ci.yml`
- root `pom.xml`
- root `lombok.config`
- `config/check_style_config.xml`
- `config/check_style_suppressions.xml`
- `README.md`
- `.env.example`
- OpenAPI contract files
- `src/main/resources/application.yml`
- `src/main/resources/application-local.yml`
- `src/main/resources/logback-spring.xml`
- `src/main/resources/ehcache.xml` when L2 cache is used

## Required Quality Checks

- frontend build passes
- backend build passes
- tests pass
- JaCoCo 80% gate exists
- Checkstyle config is root-based
- `git-commit-id-maven-plugin` configured
- OpenAPI contract exists and matches implementation
- OpenAPI generator plugin exists and follows canonical template settings
- React frontend generates API types from the backend OpenAPI YAML when frontend exists
- docker-compose creates PostgreSQL automatically when persistence is used
- backend logs are JSON structured
- Logbook HTTP request/response body logging is configured with masking
- usage logging is implemented through BigQuery with local/dev PostgreSQL fallback
- PostgreSQL types follow BIGINT/TEXT policy

## Auth and Data Checks

Confirm dual-mode auth:
- real Google SSO integration path exists (Clerk preferred or project-standard OIDC)
- mock local fallback works when keys are missing
- mode controlled by config/env only
- backend JWT validation exists for protected endpoints (JWKS signature + `iss`/`aud`/`exp`/`nbf`)
- `401`/`403` behavior is documented and tested

Confirm BigQuery policy:
- backend integration only
- keys/config via properties/env
- no credentials in source control
- `.env.example` contains placeholders for auth and BigQuery keys when those features are enabled

Confirm usage logging:
- `BQ_USAGE_CREDENTIALS_JSON`, `BQ_USAGE_TABLE`, `USAGE_LOG_SERVICE_NAME`, `USAGE_LOG_ENVIRONMENT`, `USAGE_LOGGING_ENABLED` are documented
- service account JSON is not committed
- user action success/error paths are logged

## Required Dry Run

Run or document:

```bash
docker compose --profile local config
docker compose --profile local up --build -d
curl -f http://localhost:8080/<app-context-path>/actuator/health
curl -f http://localhost:8080/<app-context-path>/actuator/prometheus
docker compose --profile local down -v
```

If execution is not possible, document exact reason and commands in README.

## Migration Notes for Engineering

Document what to replace:
- demo/mock auth defaults -> production SSO configuration values
- demo datasets -> approved production data APIs/pipelines
- Replit secrets -> company secret manager
- MVP deployment -> target infrastructure
