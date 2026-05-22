# Near-Production Project Structure

Generated full-stack projects must be structured for handoff, not as throwaway demos.

## Root

Required root files:

- `README.md`
- `.env.example`
- `.gitignore`
- `Dockerfile`
- `docker-compose.yml`
- `.github/workflows/ci.yml`

When Java backend exists:

- root `pom.xml` with `<packaging>pom</packaging>`
- root `lombok.config`
- root `config/check_style_config.xml`
- root `config/check_style_suppressions.xml`

## Backend Layout

Use Maven parent + modules. Keep generated, application, business, persistence and integration code separated.

Recommended Java modules:

- `backend/application`: Spring Boot entrypoint, REST controllers, generated API implementations, security, request mapping
- `backend/service`: business use cases, validation, orchestration
- `backend/domain`: JPA entities, repositories, domain enums, domain exceptions
- `backend/db`: Liquibase changelogs and DB seed/demo data
- `backend/external-services`: BigQuery and external API adapters
- `backend/common`: shared errors, paging, logging helpers, utility classes
- `backend/application/src/main/java/.../observability`: usage logging configuration and request/action logging adapters

Rules:

- controllers implement generated OpenAPI interfaces
- controllers stay thin
- services own business decisions
- repositories and entities stay out of REST API contracts
- DB schema changes go through Liquibase
- usage logging follows `templates/generated-project/observability/usage-logging-bigquery-rules.md`

## Frontend Layout

Use feature-first React structure inspired by the Bulletproof React architecture.

Recommended structure:

- `frontend/src/app`: app providers, router, global error boundary, app shell
- `frontend/src/pages`: route-level composition only
- `frontend/src/features`: feature modules with UI, hooks and feature-local API orchestration
- `frontend/src/entities`: reusable domain UI/models when shared across features
- `frontend/src/shared/api`: generated OpenAPI types, API client, auth-aware fetcher
- `frontend/src/shared/ui`: reusable design-system-like components
- `frontend/src/shared/lib`: framework-agnostic helpers
- `frontend/src/shared/config`: runtime config reader and validation

Rules:

- no backend calls directly from page components
- server state is handled through TanStack Query
- generated OpenAPI types are the source of truth for API payloads
- frontend never reaches DB, BigQuery, service-account keys or secrets

## Local Environment

Local environment must be reproducible through Docker Compose:

- `docker compose --profile local config`
- `docker compose --profile local up --build -d`
- `docker compose --profile local down -v`

No undocumented manual infrastructure is allowed for a local demo.
