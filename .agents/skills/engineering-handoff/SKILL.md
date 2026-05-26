---
name: engineering-handoff
description: Final handoff checklist when transferring an accepted MVP to an engineering team for long-term ownership. Use when the user says the project is being shipped to engineering, handed off, or moved out of the Replit MVP phase. Builds on top of mvp-safety-review and only adds migration-to-production delta.
metadata:
  user-invocable: "true"
---

# Engineering Handoff

Use when the MVP is accepted and must move to long-term engineering ownership.
Runs *after* `mvp-safety-review`; does not duplicate its checks.

## Prerequisite

Run `.agents/skills/mvp-safety-review/SKILL.md` first. All of its checks must
pass before this skill applies.

## Handoff package

- [ ] README: purpose, owner, run/deploy steps for both Replit and local-dev,
      API overview, Swagger/OpenAPI links, known limitations.
- [ ] `.env.example` with safe placeholders for every consumed variable.
- [ ] Architecture summary (one page).
- [ ] List of mocked components and what must replace each one.
- [ ] Data source inventory (incl. `usage_events` table and any external APIs).
- [ ] Usage logging configuration and validation notes.

## Build / quality (Phase 3 — strict)

Canonical: `templates/generated-project/testing/testing-policy.md`.

- [ ] All Maven module POMs build clean: `mvn -f backend/pom.xml -Phandoff -T 1C verify`.
- [ ] `-Phandoff` activates the `0.80` JaCoCo line-coverage gate; the suite
      must clear it.
- [ ] Integration tests with Testcontainers Postgres present and green
      (`*IT.java`, run via Failsafe).
- [ ] Frontend builds clean (`npm run check:api && npm run build`).
- [ ] Checkstyle gate enforced.
- [ ] `git-commit-id-maven-plugin` present in all module POMs.
- [ ] OpenAPI generator plugin configured per canonical rules.
- [ ] PostgreSQL types follow `BIGINT` / `TEXT` policy.
- [ ] No `<excludes><exclude>**</exclude></excludes>` or coverage-disabling
      tricks were added just to clear the 0.80 gate.

## Local-dev dry run (must succeed on a clean machine)

```bash
docker compose --profile local config
docker compose --profile local up --build -d
curl -f http://localhost:8080/<app-context-path>/actuator/health
curl -f http://localhost:8080/<app-context-path>/actuator/prometheus
docker compose --profile local down -v
```

If the dry run cannot run in the current environment, document the reason and
exact commands in README.

## Post-acceptance cleanup (after engineering accepts the codebase)

Once the suite reliably clears `0.80` and engineering owns the project,
the phased-testing plumbing becomes dead weight. Final cleanup commit:

- [ ] In `backend/pom.xml`, set
      `<jacoco.line.coverage>0.80</jacoco.line.coverage>` as the default
      (was `0.00` for MVP / `Phase 1-2`).
- [ ] Delete the `<profiles><profile><id>handoff</id>...</profile></profiles>`
      block — redundant once the default is already `0.80`.
- [ ] Update README to drop `-Phandoff` references; the build command
      becomes `mvn -f backend/pom.xml verify`.
- [ ] Engineering CI workflow drops `-Phandoff` flags.
- [ ] (Optional) Remove `templates/generated-project/testing/testing-policy.md`
      from the project — its rules no longer apply once company standards
      take over.

## Migration notes for engineering

Document the replacement plan for each item:

| From (MVP) | To (production) |
|---|---|
| Mock auth defaults | Real Clerk/Google SSO config values |
| Demo datasets / fixtures | Approved production data APIs/pipelines |
| Replit Secrets | Company secret manager |
| Replit-native PostgreSQL module | Managed PostgreSQL (RDS / CloudSQL / equivalent) |
| Replit Reserved VM (`deploymentTarget = "gce"`) | Target infrastructure (k8s / ECS / managed app platform) |
| `usage_events` in app DB | Production analytics sink (e.g. dedicated DB or BigQuery) if cross-service aggregation is needed |
