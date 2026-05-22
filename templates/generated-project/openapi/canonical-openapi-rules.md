# Canonical OpenAPI Rules

These rules are mandatory for generated Java backend projects.

## Spec Placement

Canonical spec location:

- `src/main/resources/static/api/v1/specs/openapi_3.0.3_spec.yaml`

In a multi-module backend, this path is relative to the backend application module, for example:

- `backend/application/src/main/resources/static/api/v1/specs/openapi_3.0.3_spec.yaml`

Rules:

- keep the spec as a committed static file
- OpenAPI is the source of truth
- implementation follows the spec, never the reverse
- Swagger UI must render this static spec file

## Top-Level Structure

Spec must define:

- `openapi: 3.0.3`
- `info.title`
- `info.version`
- `servers`
- `tags`
- `paths`
- `components.securitySchemes`
- `components.schemas`

Preferred versioning and layout:

- server base path under `/api/v1`
- tag-driven grouping of endpoints
- reusable shared error schemas

## Security

Protected APIs must declare:

- `security:`
- `bearerAuth` security scheme

Canonical bearer scheme:

```yaml
components:
  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT
```

## Response Discipline

Each protected operation should explicitly define:

- `200` or other success code
- `400` for validation/input errors when relevant
- `401` for missing/expired token
- `403` for invalid/insufficient authorization
- `500` for unhandled/internal failure

Shared schemas are mandatory for non-trivial APIs:

- validation error schema
- common API error schema
- multistatus schema when `207` is used

Error payloads should include:

- machine-readable code
- human-readable message
- timestamp
- correlation ID

## Operation Naming and Grouping

- use tags consistently because code generation groups APIs by tags
- every operation must have a unique `operationId`
- `operationId` must be stable and explicit

## Schema Discipline

- every field should have `type`
- use `format` for `date`, `date-time`, numeric sizes where relevant
- define `required` explicitly
- prefer reusable schemas under `components.schemas`
- document enums with descriptions

## SpringDoc Contract

Generated projects should expose Swagger UI against the static OpenAPI file:

```yaml
springdoc:
  swagger-ui:
    url: /api/v1/specs/openapi_3.0.3_spec.yaml
```

Do not treat controller annotations as the primary documentation source.

## Generator Contract

Generated Java backend projects must use the canonical Maven snippet from:

- `templates/generated-project/pom-snippets/openapi-generator-maven-plugin.xml`

Mandatory generator behavior:

- generator: `spring`
- library: `spring-boot`
- generate interfaces, not controller implementations
- group API classes by tags
- disable `openApiNullable`
- skip default interface bodies
- use Spring Boot 4 generation mode
- use `java8-localdatetime` date library

## Generated-Code Layout

Generated sources must live under:

- `${project.build.directory}/generated-sources/openapi`

Generated packages must be explicit:

- generated invoker package
- generated API package
- generated model package

Do not mix generated classes into hand-written application packages.

## Frontend Contract

React frontends must use this same YAML as their API contract source.

Required frontend approach:

- `openapi-typescript` generates TypeScript schema types
- `openapi-fetch` provides a small typed HTTP client
- TanStack Query wraps server state in feature hooks
- no handwritten frontend DTOs that duplicate OpenAPI schemas
- no raw `fetch` calls from components

Frontend rules are defined in:

- `templates/generated-project/frontend/canonical-react-frontend-rules.md`
