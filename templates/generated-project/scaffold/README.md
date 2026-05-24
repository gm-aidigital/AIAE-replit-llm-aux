# Scaffold

This directory contains **starter files** for generated projects. Agent
should copy these into the generated app and edit placeholders, rather than
writing them from scratch. Filenames marked `*.template` need their
placeholders (`PACKAGE_REPLACE_ME`, `<app-name>`, `<your-app-name>`, etc.)
substituted before use.

## Layout

```
.env.example                             # env-var placeholders
Dockerfile                               # local-dev only
docker-compose.yml                       # local-dev only, profile: local
lombok.config                            # root config
pom.xml                                  # parent POM, <packaging>pom</packaging>
README.md.template                       # generated-project README skeleton
backend/
  application/
    pom.xml                              # application module
    src/main/java/PACKAGE_REPLACE_ME/
      Application.java                   # @SpringBootApplication entrypoint
      config/
        ReplitDatabaseUrlPostProcessor.java      # profile=replit: parses DATABASE_URL
        SecurityConfig.java              # auth wiring stub
    src/main/resources/
      application.yml                    # base config
      application-replit.yml             # profile=replit overrides
      application-local.yml              # profile=local overrides
      logback-spring.xml                 # JSON logs to stdout
      static/api/v1/specs/openapi.yaml   # OpenAPI source of truth (stub)
  db/
    src/main/resources/db/changelog/
      db.changelog-master.xml            # Liquibase master
      changes/0001-usage-events.xml      # usage_events table
frontend/
  package.json                           # openapi-typescript + openapi-fetch + TanStack Query
  tsconfig.json
  vite.config.ts                         # proxies /api/* → backend:5000
  index.html
  src/
    main.tsx
    App.tsx
    shared/
      api/
        client.ts                        # auth-aware openapi-fetch wrapper
        generated/.gitkeep               # openapi-typescript output goes here
      config/runtime.ts                  # reads VITE_* vars + validates
```

## How Agent should use these files

1. Pick the files relevant to the chosen stack (full-stack → copy everything;
   backend-only demo → skip `frontend/`).
2. Replace placeholders:
   - `PACKAGE_REPLACE_ME` → the actual base Java package (e.g. `com.company.salesdashboard`).
   - `/some-path-by-app-name` / `<app-name>` → the real context-path (e.g. `/sales-dashboard`).
   - Bump dependency versions to the latest matching the canonical baseline.
3. Wire references between files (parent POM → modules, frontend OpenAPI
   script → backend spec path).
4. Run the safety review (`/agents/skills/mvp-safety-review/SKILL.md`).

## Why scaffolds exist

The template's `instructions.md` requires every generated project to have
a specific set of mandatory files. Without copyable scaffolds, Agent would
have to invent each from prose — causing drift between generations. These
files are the *current* canonical shape; canonical RULES (in
`templates/generated-project/*.md`) explain why they look this way.
