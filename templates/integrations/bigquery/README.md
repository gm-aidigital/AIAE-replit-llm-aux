# BigQuery adapter (opt-in)

Integrates Google BigQuery as an optional analytics export sink. The default baseline
usage-logging target is the PostgreSQL `usage_events` table — install this pack
only when an additional BigQuery export is needed.

## Install

```bash
PROJECT_ROOT=/path/to/project bash templates/integrations/bigquery/install.sh
```

## Configuration

Mount a service-account JSON and set the required variables:

```env
# Local development without Google credentials:
BIGQUERY_ENABLED=true
BIGQUERY_STUB_ENABLED=true
BIGQUERY_PROJECT_ID=demo-project
BIGQUERY_DATASET=analytics

# Production export (requires mounted service-account JSON):
BIGQUERY_ENABLED=true
BIGQUERY_STUB_ENABLED=false
BIGQUERY_CREDENTIALS_LOCATION=/run/secrets/bq.json
BIGQUERY_PROJECT_ID=my-gcp-project
BIGQUERY_DATASET=analytics
BIGQUERY_LOCATION=US
```

When BigQuery is active, `BigQueryUsageEventSink` becomes the primary usage
analytics route; failures fall back to PostgreSQL automatically.

Leave `BIGQUERY_ENABLED=false` (default) to keep usage analytics on PostgreSQL only.

## Security

- Never commit service-account JSON files.
- Mount credentials via Docker secrets or a volume.
- Credential file paths are never logged.
- The default sink is PostgreSQL — BigQuery is additive only.

## What is installed

| File | Description |
|------|-------------|
| `BigQueryClient` | Application-facing interface (`insertRow`) |
| `BigQueryClientImpl` | Google Cloud BigQuery SDK integration |
| `BigQueryProperties` | Typed `@ConfigurationProperties` |
| `BigQueryStubClient` | In-memory client when `stub-enabled=true` |
| `BigQueryConfig` | `@ConditionalOnProperty` beans (stub or production) |
| `BigQueryUsageEventSink` | Optional primary usage analytics export |
| `BigQueryExternalException` | Runtime exception for SDK failures |
