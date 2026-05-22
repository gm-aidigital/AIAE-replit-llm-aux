# Canonical React Frontend Rules

Generated React frontends must be maintainable, typed and connected to backend APIs through OpenAPI generation.

The architecture is based on the practical feature-first approach popularized by Bulletproof React, adapted for MVPs that must be handed off to engineering teams.

## Stack

- React
- TypeScript
- Vite unless the user explicitly requests another framework
- TanStack Query for server state
- `openapi-typescript` for API types
- `openapi-fetch` for the small typed HTTP client
- Clerk React SDK when Google SSO is enabled

## Structure

Use this layout:

- `src/app`
- `src/pages`
- `src/features`
- `src/entities`
- `src/shared/api`
- `src/shared/ui`
- `src/shared/lib`
- `src/shared/config`

## OpenAPI Client Generation

Frontend must consume the existing backend OpenAPI YAML. Do not handwrite DTOs that duplicate backend schemas.

Canonical source spec:

- `../backend/application/src/main/resources/static/api/v1/specs/openapi_3.0.3_spec.yaml`

If the generated project uses a different backend module path, keep the spec path explicit in `package.json` scripts and document it in `README.md`.

Required packages:

- `openapi-typescript`
- `openapi-fetch`
- `@tanstack/react-query`

Required scripts:

```json
{
  "scripts": {
    "generate:api": "openapi-typescript ../backend/application/src/main/resources/static/api/v1/specs/openapi_3.0.3_spec.yaml -o src/shared/api/generated/schema.d.ts",
    "check:api": "npm run generate:api && tsc --noEmit"
  }
}
```

Generated API artifacts:

- `src/shared/api/generated/schema.d.ts`
- `src/shared/api/client.ts`

Rules:

- never manually edit generated schema files
- generated types are imported by API wrappers and feature hooks
- API wrappers attach auth headers in one place
- React components do not build raw URLs
- React components do not call `fetch` directly

## Data Fetching

Use TanStack Query for backend data:

- queries for reads
- mutations for writes
- stable query keys colocated with feature API code
- loading, empty, error and success states for every async surface

Do not store server data in ad hoc global state.

## Auth Integration

Auth must follow:

- `templates/generated-project/auth/google-sso-clerk-blueprint.md`

Frontend requirements:

- real SSO uses Clerk
- mock mode uses local login form
- all protected API calls use `Authorization: Bearer <jwt>`
- `GET /api/v1/auth/me` bootstraps user state
- `401` clears local auth state and routes to login
- `403` renders access denied

## Quality Rules

- strict TypeScript
- no `any` unless isolated and justified
- no hardcoded backend URLs in components
- no secrets in frontend env vars
- form validation is explicit
- semantic HTML and keyboard-accessible controls
- avoid snapshot-heavy tests; prefer behavior tests for forms, auth, API states and critical flows
