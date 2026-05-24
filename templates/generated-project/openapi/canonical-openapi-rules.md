# Canonical OpenAPI Rules

Single source of truth for OpenAPI in generated Java backend projects.
Other files reference this one; do not duplicate its content.

## Spec file

- Path: `src/main/resources/static/api/v1/specs/openapi.yaml`
  (relative to the backend application module — for example
  `backend/application/src/main/resources/static/api/v1/specs/openapi.yaml`).
- Committed as a static file.
- OpenAPI is the source of truth; controller annotations are not.

## Required top-level keys

`openapi: 3.0.3`, `info.title`, `info.version`, `servers`, `tags`, `paths`,
`components.securitySchemes`, `components.schemas`.

Server base path is `/api/v1`. Tag-driven grouping (code generation uses tags).

## Security scheme (canonical)

```yaml
components:
  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT
```

Protected operations must declare `security: [{ bearerAuth: [] }]` and document
`401` and `403` responses with examples.

## Response discipline

Every protected operation explicitly declares:
`200`/success, `400` when validation applies, `401`, `403`, `500`.

Shared schemas under `components.schemas`:
- common API error
- validation error
- multistatus when `207` is used

Error payload fields: machine-readable code, human message, timestamp, correlation ID.

## Schema discipline

- Every field has `type` and uses `format` where relevant (`date`, `date-time`, numeric sizes).
- `required` is explicit.
- Reuse schemas from `components.schemas`.
- Document enums with descriptions.
- Unique, stable `operationId` per operation.

## Generator (Maven)

Use the canonical snippet:
`templates/generated-project/pom-snippets/openapi-generator-maven-plugin.xml`.

Mandatory options:
`generatorName=spring`, `library=spring-boot`, `interfaceOnly=true`,
`useSpringBoot3=true`, `openApiNullable=false`, `skipDefaultInterface=true`,
`useTags=true`, `dateLibrary=java8-localdatetime`,
`additionalModelTypeAnnotations=@lombok.Generated` (exempts generated DTOs from JaCoCo).

**NOT** used: `modelNameSuffix`. Schema names already carry the version
suffix explicitly in the YAML (`ResourceV1`, `ApiErrorV1`, etc.) — see
"Versioned DTO names" below.

### Versioned DTO names (mandatory)

Every schema in `openapi.yaml` is named with a version suffix **explicitly
in the YAML** — `ResourceV1`, `CreateResourceRequestV1`, `ApiErrorV1`,
`ValidationParameterV1`. The generator emits the Java class with that same
name verbatim.

**Do NOT use `<modelNameSuffix>V1</modelNameSuffix>` in the generator
config.** A global suffix forces every schema to carry the same version
band, which is wrong: in practice, V2 changes maybe 30% of entities and
leaves the rest at V1. With explicit names in the YAML you can keep
`ResourceV1` and add `ResourceV2` side-by-side; with a global suffix you'd
have to bump every type at once.

When a breaking v2 contract is needed:

1. Add the new schemas to the **same** `openapi.yaml` under their explicit
   V2 names: `ResourceV2`, `CreateResourceRequestV2`. Keep V1 schemas
   untouched.
2. Add new operations under `/api/v2/...` paths in the same file (or split
   into two specs if the team prefers — same generator plugin, two
   executions, each with its own `apiPackage`).
3. V1 endpoints + V1 DTOs keep working unchanged; V2 endpoints emit V2 DTOs.
4. Sunset V1 when the last consumer migrates; mark V1 operations
   `deprecated: true` in the spec first to telegraph the deadline.

Why this matters in practice: 95% of "V2" changes touch some entities and
not others. The explicit-suffix convention lets you ship those incremental
breaks without renaming the world.

Every reference to a DTO type in handwritten code uses the explicit
versioned name: `ApiErrorV1 dto = new ApiErrorV1()`. There is never a
type called `ApiError` (no suffix) in this project — the suffix is part
of the type's identity, not a generator-time decoration.

Output: `${project.build.directory}/generated-sources/openapi`.
Generated API/model/invoker packages must be explicit and separate from
hand-written packages.

## SpringDoc

```yaml
springdoc:
  swagger-ui:
    url: /api/v1/specs/openapi.yaml
```

## Frontend contract

React frontends consume this same YAML via `openapi-typescript` +
`openapi-fetch` + TanStack Query. See
`templates/generated-project/frontend/canonical-react-frontend-rules.md`.

## Review checklist

See `templates/generated-project/openapi/openapi-review-checklist.md`.
