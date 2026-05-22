# Usage Logging BigQuery Rules

Generated internal AI Digital services must write one usage event per meaningful user action.

Do not commit service account JSON keys. Credentials are supplied through environment variables or a secret manager at runtime.

Production/staging target is the shared BigQuery usage table.
Local/Replit/dev fallback is PostgreSQL with the same logical schema when BigQuery credentials are absent.

## Target Table

- Project: `aiae-493511`
- Dataset: `usage_logging_ai_services`
- Table: `usage_logging_ai_services_table`
- Fully qualified table: `aiae-493511.usage_logging_ai_services.usage_logging_ai_services_table`

The table is append-only. Services only write rows; they do not read from it.

## Event Schema

Every event row must follow this schema:

- `event_id`: UUID v4 per event
- `event_timestamp`: UTC timestamp, ISO 8601
- `service`: stable lowercase hyphen-separated service name
- `environment`: `prod`, `staging`, or `dev`
- `event_type`: one of `api_request`, `auth`, `error`, `custom`
- `action`: dotted lowercase action name, for example `forecast.create`
- `user_id`: stable authenticated user ID when available
- `user_email`: authenticated user email when available
- `status`: `success`, `error`, or HTTP status code as string
- `duration_ms`: operation latency in milliseconds
- `attributes`: sanitized structured JSON payload
- `error_message`: short error message for failures
- `client_ip`: first entry of `X-Forwarded-For`
- `user_agent`: request `User-Agent`

## Required Configuration

Generated projects must include these placeholders in `.env.example`:

- `BQ_USAGE_CREDENTIALS_JSON`
- `BQ_USAGE_TABLE`
- `USAGE_LOG_SERVICE_NAME`
- `USAGE_LOG_ENVIRONMENT`
- `USAGE_LOGGING_ENABLED`
- `USAGE_LOG_LOCAL_FALLBACK_ENABLED`

Recommended defaults:

```properties
BQ_USAGE_TABLE=aiae-493511.usage_logging_ai_services.usage_logging_ai_services_table
USAGE_LOG_ENVIRONMENT=dev
USAGE_LOGGING_ENABLED=true
USAGE_LOG_LOCAL_FALLBACK_ENABLED=true
```

`BQ_USAGE_CREDENTIALS_JSON` must be a single-line service account JSON string provided through runtime secrets. It must not be committed.

## Java Backend Contract

Java/Spring backends must include a usage logging component with this shape:

- `UsageLoggingProperties`
- `UsageLogger` interface
- `BigQueryUsageLogger` implementation
- `PostgresUsageLogger` local fallback implementation
- `CompositeUsageLogger` or conditional bean selection
- no-op behavior when credentials/table/service name are missing
- async/fire-and-forget write path
- never throw from usage logging into user request flow

Required dependency:

- `com.google.cloud:google-cloud-bigquery`
- PostgreSQL/JPA/JDBC dependency already used by the backend when local fallback is enabled

Required behavior:

- initialize BigQuery client from `BQ_USAGE_CREDENTIALS_JSON` when present
- use `ServiceAccountCredentials.fromStream(...)` or equivalent in-memory credentials loading
- insert rows into `BQ_USAGE_TABLE`
- if credentials are missing and `USAGE_LOG_LOCAL_FALLBACK_ENABLED=true`, insert rows into local PostgreSQL table
- log insertion failures to structured application logs
- swallow insertion failures after logging
- fail fast in `prod` or `staging` when usage logging is enabled but BigQuery credentials are missing

Do not load credentials from a committed JSON file path by default.

## Local PostgreSQL Fallback

Use local PostgreSQL fallback only for local/Replit/dev environments. It exists so generated apps can answer "who used what and how often" without granting Replit access to GCP.

Canonical local table:

- `usage_log_events`

Columns:

- `event_id TEXT PRIMARY KEY`
- `event_timestamp TIMESTAMP WITH TIME ZONE NOT NULL`
- `service TEXT NOT NULL`
- `environment TEXT`
- `event_type TEXT NOT NULL`
- `action TEXT`
- `user_id TEXT`
- `user_email TEXT`
- `status TEXT`
- `duration_ms BIGINT`
- `attributes JSONB`
- `error_message TEXT`
- `client_ip TEXT`
- `user_agent TEXT`

Rules:

- create the table through Liquibase
- use PostgreSQL `TEXT`, `BIGINT`, `JSONB`, never `VARCHAR`
- add indexes for local analytics:
  - `(service, event_timestamp)`
  - `(user_email, event_timestamp)`
  - `(action, event_timestamp)`
- do not use local PostgreSQL fallback in production/staging unless explicitly approved
- document that local fallback is not a replacement for centralized BigQuery analytics

## Instrumentation Rules

Log:

- successful user actions
- failed user actions
- auth login/logout when applicable
- important custom domain actions

Do not log:

- `/health`
- `/actuator/*`
- OPTIONS requests
- browser prefetches
- static assets
- internal readiness/liveness probes

For each logged action, record both success and error paths with the same `action` value.

## Sensitive Data Rules

Never write these to `attributes`, `error_message`, or logs:

- credentials
- API keys
- JWTs
- service account JSON
- raw document contents
- full request/response bodies
- third-party PII
- private URLs

Keep `attributes` small and structured. Strip raw user content over roughly 500 characters.

## Local and Test Behavior

- local dev must run without `BQ_USAGE_CREDENTIALS_JSON`
- local dev should write usage events to PostgreSQL fallback when `USAGE_LOG_LOCAL_FALLBACK_ENABLED=true`
- tests must run without BigQuery access
- logger must no-op only when usage logging is disabled or tests intentionally disable it
- unit tests should verify no-op behavior, success insert mapping, failed insert swallowing, and sanitization

## Acceptance Checks

Manual smoke check after deployment:

```sql
SELECT
  event_timestamp,
  service,
  action,
  user_email,
  status,
  duration_ms
FROM `aiae-493511.usage_logging_ai_services.usage_logging_ai_services_table`
WHERE service = 'your-service-name'
ORDER BY event_timestamp DESC
LIMIT 10;
```

Expected result: a triggered user action appears within normal BigQuery streaming latency.
