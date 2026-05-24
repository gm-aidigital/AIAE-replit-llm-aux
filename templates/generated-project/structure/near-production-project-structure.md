# Near-Production Project Structure

Generated full-stack projects are structured for engineering handoff, not as
throwaway demos. The same generated project must run in two environments:

1. **Replit workspace** (demo / preview / deploy) — uses Replit-native services:
   the `postgresql-16` SQL Database module injects a single `DATABASE_URL`
   env var, backend binds port 5000 mapped to external 80, secrets come from
   the Replit Secrets pane.
2. **Local developer machine** (handoff) — uses `docker-compose --profile local`
   to bring up PostgreSQL alongside the backend.

Docker and docker-compose exist *only* for the local-dev path. The Replit run
does not invoke Docker.

## Root layout

```
<project-root>/
├── README.md
├── .env.example
├── .gitignore                            # excludes template control plane + Python files
├── .replit                               # Replit workspace config (run, ports, modules)
├── replit.nix                            # Replit Nix package deps
├── docker-compose.yml                    # local-dev only; orchestrates backend + frontend + postgres
├── .github/workflows/ci.yml
├── backend/                              # ALL Java/Maven artifacts live here
│   ├── pom.xml                           # parent POM, <packaging>pom</packaging>
│   ├── Dockerfile                        # backend image (local-dev only)
│   ├── lombok.config
│   ├── config/                           # Checkstyle config referenced by parent pom
│   │   ├── checkstyle.xml
│   │   └── checkstyle-suppressions.xml
│   ├── application/                      # REST + security + GlobalExceptionHandler
│   ├── service/                          # Business logic + MapStruct Entity↔Record
│   ├── domain/                           # JPA entities + repos + AppException family
│   ├── db/                               # Liquibase changelogs only
│   └── external-services/                # External clients, no DB
└── frontend/                             # React + TypeScript + Vite
    ├── package.json
    ├── vite.config.ts
    ├── tsconfig.json
    ├── Dockerfile                        # frontend image (local-dev only)
    ├── nginx.conf                        # SPA routing + /api proxy for nginx runtime
    └── src/
```

**Hard rules** (the principle: every artifact lives in the folder of the
thing it serves; root is reserved for project-level concerns):

- Parent `pom.xml` lives at `backend/pom.xml`, NOT at project root.
- `lombok.config` lives at `backend/lombok.config`, NOT at root.
- `config/checkstyle*.xml` live at `backend/config/`, NOT at root.
- `Dockerfile` is split: `backend/Dockerfile` for the backend image,
  `frontend/Dockerfile` for the frontend image. No monolithic root Dockerfile.
- `docker-compose.yml` stays at root because it orchestrates both services
  plus PostgreSQL — that's the one Docker-related file that's project-level.

Build invocation: `mvn -f backend/pom.xml ...` from root, or
`cd backend && mvn ...`.

## Backend modules

Maven parent + modules. Keep generated, application, business, persistence and
integration code separated:

- `backend/application` — Spring Boot entrypoint, REST controllers, generated
  API implementations, security, request mapping, observability glue
- `backend/service` — business use cases, validation, orchestration
- `backend/domain` — JPA entities, repositories, domain enums/exceptions
- `backend/db` — Liquibase changelogs and seed/demo data
- `backend/external-services` — external API adapters (and any future remote sinks)
- `backend/common` — shared errors, paging, logging helpers

Rules:
- Controllers implement generated OpenAPI interfaces and stay thin.
- Services own business decisions.
- Entities and repositories do not appear in REST contracts.
- DB schema changes go through Liquibase.
- Usage logging follows
  `templates/generated-project/observability/usage-logging-rules.md`.

## Frontend layout

Feature-first React (Bulletproof React shape, see
`templates/generated-project/frontend/canonical-react-frontend-rules.md`):

```
frontend/src/app          # providers, router, global error boundary, shell
frontend/src/pages        # route-level composition only
frontend/src/features     # feature modules (UI + hooks + feature API)
frontend/src/entities     # reusable domain UI/models
frontend/src/shared/api   # generated OpenAPI types + auth-aware fetcher
frontend/src/shared/ui    # reusable design-system components
frontend/src/shared/lib   # framework-agnostic helpers
frontend/src/shared/config
```

## Runtime: Replit vs local-dev

| Aspect | Replit run | Local-dev run |
|---|---|---|
| PostgreSQL | Replit SQL Database via `DATABASE_URL` (libpq URL with `sslmode=require`); `ReplitDatabaseUrlPostProcessor` parses it into a `HikariDataSource` | `docker-compose --profile local` PostgreSQL service |
| Backend port | `5000` (Replit maps to external `80`) | `8080` (mapped to host) |
| Frontend dev | Vite on `5173` mapped 1:1 (workspace preview only) | Vite on `5173` |
| Frontend in **Deployment** | Spring Boot serves the built `frontend/dist/` from `src/main/resources/static/` (single public process on external 80). Vite is not running. | n/a |
| Secrets | Replit Secrets pane | `.env` (gitignored) |
| Build | Maven + Vite via `.replit` workflow (workspace) or single `mvn package` (deployment, frontend-maven-plugin runs npm) | `mvn package`, `npm run build`, or `docker compose up` |

Spring profile naming:
- Replit: profile `replit` (set via `.replit` `[env]`, reads `DATABASE_URL`, Hikari pool `2–3`).
- Local docker-compose: profile `local` (Hikari pool `50`).

Both profiles share `application.yml` as the base and override only what differs.
See `.agents/skills/backend-java-feature/references/database-url-translation.md`.

## Local-dev dry-run (handoff requirement only)

```bash
docker compose --profile local config
docker compose --profile local up --build -d
curl -f http://localhost:8080/<app-context-path>/actuator/health
curl -f http://localhost:8080/<app-context-path>/actuator/prometheus
docker compose --profile local down -v
```

Skip this on Replit — it does not provide Docker.
