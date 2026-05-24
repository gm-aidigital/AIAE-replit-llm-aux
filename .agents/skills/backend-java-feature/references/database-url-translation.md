# Translating Replit's DATABASE_URL for the Java backend

Replit's managed SQL Database injects only one env var:

```
DATABASE_URL=postgresql://<user>:<password>@<host>:<port>/<db>?sslmode=require
```

(`PGHOST`, `PGUSER`, `PGPASSWORD`, `PGDATABASE`, `PGPORT` are **legacy
Neon-only** and are NOT injected by Replit's current SQL Database. Do not
rely on them.)

Spring Boot's standard property model expects:
```
spring.datasource.url      = jdbc:postgresql://host:port/db
spring.datasource.username = user
spring.datasource.password = pass
```

Spring can't decompose a libpq URL on its own. So **we use a Spring Boot
`EnvironmentPostProcessor`** that runs before the application context is
built, parses `DATABASE_URL`, and adds the three standard properties to
the environment. After it runs, Spring Boot's auto-configuration builds
HikariDataSource exactly as if you had set the properties yourself.

This is the Spring-idiomatic shape: no custom `DataSource` `@Bean`, no
override of Spring's own construction, pool sizing stays in
`application-replit.yml`, and tests are unaffected (the post-processor
no-ops when `replit` profile is inactive).

## Canonical approach: `ReplitDatabaseUrlPostProcessor`

Copy `ReplitDatabaseUrlPostProcessor.java` (next to this file) into
`backend/application/src/main/java/<your.base.package>/config/`. Adjust the
package declaration.

The class:

- implements `org.springframework.boot.env.EnvironmentPostProcessor`,
- early-returns when profile `replit` is not active,
- parses `DATABASE_URL` via `java.net.URI`,
- adds `spring.datasource.url`, `username`, `password`, and Hikari SSL
  data-source-properties to a high-priority `MapPropertySource`,
- fails fast with a readable message when `DATABASE_URL` is missing.

Register it via:

```
backend/application/src/main/resources/META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor.imports
```

one line, the FQN of the class. (For Spring Boot 2.x and earlier, use
`META-INF/spring.factories` instead.)

`application-replit.yml` next to this file only declares Hikari pool size,
port, and a couple of usage-logging defaults — no datasource block.

## Why not a `@Configuration @Bean DataSource`?

A custom `@Bean DataSource` works, but it's heavier than necessary:
- it overrides Spring's auto-config rather than feeding it,
- pool size moves out of YAML into Java (or stays in YAML but is duplicated
  by the bean — error-prone),
- tests have to manage the bean override,
- you re-implement HikariCP construction from scratch.

`EnvironmentPostProcessor` is the standard Spring pattern for "translate
one env var into Spring properties at boot." It's exactly the shape we
need.

## Why not a prestart shell script

An earlier draft of this template documented a `prestart.sh` that parsed
`DATABASE_URL` into `DATABASE_JDBC_URL` / `DB_USERNAME` / `DB_PASSWORD` env
vars. We dropped that approach because:

- It only ran from the Run workflow, so deployments would fail to connect.
- It required `set -euo pipefail` plumbing and was fragile around special
  characters in passwords.
- Spring's substitution still couldn't apply the SSL data-source-properties
  cleanly from YAML alone.

The Java post-processor is the same logic, runs in every environment, and is
testable.

## Local-dev profile

`application-local.yml` configures `spring.datasource.url` /
`spring.datasource.username` / `spring.datasource.password` for the
docker-compose Postgres directly. HikariCP pool can be the full baseline
(50). The post-processor is inactive on this profile.

## Tests

Tests run on the default Spring profile (no `replit`/`local`). The
post-processor is inactive, and tests get an in-memory Testcontainers
Postgres or the embedded `application-test.yml` config.
