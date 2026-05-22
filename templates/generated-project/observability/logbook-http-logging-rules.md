# Logbook HTTP Logging Rules

Generated Java/Spring backend services must log inbound and outbound HTTP traffic with Zalando Logbook.

These logs are operational/debug logs to stdout. They are separate from usage analytics events.

## Required Dependency

- `org.zalando:logbook-spring-boot-starter`

Use a centrally managed version property in the parent POM.

## Required Configuration

Create a `LogbookConfiguration` class in the backend application/common layer.

Required behavior:

- register a `Logbook` bean
- use `JsonHttpLogFormatter` so request/response logs are JSON
- use `DefaultHttpLogWriter`
- log request and response bodies always, after filtering
- exclude noisy infrastructure traffic
- mask sensitive headers and JSON body fields

## Body Logging Policy

Request and response bodies must be logged for application endpoints.

Rules:

- always log bodies after applying filters
- never log unfiltered bodies
- truncate or filter very large payloads if the application accepts files/documents
- never log raw documents, credentials, JWTs, service account JSON, API keys, or raw personal data

Do not use `BodyOnlyIfStatusAtLeastStrategy` for this template because successful request/response bodies must also be visible in logs.

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
