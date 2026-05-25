# Usage Logging Rules

One fire-and-forget event per meaningful user action → app's PostgreSQL
`usage_events` table. Manager queries the table for usage estimation.

One sink, one table, the same DB the app already uses.

## Default behavior

Ships with `USAGE_LOGGING_ENABLED=true` + `PostgresUsageLogger`. Binding
rules at startup:

| `enabled` | `service-name` | Binds | Notes |
|---|---|---|---|
| `false` | (any) | `NoOpUsageLogger` | Explicit opt-out, silent by design |
| `true`  | non-empty, not placeholder | `PostgresUsageLogger` | Normal path |
| `true`  | **empty / blank / `replit-mvp-template`** | **FAIL FAST at startup** | Loud `IllegalStateException` — do NOT silently fall back to NoOp |

The fail-fast row is REQUIRED. Past sessions silently bound `NoOpUsageLogger`
on empty `service-name` and shipped projects with zero usage events to the
DB — the dashboard read as "0 actions" with no error anywhere. The right
behavior is loud refusal so the misconfiguration is impossible to miss.

The scaffold's `application.yml` defaults `service-name` to
`${spring.application.name}` so an unset env var still binds the logger.
During generation, set `APP_SERVICE_NAME` / `spring.application.name` to the
real service identifier before handoff.

Local dev + tests run without setup beyond the default `usage_events`
Liquibase changelog. Logger never blocks a request, never throws into the flow.

## Automatic vs. manual

**Automatic** (template wires it, don't reimplement):
- `spring-boot-starter-aop` → AspectJ proxying (no `@EnableAspectJAutoProxy`).
- `UsageLoggingAspect` is `@Aspect @Component`, scanned at startup.
- `@ConditionalOnProperty(name="app.usage-logging.enabled", matchIfMissing=true)`
  → aspect active by default; flip to `false` to silence without code change.
- Aspect handles success/exception/duration/user/correlationId. Business code
  never imports `UsageLogger`.

**Manual** (your discipline):
- **`@LogUsage(action = "<dotted.lowercase>")` on every `*ServiceImpl` public
  method that's a user action.** Without it, the method is NOT logged —
  aspect can't guess which methods are user actions vs internal helpers.
- One `@LogUsage` per public method on every `*ServiceImpl`. Private/internal
  helpers stay un-annotated.
- `action` is a stable analytics identifier — pick once; renaming breaks dashboard queries.

Reference: `SampleServiceImpl` in scaffold.

## Required env placeholders (in `.env.example`)

```
USAGE_LOGGING_ENABLED=true
USAGE_LOG_SERVICE_NAME=generated-mvp
USAGE_LOG_ENVIRONMENT=dev
```

That is the entire usage-logging configuration surface.

`USAGE_LOG_SERVICE_NAME` should be set to the actual service identifier before
handoff. `replit-mvp-template` is a recognisable rejected placeholder. When
`enabled=true` AND `service-name` is empty/blank/placeholder,
`UsageLoggingConfig` MUST throw `IllegalStateException` at startup with a
clear error message — do NOT silently bind `NoOpUsageLogger`. NoOp is
reserved for the explicit opt-out path (`enabled=false`).

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

Wired via Spring AOP aspect — **no explicit `logger.record(...)` calls in
business code**. Methods to track get `@LogUsage(action = "...")`;
`UsageLoggingAspect` does the rest. Both success and exception paths captured
by one `try/catch/finally` in the aspect. Disable globally via
`app.usage-logging.enabled=false`.

### Components

- `LogUsage` annotation (`@Retention(RUNTIME) @Target(METHOD)`) carrying
  `action` (mandatory, dotted lowercase like `employee.update`) and
  `eventType` (default `api_request`). Lives in
  `service/src/main/java/<base>/service/common/observability/LogUsage.java` —
  in the service module so all `*ServiceImpl` classes can see it without
  service depending on application.
- `UsageLoggingAspect` — `@Aspect @Component @Order(LOWEST_PRECEDENCE - 100)`,
  bound to `@annotation(logUsage)`, wraps target with `try/catch/finally`.
- `UsageLoggingProperties` — `@ConfigurationProperties("app.usage-logging")`
  binding `enabled`, `service-name`, `environment`. NO magic strings.
- `UsageEvent` — immutable value type (`@Builder` Lombok record), also in
  `service/common/observability/`. Service classes don't construct it
  directly (the aspect does), but it lives in service so the aspect (in
  application/) can import it via the application→service direction.
- `UsageEventEntity` — JPA `@Entity` on `usage_events` (lives in `domain`).
  **The `attributes` field MUST be declared as
  `Map<String, Object>` with `@JdbcTypeCode(SqlTypes.JSON)` +
  `@Column(columnDefinition = "jsonb")` — Hibernate 6 native JSON mapping.**
  Declaring it as `String` binds null as `varchar`, and Postgres rejects:
  `column "attributes" is of type jsonb but expression is of type character
  varying`. Past sessions burned hours on exactly this error.
- `UsageEventRepository extends JpaRepository<UsageEventEntity, Long>`
  (lives in `domain`).
- `UsageLogger` — interface, single method `void record(UsageEvent event)`.
  Lives in `service/common/observability/`; impls (`PostgresUsageLogger`,
  `NoOpUsageLogger`) live in `application/observability/usage/`.
- `PostgresUsageLogger` — implementation, `@Async("usageLoggingExecutor")`
  so the DB write runs off the request thread. Repository `save()` opens its
  own short transaction.
- `NoOpUsageLogger` — bound only when `enabled=false`.

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
            .orElseThrow(() -> new AppException(ErrorReason.C001, id));
        entity.apply(update);
        return mapper.toRecord(repo.save(entity));
    }
}
```

That's the entire integration. The aspect captures: action `resource.update`,
duration of the call, success or error (with truncated message), the
authenticated user from `SecurityContext`, the correlationId from MDC.

### Async + SecurityContext caveat

`SecurityContextHolder` is `ThreadLocal`. If the `@LogUsage` method is itself
`@Async`, set at startup:

```java
SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_INHERITABLETHREADLOCAL);
```

Or use `DelegatingSecurityContextRunnable`. Not needed for synchronous
controller→service (common path).

**Why JPA, not JdbcTemplate.** Codebase uses JPA; mixing = two persistence
paths to maintain. JPA `save()` single insert is fast enough; async wrapper
keeps it off the request path.

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

Replit's [Create a dashboard from data](https://docs.replit.com/build/dashboard)
turns any data source into a small visualization app. Manager uses it on the
**same DB** the MVP writes to.

### Cross-app DB sharing

`postgresql-16` provisions a SQL Database **per app**. Dashboard app is
separate → does NOT auto-share MVP's DB. Two options:

**Option 1 — manual connection string (simplest).**
1. Copy MVP app's `DATABASE_URL` from Replit Secrets.
2. Create read-only role on MVP's DB (`usage_reader`, see below); build a
   connection string with its credentials (not superuser).
3. Paste read-only URL as `DATABASE_URL` in the dashboard app's Secrets.

**Option 2 — shared external DB** (only for cross-service aggregation).
Managed Postgres outside Replit (Neon, RDS); both apps' `DATABASE_URL` point
to it. Triggers the BigQuery migration revisit below.

### Manager flow (Option 1)

1. "Create a dashboard from data" in Replit.
2. Paste read-only `DATABASE_URL`.
3. Describe the view in plain language (e.g. "action counts per day, top users
   by action volume, error-rate trend, filter by service and date range").
4. Agent generates the dashboard; manager bookmarks the URL.

Dashboard is independent of MVP — reads `usage_events` only. Service code
stays free of dashboard UI.

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

For cross-service aggregation, add a second `UsageLogger` impl writing to
the aggregation target (e.g. BigQuery); bind instead of `PostgresUsageLogger`.
Event payload is portable — adding a sink is implementation-only, no contract changes.
