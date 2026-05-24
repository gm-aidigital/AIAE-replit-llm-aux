# Logbook HTTP Logging Rules

Backend logs inbound + outbound HTTP via Zalando Logbook. Operational/debug
logs to stdout, separate from usage analytics events.

## Required Dependency

- `org.zalando:logbook-spring-boot-starter` (version managed in parent POM).

## Required Configuration

`LogbookConfiguration` class in application/common layer:

- register `Logbook` bean
- `JsonHttpLogFormatter` (JSON request/response logs)
- `DefaultHttpLogWriter`
- log request + response bodies always, after filtering
- exclude noisy infrastructure
- mask sensitive headers + JSON body fields

## Body Logging Policy

- Always log bodies after filters; never log unfiltered.
- Truncate large payloads if app accepts files/docs.
- Never log raw documents, credentials, JWTs, service account JSON, API keys, PII.
- Do NOT use `BodyOnlyIfStatusAtLeastStrategy` — successful bodies must be visible too.

## Exclusions

Do not log:

- `/health`
- `/actuator/**`
- `/swagger-ui/**`
- `/api/v1/specs/**`
- static assets
- OPTIONS requests
- readiness/liveness probes

## Required Header Masking

Mask at least:

- `Authorization`
- `Cookie`
- `Set-Cookie`
- `X-API-Key`
- `AccessKey`
- `Proxy-Authorization`
- `X-Goog-*`

## Required JSON Field Masking

Mask fields matching:

- `.*password.*`
- `.*token.*`
- `.*secret.*`
- `.*key.*`
- `.*credential.*`
- `.*authorization.*`
- `privateKey`
- `clientSecret`
- `serviceAccount`

Replacement value:

- `XXX`

## Application YAML Baseline

```yaml
logging:
  level:
    org.zalando.logbook: TRACE

app:
  logbook:
    censored-replacement: XXX
    headers-to-censor:
      - Authorization
      - Cookie
      - Set-Cookie
      - X-API-Key
      - AccessKey
      - Proxy-Authorization
    json-fields-to-censor:
      - .*password.*
      - .*token.*
      - .*secret.*
      - .*key.*
      - .*credential.*
      - .*authorization.*
      - privateKey
      - clientSecret
      - serviceAccount
    excluded:
      "/**/actuator/**":
        - GET
      "/**/swagger-ui/**":
        - GET
      "/**/specs/**":
        - GET
      "/health":
        - GET
      "/**":
        - OPTIONS
```

## Acceptance Checks

- normal API request logs request and response body
- `Authorization` is masked
- JSON token/password/secret fields are masked
- `/actuator/health` is not logged
- OPTIONS request is not logged
