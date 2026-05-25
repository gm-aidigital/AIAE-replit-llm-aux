# Translating Replit's DATABASE_URL for the Java backend

Replit's managed SQL Database injects only:

```
DATABASE_URL=postgresql://<user>:<password>@<host>:<port>/<db>[?sslmode=...]
```

The `sslmode` parameter is tier-specific: Replit's production-grade tier
sets `sslmode=require`; the Helium development tier omits it (no TLS).
The processor reads sslmode from the URL and applies it as-is — do NOT
force `require` in app config (used to be the default; broke Helium).

(`PGHOST`, `PGUSER`, `PGPASSWORD`, `PGDATABASE`, `PGPORT` are legacy Neon-only
and NOT injected by current Replit SQL Database.)

Spring Boot expects:
```
spring.datasource.url      = jdbc:postgresql://host:port/db
spring.datasource.username = user
spring.datasource.password = pass
```

Spring can't decompose a libpq URL. We use a Spring Boot
`EnvironmentPostProcessor` that runs before context build, parses
`DATABASE_URL`, and adds the standard properties. Spring Boot's auto-config
then builds HikariDataSource as if you set them yourself — no custom
`@Bean`, no override.

## Canonical: `ReplitDatabaseUrlPostProcessor`

Copy `ReplitDatabaseUrlPostProcessor.java` (next to this file) into
`backend/application/src/main/java/<your.base.package>/config/`; adjust package.

The class:
- implements `EnvironmentPostProcessor`,
- early-returns when `DATABASE_URL` is absent (gate on env-var presence, NOT
  profile — `getActiveProfiles()` is empty before profiles resolve),
- parses `DATABASE_URL` via `java.net.URI`,
- reads `sslmode` from the URL query string when present; if the URL omits it,
  the processor does not force TLS properties (`disable` produces `ssl=false`),
- adds `spring.datasource.url`/`username`/`password` + Hikari SSL props to a
  high-priority `MapPropertySource`,
- fails fast with a readable message on missing `DATABASE_URL`.

Register via:
```
backend/application/src/main/resources/META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor.imports
```
One line: FQN of the class.

`application-replit.yml` declares only Hikari pool size, port, usage-logging
defaults — no datasource block.

## Why not `@Bean DataSource` or a `prestart.sh`

- `@Bean DataSource` overrides Spring's auto-config; pool sizing moves out
  of YAML; tests must manage bean override; HikariCP construction is
  reimplemented from scratch.
- A `prestart.sh` parsing `DATABASE_URL` ran only in the Run workflow
  (deployments would fail to connect), required `set -euo pipefail`, was
  fragile around special chars in passwords, and YAML alone couldn't apply
  Hikari SSL data-source-properties cleanly.

`EnvironmentPostProcessor` runs in every environment and is testable.

## Local-dev profile

`application-local.yml` sets `spring.datasource.url`/`username`/`password`
for the docker-compose Postgres directly. Hikari pool can be `50`.
Post-processor is inactive (no `DATABASE_URL`).

## Tests

Run on default profile (no `replit`/`local`). Post-processor inactive;
tests use Testcontainers Postgres or embedded `application-test.yml`.
