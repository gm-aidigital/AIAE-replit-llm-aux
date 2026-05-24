# Usage Logging Rules

Generated backend services emit one fire-and-forget event per meaningful user
action into the app's own PostgreSQL `usage_events` table. The manager runs
SQL against that table for usage estimation.

This is intentionally simple: one sink, one table, the same DB the app already
uses on Replit and local-dev.

## Default behavior

Generated projects ship with `USAGE_LOGGING_ENABLED=true` and a
`PostgresUsageLogger` bound. When the flag is `false`, or any required setting
is missing, `NoOpUsageLogger` is bound instead — the logger silently
short-circuits.

Local dev and tests must run without any usage-logging setup beyond the
default `usage_events` Liquibase changelog. The logger never blocks a user
request and never throws into the request flow.

## What is automatic vs. what is your job

**Automatic (the template wires it for you, do not reimplement):**
- `spring-boot-starter-aop` on the classpath → Spring Boot enables AspectJ
  proxying with no `@EnableAspectJAutoProxy` needed.
- `UsageLoggingAspect` is `@Aspect @Component` — component-scanned at startup.
- `@ConditionalOnProperty(name="app.usage-logging.enabled", matchIfMissing=true)`
  → aspect is **active by default**; set the flag to `false` to globally
  silence usage logging without touching code.
- The aspect handles success / exception / duration / user / correlationId
  uniformly. Business code never imports `UsageLogger`.

**Manual (your discipline — the aspect cannot guess it):**
- **You must put `@LogUsage(action = "<dotted.lowercase>")` on every
  service-impl public method that represents a user action.** Methods without
  the annotation are NOT logged — the aspect has no way to know which method
  is a "user action" and which is internal helper, so it only fires on the
  annotation.
- One `@LogUsage` per public method on every `*ServiceImpl` is the rule.
  Private helpers and internal-only methods stay un-annotated.
- The `action` value is a stable identifier for analytics — pick once,
  don't rename later (or you'll break the manager's dashboard queries).

Copy `SampleServiceImpl` in scaffold as the reference shape — every new
service follows the same `@Service` + `@RequiredArgsConstructor` + `@LogUsage`
on each public method pattern.

## Required env placeholders (in `.env.example`)

```
USAGE_LOGGING_ENABLED=true
USAGE_LOG_SERVICE_NAME=replit-mvp-template
USAGE_LOG_ENVIRONMENT=dev
```

That is the entire usage-logging configuration surface.

`USAGE_LOG_SERVICE_NAME` must be set to the actual service identifier before
publishing — `replit-mvp-template` is a recognisable placeholder. The
`PostgresUsageLogger` must reject placeholder values like `replit-mvp-template`
or empty strings at startup with a clear error message (and bind
`NoOpUsageLogger` instead).

## Event payload

| Field | Postgres type | Notes |
|---|---|---|
| `event_id` | `TEXT UNIQUE` | UUID v4 per event |
| `event_timestamp` | `TIMESTAMPTZ` | UTC, ISO 8601 |
| `service` | `TEXT` | stable lowercase hyphen-separated service name |
| `environment` | `TEXT` | `prod` / `staging` / `dev` |
| `event_type` | `TEXT` | `api_request` / `auth` / `error` / `custom` |
| `action` | `TEXT` | dotted lowercase, e.g. `forecast.create` |
| `user_id` | `TEXT` | when authenticated |
| `user_email` | `TEXT` | when authenticated |
| `status` | `TEXT` | `success` / `error` / HTTP code as string |
| `duration_ms` | `BIGINT` | operation latency |
| `attributes` | `JSONB` | sanitized structured payload |
| `error_message` | `TEXT` | short error description for failures |
| `client_ip` | `TEXT` | first `X-Forwarded-For` entry |
| `user_agent` | `TEXT` | request UA |

## Java backend contract — AOP-driven, single annotation

Usage logging is wired via a Spring AOP aspect — **no explicit
`logger.record(...)` calls scattered in business code**. Service / controller
methods you want to track get the `@LogUsage(action = "...")` annotation;
the `UsageLoggingAspect` does the rest.

Why aspect-based:
- Zero noise in service code. Business logic doesn't import UsageLogger.
- Single switch: remove `@LogUsage` (or disable the aspect via
  `app.usage-logging.enabled=false`) and logging disappears from the call
  path entirely.
- Both success and exception paths are captured automatically by one
  `try/catch/finally` in the aspect.

### Components

- `LogUsage` annotation (`@Retention(RUNTIME) @Target(METHOD)`) carrying
  `action` (mandatory, dotted lowercase like `employee.update`) and
  `eventType` (default `api_request`).
- `UsageLoggingAspect` — `@Aspect @Component @Order(LOWEST_PRECEDENCE - 100)`,
  bound to `@annotation(logUsage)`, wraps target with `try/catch/finally`.
- `UsageLoggingProperties` — `@ConfigurationProperties("app.usage-logging")`
  binding `enabled`, `service-name`, `environment`. NO magic strings.
- `UsageEvent` — immutable value type (`@Builder` Lombok record).
- `UsageEventEntity` — JPA `@Entity` on `usage_events` (lives in `domain`).
- `UsageEventRepository extends JpaRepository<UsageEventEntity, Long>`
  (lives in `domain`).
- `UsageLogger` — interface, single method `void record(UsageEvent event)`.
- `PostgresUsageLogger` — implementation, `@Async` + `@Transactional(REQUIRES_NEW)`
  so the DB write runs off the request thread in its own short transaction
  (uncoupled from the calling request's tx outcome).
- `NoOpUsageLogger` — bound when `enabled=false` or required props missing.

### Critical invariants (preserve on every edit)

1. **Self-invocation does NOT trigger the aspect.** Spring AOP uses proxies;
   `this.method()` inside the same bean bypasses the proxy. Always call
   the `@LogUsage`-annotated method from another bean (controller calling
   service is the canonical case).
2. **Aspect order is `LOWEST_PRECEDENCE - 100`** — strictly OUTER than
   Spring's `@Transactional` (`LOWEST_PRECEDENCE`). Usage event is logged
   AFTER the controller's transaction commits/rolls back; the `status`
   field reflects the final outcome.
3. **Logger is `@Async`** — the aspect hands off the event and returns
   immediately. Logger's own writer opens `REQUIRES_NEW` so it isn't
   bound to the caller's tx.
4. **No raw args** — the aspect deliberately ignores `joinPoint.getArgs()`.
   `action`/`eventType` from the annotation + auth context + duration +
   status is the entire payload. Anything sensitive is unreachable through
   this path.
5. **MDC correlationId** is captured via `MDC.get("correlationId")`. A
   request filter (Logbook or your own) must populate it earlier in the chain.
6. **`@Aspect` bean is `@ConditionalOnProperty(name="app.usage-logging.enabled", havingValue="true", matchIfMissing=true)`**
   — set `app.usage-logging.enabled=false` to disable globally without
   touching code.

### Usage at call sites

```java
@Service
@RequiredArgsConstructor
public class ResourceServiceImpl implements ResourceService {

    private final ResourceRepository repo;
    private final ResourceMapper mapper;

    @Override
    @LogUsage(action = "resource.update")
    public ResourceRecord update(Long id, ResourceUpdate update, AppUser caller) {
        ResourceEntity entity = repo.findById(id)
            .orElseThrow(() -> new AppException(ResourceErrorReason.E001, id));
        entity.apply(update);
        return mapper.toRecord(repo.save(entity));
    }
}
```

That's the entire integration. The aspect captures: action `resource.update`,
duration of the call, success or error (with truncated message), the
authenticated user from `SecurityContext`, the correlationId from MDC.

### Async + SecurityContext caveat

`SecurityContextHolder` uses `ThreadLocal` by default. If the
`@LogUsage`-annotated method is itself `@Async` (running on a different
thread), set the strategy at startup:

```java
SecurityContextHolder.setStrategyName(
    SecurityContextHolder.MODE_INHERITABLETHREADLOCAL);
```

Or use Spring's `DelegatingSecurityContextRunnable`. For synchronous
controller → service calls (the common path) this isn't needed.

**Why JPA, not JdbcTemplate.** The rest of the codebase uses JPA; mixing
JdbcTemplate for one table is a smell and a second persistence path to
maintain. JPA `save()` on a single insert is fast enough; the async wrapper
makes the cost off the request path either way.

Binding rule (Spring `@Configuration`, no magic strings):

```java
@Configuration
@EnableAsync
public class UsageLoggingConfig {

    @Bean
    @ConditionalOnProperty(name = "app.usage-logging.enabled", havingValue = "true")
    @ConditionalOnProperty(name = "app.usage-logging.service-name")
    UsageLogger postgresUsageLogger(UsageEventRepository repo, UsageLoggingProperties props) {
        return new PostgresUsageLogger(repo, props);
    }

    @Bean
    @ConditionalOnMissingBean(UsageLogger.class)
    UsageLogger noOpUsageLogger() {
        return new NoOpUsageLogger();
    }
}
```

Implementation rules for `PostgresUsageLogger`:
- `@Async("usageLoggingExecutor")` on the `record` method — runs off-thread.
- Inside the method body: `repo.save(toEntity(event))` wrapped in
  `try/catch (Exception)`. On failure: log via SLF4J, **swallow** — never
  rethrow into the request flow.
- Provide a `TaskExecutor` bean `usageLoggingExecutor` with a bounded queue
  (e.g. `ThreadPoolTaskExecutor` with `corePoolSize=1, queueCapacity=200`)
  + `RejectedExecutionHandler` that logs+drops.
- Never `@Transactional` on the logger — opens an outer-transaction trap.
  If the calling request is in a transaction and async picks up its rollback,
  events disappear. The repo's `save` opens its own short transaction.

## Liquibase changelog

Place under `backend/db/src/main/resources/db/changelog/`:

```xml
<changeSet id="usage-events-initial" author="agent">
  <createTable tableName="usage_events">
    <column name="id" type="BIGINT" autoIncrement="true">
      <constraints primaryKey="true" nullable="false"/>
    </column>
    <column name="event_id"         type="TEXT"        ><constraints nullable="false" unique="true"/></column>
    <column name="event_timestamp"  type="TIMESTAMPTZ" ><constraints nullable="false"/></column>
    <column name="service"          type="TEXT"        ><constraints nullable="false"/></column>
    <column name="environment"      type="TEXT"        ><constraints nullable="false"/></column>
    <column name="event_type"       type="TEXT"        ><constraints nullable="false"/></column>
    <column name="action"           type="TEXT"        ><constraints nullable="false"/></column>
    <column name="user_id"          type="TEXT"        />
    <column name="user_email"       type="TEXT"        />
    <column name="status"           type="TEXT"        ><constraints nullable="false"/></column>
    <column name="duration_ms"      type="BIGINT"      />
    <column name="attributes"       type="JSONB"       />
    <column name="error_message"    type="TEXT"        />
    <column name="client_ip"        type="TEXT"        />
    <column name="user_agent"       type="TEXT"        />
  </createTable>

  <createIndex tableName="usage_events" indexName="idx_usage_events_service_ts">
    <column name="service"/>
    <column name="event_timestamp" descending="true"/>
  </createIndex>
  <createIndex tableName="usage_events" indexName="idx_usage_events_action_ts">
    <column name="action"/>
    <column name="event_timestamp" descending="true"/>
  </createIndex>
</changeSet>
```

## Instrumentation

Log:
- successful user actions
- failed user actions (same `action` value as the success path)
- auth login/logout when applicable
- important custom domain actions

Skip:
- `/health`, `/actuator/*`
- `OPTIONS` requests
- browser prefetches, static assets
- internal readiness/liveness probes

## Sensitive data

Never write to `attributes`, `error_message`, or app logs:
credentials, API keys, JWTs, service account JSON, raw document contents,
full request/response bodies, third-party PII, private URLs.

Keep `attributes` small and structured. Strip raw user content over ~500 chars.

## Acceptance check

```sql
-- Recent activity for a service
SELECT event_timestamp, service, action, user_email, status, duration_ms
FROM usage_events
WHERE service = '<your-service-name>'
ORDER BY event_timestamp DESC
LIMIT 10;

-- Manager estimation: action counts over the last 7 days
SELECT action, COUNT(*) AS n
FROM usage_events
WHERE event_timestamp > now() - interval '7 days'
GROUP BY action
ORDER BY n DESC;
```

## Manager workflow: build a dashboard with Replit

Replit ships a built-in [Create a dashboard from data](https://docs.replit.com/build/dashboard)
workflow that turns any data source into a small visualization app. The
manager uses it on the **same DB** that the MVP writes to.

### Cross-app DB sharing on Replit (important)

Replit's `postgresql-16` module provisions a SQL Database **per app**. The
manager's dashboard app is a separate Replit app, so it does NOT automatically
share the MVP's DB. Two ways to give the dashboard access:

**Option 1 — manual connection string (simplest).**
1. In the MVP app's Replit Secrets pane, copy the value of `DATABASE_URL`.
2. Create a read-only role on the MVP's DB (`usage_reader`, see below) and
   build a connection string with its credentials instead of the default
   superuser.
3. In the dashboard app's Replit Secrets pane, paste that read-only URL as
   `DATABASE_URL` (or whatever var the dashboard app uses).

**Option 2 — shared external DB** (only when usage from multiple services
must aggregate). Provision a managed Postgres outside Replit (Neon, RDS,
etc.) and point both apps' `DATABASE_URL` at it. This is the trigger to
revisit the BigQuery migration note below.

### Manager flow (Option 1)

1. Manager opens Replit and picks "Create a dashboard from data".
2. Pastes the read-only `DATABASE_URL` into the dashboard app's Secrets pane
   when prompted for a data source.
3. Describes the desired view in plain language, e.g. *"build a dashboard
   from the `usage_events` table: action counts per day, top users by
   action volume, error-rate trend, filter by service and date range."*
4. Replit Agent generates the dashboard app. The manager bookmarks its
   preview URL.

The dashboard app is independent of the MVP — it only reads `usage_events`.
Service code stays free of dashboard UI.

### Read-only manager role

Run once against the MVP's DB (using the superuser `DATABASE_URL`):

```sql
CREATE ROLE usage_reader LOGIN PASSWORD '<strong-random>';
GRANT CONNECT ON DATABASE <db> TO usage_reader;
GRANT USAGE ON SCHEMA public TO usage_reader;
GRANT SELECT ON usage_events TO usage_reader;
```

Then build the dashboard's `DATABASE_URL` as:
```
postgresql://usage_reader:<password>@<host>:<port>/<db>?sslmode=require
```

## Future migration (only when needed)

If usage from multiple internal services later needs cross-service
aggregation, add a second `UsageLogger` implementation that writes to your
aggregation target (for example BigQuery) and bind it instead of
`PostgresUsageLogger`. The event payload above is intentionally portable, so
adding a sink is implementation work only — no contract or call-site changes.
