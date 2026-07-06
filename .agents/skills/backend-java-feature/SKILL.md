---
name: backend-java-feature
description: Build the backend for ANY generated project in this template. The template's backend is ALWAYS Java 21 LTS + Spring Boot 3.x + Maven + PostgreSQL — never Python/Flask/Django, never Node/Express, never anything else. Use for every backend feature regardless of how simple the user prompt sounds.
metadata:
  user-invocable: "true"
---

# Backend Java Feature

Use for **every** backend change. Stack lock: `custom_instruction/instructions.md`.

## Baseline (non-negotiable)

Java 21 · Spring Boot 3.x · Maven multi-module · OpenAPI contract-first ·
Liquibase · PostgreSQL · package root `com.aidigital.<app-name-package>.*`.

Run `bash scripts/apply-package-name.sh <app-name-package>` once after copying
the scaffold. Replace every `PACKAGE_REPLACE_ME` — never use `com.example`.

HTML-only inputs still get the Java backend when usage logging, persistence,
auth, analytics, or multi-user review is required. Migrate UI code into
`frontend/`, keep the backend in `backend/`, and follow
`templates/generated-project/generation/html-only-project-migration.md`.

## Canonical references (do not duplicate)

| Topic | File |
|---|---|
| Structure | `templates/generated-project/structure/near-production-project-structure.md` |
| Entity-service boundary | `templates/generated-project/structure/entity-service-boundary-policy.md` |
| Architecture rules (full) | `references/backend-workflow-details.md` |
| Testing | `templates/generated-project/testing/testing-policy.md` |
| Backend test style | `templates/generated-project/testing/backend-test-style-rules.md` |
| OpenAPI | `templates/generated-project/openapi/canonical-openapi-rules.md` |
| Auth (Clerk SSO only) | `templates/generated-project/auth/google-sso-clerk-blueprint.md` |
| Usage logging | `templates/generated-project/observability/usage-logging-rules.md` |
| BigQuery query construction | `templates/generated-project/integrations/bigquery-query-rules.md` |
| Code patterns | `references/code-patterns.md` |
| Spring gotchas | `references/spring-boot-gotchas.md` |
| Hikari/JPA YAML | `references/hikari-jpa-baseline.yml` |
| Replit datasource | `references/database-url-translation.md` |
| Scaffold | `templates/generated-project/scaffold/` |

## Workflow

1. **Copy, don't regenerate** — scaffold POMs, YAML, `SecurityConfig`, `SpaFallbackController`.
2. **OpenAPI first** — update `openapi.yaml`, run review checklist, regenerate interfaces.
3. **Thin controllers** — implement generated `*Api`; ≤6 lines; no repositories; `@Transactional` on controller.
4. **Service interface + impl** — business logic in `*ServiceImpl`; inject interface, not impl.
5. **Two MapStruct mappers** — entity↔record in `service/`, record↔DTO in `application/`.
6. **Liquibase** — every `changeSet` has direct `preConditions`; verify gates reject missing preconditions.
7. **Errors** — single `ErrorReason` enum + `AppException` only.
8. **Auth** — Clerk JWT via `SecurityConfig`; resolve caller with `AppUserFactory`; no mock/Replit OIDC.
9. **Tests** — Phase 2 minimums per `testing-policy.md` before publish.

Full architecture, module matrix, port lock, build flags, gotcha table:
`references/backend-workflow-details.md`.

## Local verify

```bash
bash scripts/local-verify.sh
```

Skip docker-compose dry run on Replit when Docker is unavailable; document why in README.
