# Project Context for Replit Agent

This is a company MVP template.
This repository is a Replit Custom Template configuration repository.
It stores instructions and skills for agent behavior.
Mandatory backend artifacts (for example `pom.xml`, `Dockerfile`, `docker-compose.yml`) are required in generated project repositories, not in this template repository.

Agent should keep this file updated as the project evolves. Do not remove references to company instructions and skills.

Always follow:
1. `custom_instruction/instructions.md`
2. `.agents/skills/backend-java-feature/SKILL.md`
3. `.agents/skills/openapi-contract-first/SKILL.md`
4. `.agents/skills/frontend-react-feature/SKILL.md`
5. `.agents/skills/mvp-safety-review/SKILL.md`
6. `.agents/skills/engineering-handoff/SKILL.md`
7. `templates/generated-project/.github/workflows/ci.yml` as canonical CI template for generated Java backend projects
8. `templates/generated-project/pom-snippets/git-commit-id-maven-plugin.xml` as canonical plugin snippet
9. `templates/generated-project/pom-snippets/openapi-generator-maven-plugin.xml` as canonical OpenAPI generator snippet
10. `templates/generated-project/openapi/openapi-review-checklist.md` as canonical OpenAPI checklist
11. `templates/generated-project/openapi/canonical-openapi-rules.md` as canonical OpenAPI structure/generator rules
12. `templates/generated-project/auth/google-sso-clerk-blueprint.md` as canonical auth blueprint
13. `templates/generated-project/frontend/canonical-react-frontend-rules.md` as canonical React frontend rules
14. `templates/generated-project/structure/near-production-project-structure.md` as canonical generated-project structure
15. `templates/generated-project/generation/token-efficient-generation-rules.md` as token-efficient generation policy
16. `templates/generated-project/observability/usage-logging-bigquery-rules.md` as canonical usage logging policy
17. `templates/generated-project/observability/logbook-http-logging-rules.md` as canonical HTTP request/response logging policy

Default MVP assumptions:
- Build a demo that can run and publish in Replit.
- Use near-production project structure for generated full-stack/backend apps.
- Use dual-mode auth: real Google SSO path + mock local-user fallback when keys are missing.
- Mock BigQuery data unless approved backend BigQuery integration is required.
- Never use production secrets or production data.
- Frontend must not access DB/BigQuery/secrets directly.
- Backend services must include usage logging: BigQuery when credentials exist, local PostgreSQL fallback in local/dev when they do not.

Mandatory Java backend baseline:
- Java 25
- Spring Boot 4.x
- Maven parent POM with dependencyManagement
- Lombok with root `lombok.config`
- Checkstyle under root `config`
- JaCoCo 80% line coverage
- git-commit-id maven plugin
- OpenAPI generator snippet from template repository
- frontend OpenAPI client generation from the backend YAML
- JSON structured logs to stdout
- Logbook HTTP request/response JSON logging with bodies and masking
- BigQuery usage logging for meaningful user actions
- Dockerfile
- docker-compose with `local` profile
- GitHub Actions CI
- dry run of local profile

Preferred architecture:
- React + TypeScript frontend.
- For backend/full-stack apps, prefer the canonical near-production structure over ad hoc flat layouts.
- If Java/Spring Boot is requested or a backend is needed for handoff, use Java 25 + Spring Boot 4.x + Maven.
- Keep frontend/backend separated.
- Use typed API calls generated from the backend OpenAPI YAML.
- Store env var names in `.env.example`.

Current project decisions:
- No decisions yet.

Template repository organization:
- `custom_instruction/instructions.md`: static authoritative company rules.
- `replit.md`: concise living entrypoint that points Agent to required rules.
- `.agents/skills/*/SKILL.md`: specialized workflows Agent uses when relevant.
- `templates/generated-project/*`: canonical files, snippets and blueprints copied or followed in generated app repositories.
