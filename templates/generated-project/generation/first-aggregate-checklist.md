# First real aggregate — checklist

Run in order when landing the **first** domain feature (same commit).

1. **Clerk Secrets** — set `CLERK_*` and `AUTH_*` in Replit Secrets before Run.
2. `bash scripts/apply-package-name.sh <app-name-package>` if not done yet.
3. Read the sample aggregate under `backend/*/sample/` — copy its layer pattern.
4. OpenAPI: add endpoints to `backend/application/src/main/resources/api/v1/specs/openapi.yaml`.
5. `mvn -f backend/pom.xml -DskipTests compile` then `cd frontend && npm run generate:api`.
6. Implement generated `*Api` in `application/<aggregate>/controllers/` (≤6 lines each).
7. Add entity/repo (domain), record/mapper/service (service), Liquibase in `db/changelog/changes/`.
8. `bash scripts/strip-scaffold-samples.sh` — removes reference `sample/*` fixtures.
9. Fill `README.md` (purpose, API links, env vars, run steps).
10. Add tests: `*ControllerTest`, `*ServiceImplTest`, frontend flow test.
11. `bash scripts/local-verify.sh` before share/publish.

Forbidden: rename sample classes in place; leave `0002-sample-reference.xml` in master changelog.

Layer rules (on demand): `templates/generated-project/structure/near-production-project-structure.md`.
Token load order: `templates/generated-project/generation/token-efficient-generation-rules.md`.
