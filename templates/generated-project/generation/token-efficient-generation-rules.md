# Token-Efficient Generation Rules

Replit must generate projects by reusing canonical artifacts and generators instead of hand-writing repeated boilerplate.

## Core Rules

- Copy canonical template snippets instead of retyping them.
- Generate OpenAPI first, then generate backend interfaces and frontend API types from the same YAML.
- Do not manually duplicate DTOs between backend and frontend.
- Do not paste generated OpenAPI models or generated API types into explanations.
- Keep generated code in dedicated generated folders.
- Keep handwritten code small and layered.

## Recommended Generation Order

1. Create root structure and mandatory files.
2. Create OpenAPI YAML with only required endpoints.
3. Add backend generator snippet and generated-interface implementation classes.
4. Add frontend OpenAPI type generation script and a small typed API client.
5. Add feature code using generated types.
6. Add Docker Compose and DB only when persistence is needed.
7. Add tests for changed behavior.
8. Run local dry run.

## Avoid Token Waste

- Do not scaffold unused screens, endpoints, entities, DTOs or repositories.
- Do not create placeholder abstractions without immediate use.
- Do not include huge sample datasets inline; use small fixtures.
- Do not regenerate full project text in README; document commands and decisions only.
- Do not create parallel API client styles.
- Do not add both generated full clients and handwritten clients.
- Do not paste service account JSON or secret examples; use env placeholders only.

## Preferred Codegen Choices

- Backend: `openapi-generator-maven-plugin` with `interfaceOnly=true`.
- Frontend: `openapi-typescript` + `openapi-fetch`.
- Server state: TanStack Query wrappers around the small OpenAPI client.

This keeps generated code small, typed and easy to inspect.
