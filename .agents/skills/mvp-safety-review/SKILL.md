---
name: mvp-safety-review
description: Safety and release-readiness checklist for MVP projects before publish/share.
argument-hint: "[project-or-branch]"
user-invocable: true
---

# MVP Safety Review

Run this review before publishing, demoing, or handing off an MVP.

## Security and Data Gates

Confirm:
- no real secrets committed
- no production datasets embedded
- no production service-account files in repo
- frontend does not access DB/BigQuery/secrets directly
- `.env.example` exists and has placeholders only
- `.env.example` includes auth and BigQuery placeholders when those features are present
- README lists data sources and limitations
- OpenAPI static spec exists when backend REST API exists
- frontend API types are generated from the backend OpenAPI YAML when frontend exists
- service account JSON keys are not committed

## Auth Gate (Mandatory Dual-Mode)

Confirm auth behavior is config-driven:
- real Google SSO path exists (Clerk preferred or project-standard OIDC)
- fallback mock local user mode exists when keys are missing
- login UI exists and remains usable in fallback mode
- when keys are provided, real SSO can be enabled without code rewrite
- backend validates JWT for protected endpoints (signature + claims)
- missing/invalid token returns `401`
- unauthorized token returns `403`

Reject publish if:
- auth secrets are hardcoded
- real SSO is claimed but only mock exists
- fallback mode does not work without keys
- backend trusts frontend login state without validating JWT

## BigQuery Gate

If BigQuery is required by the feature:
- dependency exists in backend only
- credentials/config are properties/env based
- no raw credentials in code/repo
- frontend accesses BigQuery data only through backend APIs

## Backend Quality Gates

Confirm:
- OpenAPI contract updated for API changes
- OpenAPI generator plugin is configured when backend REST API exists
- frontend uses `openapi-typescript`, `openapi-fetch`, and TanStack Query when React frontend exists
- Dockerfile and docker-compose local profile exist
- docker-compose includes PostgreSQL when persistence is used
- dry run steps are documented or executed
- Actuator health and Prometheus endpoints exist
- Checkstyle and JaCoCo gates exist
- logs are structured JSON
- Logbook logs request/response bodies with masking and endpoint exclusions
- usage logging writes to BigQuery with credentials, falls back to local PostgreSQL in local/dev, and never breaks user requests

## Non-Publish Conditions

Do not publish if any is true:
- source contains tokens/passwords/private keys
- source contains service account JSON keys
- production credentials are required to run local demo
- no fallback auth path for key-missing mode
- app relies on undocumented manual setup
