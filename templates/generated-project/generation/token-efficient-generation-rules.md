# Token-Efficient Generation Rules

Generate projects by reusing canonical artifacts and codegen. Do not retype.

## Hard rules

- Do not duplicate canonical content. Reference instead.
- OpenAPI YAML is the single source of API truth — backend interfaces and
  frontend types are generated from it.
- Generated code lives in dedicated folders. Never mix with handwritten code.
- Do not paste generated OpenAPI/DTO bodies into explanations or READMEs.
- Do not scaffold unused screens, endpoints, entities, DTOs, repositories.
- Do not include large fixture datasets inline; use small fixtures.
- Do not commit service account JSON. Env placeholders only.

## Recommended order

1. Root structure + mandatory files (see
   `templates/generated-project/structure/near-production-project-structure.md`).
2. OpenAPI YAML with only the endpoints the feature needs.
3. Backend `openapi-generator-maven-plugin` snippet + interface implementations.
4. Frontend `openapi-typescript` script + small typed client.
5. Feature code over generated types.
6. Persistence (Liquibase + JPA) only when needed.
7. Tests for changed behavior.
8. Replit run + (optional) local docker-compose dry run.

## Preferred codegen

- Backend: `openapi-generator-maven-plugin`, `interfaceOnly=true`.
- Frontend: `openapi-typescript` + `openapi-fetch`.
- Server state: TanStack Query wrappers around the generated client.

Keep generated surface small, typed and inspectable.
