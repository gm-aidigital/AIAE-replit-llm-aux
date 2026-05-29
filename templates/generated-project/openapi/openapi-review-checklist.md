# OpenAPI Review Checklist

Quick gate for every API change. Detailed rules in
`templates/generated-project/openapi/canonical-openapi-rules.md`.

## Contract

- [ ] `openapi` and `info.version` present
- [ ] Spec path is `src/main/resources/api/v1/specs/openapi.yaml` (NOT under `static/` — Vite build wipes that dir)
- [ ] Path/method correct, unique stable `operationId`, meaningful tags

## Request and response

- [ ] Request body schema with constraints when applicable
- [ ] Parameters documented with constraints
- [ ] Response schema for every declared status code
- [ ] `Content-Type: application/json` unless otherwise required
- [ ] File exports/downloads use `type: string`, `format: binary` under the
      real media type (`text/csv`, XLSX/PDF media type, etc.) so Spring
      generator emits `ResponseEntity<Resource>`
- [ ] No export/download controller is expected to return `byte[]` or call
      generated-interface `getRequest()`

## Errors

- [ ] Explicit `4xx`/`5xx` for expected failures
- [ ] Shared error schema across operations
- [ ] At least one error example per operation

## Security (when protected)

- [ ] `bearerAuth` exists in `components.securitySchemes`
- [ ] Operation declares `security`
- [ ] Explicit `401` and `403` responses
- [ ] `/api/v1/auth/me` documented (no mock-login endpoint — Clerk SSO only)

## Examples

- [ ] Success example
- [ ] Validation-error example
- [ ] Business-error example (when relevant)

## Compatibility

- [ ] No silent breaking changes
- [ ] Deprecations marked
- [ ] Migration note for any breaking change
