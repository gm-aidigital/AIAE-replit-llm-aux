# HTTP client foundation (opt-in)

Install with the **first** outbound HTTP integration pack only. Do not add this
module to projects without real external API calls.

## Contains

- `PooledHttpClientProperties`
- `PooledRestClientFactory`
- Apache HttpClient 5 wiring for Spring `RestClient`
- mandatory `LogbookClientHttpRequestInterceptor` registration using the
  application-owned `Logbook` bean
- reusable `ExternalClientMetricsInterceptor` from `backend/observability` for
  the shared `external.client.requests` timer schema
- focused configuration tests

## Install

```bash
bash templates/integrations/_http-client-foundation/install.sh
```

Later HTTP integration packs reuse this foundation instead of copying transport code.
