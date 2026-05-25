# Canonical OpenAPI Rules

Single source of truth for OpenAPI. Other files reference this; never duplicate.

## Spec file

- Path: `src/main/resources/api/v1/specs/openapi.yaml`
  (e.g. `backend/application/src/main/resources/api/v1/specs/openapi.yaml`).
- **OUTSIDE `src/main/resources/static/`** — Vite's `outDir` is `static/` with
  `emptyOutDir: true`, so `npm run build` wipes everything there. Past sessions
  burned hours on "OpenAPI generator NPE: inputSpec is null" because spec got
  deleted between builds.
- `OpenApiSpecConfig` (WebMvcConfigurer) restores the public URL
  `/api/v1/specs/openapi.yaml` by mapping to `classpath:/api/v1/specs/`.
  SpringDoc + frontend `openapi-typescript` read from that URL.
- Committed as static file. OpenAPI is source of truth; controller annotations are not.

## Required top-level keys

`openapi: 3.0.3`, `info.title`, `info.version`, `servers`, `tags`, `paths`,
`components.securitySchemes`, `components.schemas`.

Tag-driven grouping (code generation uses tags).

### `/api/v1` prefix — must live in path keys, NOT only in `servers`

`servers: [{ url: /api/v1 }]` is **documentation only**. The `spring`
generator of `openapi-generator-maven-plugin` IGNORES `servers[].url` when
emitting Spring route mappings — controllers carry the path keys verbatim.
If your spec has `paths: { /employees: ... }`, Spring registers
`@GetMapping("/employees")`, NOT `@GetMapping("/api/v1/employees")`. The
Vite proxy, `AuthConstants.PUBLIC_PATHS`, SpringDoc URLs, and
manually-curl'd endpoints all expect `/api/v1/...` — generated routes that
omit the prefix produce 404s everywhere downstream.

**Correct (every path key carries the full prefix):**

```yaml
servers:
  - url: /api/v1               # documentation hint for Swagger UI
paths:
  /api/v1/employees:           # ← actual generated route
    get: { … }
  /api/v1/auth/mock/login:     # ← actual generated route
    post: { … }
```

**Forbidden (rely on `servers` to "add" the prefix):**

```yaml
servers:
  - url: /api/v1
paths:
  /employees:                  # WRONG — generator emits @GetMapping("/employees")
    get: { … }
```

Do NOT try to fix this with `server.servlet.context-path: /api/v1` in
`application.yml` — that conflicts with the per-app context-path (e.g.
`/employee-directory`) and forces a single global prefix that breaks
when V2 lands on `/api/v2/`.

**Review check (grep-able):** every `paths:` key must start with `/api/v`.

```bash
# Lists offending path keys (paths NOT starting with /api/v<digit>/).
awk '/^paths:/{in=1;next} /^[a-z]/{in=0} in && /^  \//{print FILENAME":"NR": "$0}' \
  backend/application/src/main/resources/api/v1/specs/openapi.yaml \
  | grep -vE '^[^:]+:[0-9]+:  /api/v[0-9]+/'
```

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

## Frontend contract boundary

Frontend code must call backend operations through the generated
`openapi-fetch` client only. Raw `fetch`, `axios`, and `XMLHttpRequest` are
forbidden under `frontend/src` because they bypass generated path/method types.
This is what allows regressions such as `PATCH` when the spec defines `PUT`, or
`/usage/summary` when the spec defines `/admin/usage`.

Every Replit/frontend workflow must run `npm run generate:api` before Vite,
typecheck, tests, or build. Stale/missing `schema.d.ts` invalidates the
contract-first guarantee.

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

### Enums — standalone schemas, never inline (REQUIRED)

Every enum MUST be a NAMED schema under `components.schemas`, referenced via
`$ref` from every property using it. Inline `enum: [...]` on a property is forbidden.

**Why.** Spring openapi-generator handles inline vs `$ref` differently:
- **Inline** → nested inner class (`EmployeeV1.StatusEnum`); same enum in two
  DTOs → TWO unrelated inner types, not assignable.
- **`$ref`** → top-level enum class (`EmployeeStatusV1`) shared by every
  referencer. One type, reusable in mappers/repos/query params.

The `EmployeeV1.StatusEnum does not exist` MapStruct failure comes from
mixing the two. Standardise on `$ref`.

**Correct (canonical form):**

```yaml
components:
  schemas:
    EmployeeStatusV1:
      type: string
      enum: [ACTIVE, ON_LEAVE, TERMINATED]
      description: Employment status.

    EmployeeV1:
      type: object
      required: [id, fullName, status]
      properties:
        id: { type: string, format: uuid }
        fullName: { type: string }
        status: { $ref: '#/components/schemas/EmployeeStatusV1' }

    UpdateEmployeeRequestV1:
      type: object
      properties:
        status: { $ref: '#/components/schemas/EmployeeStatusV1' }

paths:
  /employees:
    get:
      parameters:
        - in: query
          name: status
          schema: { $ref: '#/components/schemas/EmployeeStatusV1' }
```

**Forbidden (will produce inner-class enums and downstream pain):**

```yaml
# DON'T — inline enum on a property
EmployeeV1:
  properties:
    status:
      type: string
      enum: [ACTIVE, ON_LEAVE, TERMINATED]    # inline → inner class
```

**Naming.** Enum schema = `<Noun>V1` (version suffix as for DTOs).
Examples: `EmployeeStatusV1`, `PriorityV1`, `AuthModeV1`. Generator emits
Java enum with that exact name.

**Review check (grep-able):**
```bash
# Inline enums must not appear in property bodies. The only acceptable
# `enum:` line is at the top of a components.schemas entry.
awk '/^  [A-Z][A-Za-z0-9]+V[0-9]+:/{schema=1} /^    /{} /enum:/{if(!schema)print FILENAME":"NR": inline enum"}' \
  backend/application/src/main/resources/api/v1/specs/openapi.yaml
```

## Generator (Maven)

Configured inline in the canonical parent pom:
`templates/generated-project/scaffold/backend/pom.xml` (declared in
`<pluginManagement>`, activated in `application/pom.xml`).

Mandatory options:
`generatorName=spring`, `library=spring-boot`, `interfaceOnly=true`,
`useSpringBoot3=true`, `openApiNullable=false`, `skipDefaultInterface=true`,
`useTags=true`, `dateLibrary=java8-localdatetime`,
`additionalModelTypeAnnotations=@lombok.Generated` (exempts generated DTOs from JaCoCo).

**NOT** used: `modelNameSuffix`. Schema names already carry the version
suffix explicitly in the YAML (`ResourceV1`, `ApiErrorV1`, etc.) — see
"Versioned DTO names" below.

### `dateLibrary=java8-localdatetime` is locked

Project-wide time type: **`LocalDateTime`** (interpreted as UTC). Every
`format: date-time` → `java.time.LocalDateTime`. Do NOT change to `java8`
(→ `OffsetDateTime`); do NOT add `<typeMappings>OffsetDateTime=LocalDateTime</typeMappings>`.

On `incompatible types: LocalDateTime cannot be converted to OffsetDateTime`
→ change hand-written code (field type → `LocalDateTime`), not the generator.

UTC convention:
- DB: `TIMESTAMPTZ` storing UTC.
- Service/controller signatures: `LocalDateTime` as UTC.
- JSON wire: ISO-8601 without offset (`2026-05-24T10:00:00`); document UTC in each field.
- Frontend: treats as UTC, converts to local only at display.

### Versioned DTO names (mandatory)

Every schema named with explicit version suffix in YAML: `ResourceV1`,
`CreateResourceRequestV1`, `ApiErrorV1`. Generator emits Java class verbatim.

**Do NOT use `<modelNameSuffix>V1</modelNameSuffix>`.** A global suffix forces
every schema to the same version band — V2 typically changes ~30% of entities.
Explicit names let `ResourceV1` and `ResourceV2` coexist; global suffix forces
bumping every type at once.

Breaking V2 contract:
1. Add V2 schemas to the same `openapi.yaml` (`ResourceV2`, `CreateResourceRequestV2`).
   Keep V1 untouched.
2. Add operations under `/api/v2/...` (or split specs — two generator
   executions, separate `apiPackage`).
3. V1 endpoints/DTOs keep working; V2 emits V2 DTOs.
4. Mark V1 `deprecated: true` before sunset.

Hand-written code always uses the versioned name: `ApiErrorV1 dto = new ApiErrorV1()`.
There is no `ApiError` (no suffix) in this project.

Output: `${project.build.directory}/generated-sources/openapi`. Generated
API/model/invoker packages must be separate from hand-written.

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
