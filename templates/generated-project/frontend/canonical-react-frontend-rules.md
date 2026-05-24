# Canonical React Frontend Rules

Single source of truth for the React frontend of generated projects.
Bulletproof-React-inspired, adapted for MVP→handoff.

## Stack

- React + TypeScript (strict).
- Vite (use another framework only if user explicitly asks).
- `openapi-typescript` (types) + `openapi-fetch` (HTTP client).
- TanStack Query for server state.
- Clerk React SDK when SSO is enabled (see
  `templates/generated-project/auth/google-sso-clerk-blueprint.md`).
- **Plain CSS files with BEM naming** for styling — NOT CSS Modules,
  Tailwind, styled-components, Emotion, or CSS-in-JS. See
  `templates/generated-project/frontend/bem-naming-rules.md`.

## Folder layout

```
src/app          src/pages        src/features
src/entities     src/shared/api   src/shared/ui
src/shared/lib   src/shared/config
```

## OpenAPI client generation

Source: `../backend/application/src/main/resources/static/api/v1/specs/openapi.yaml`
(adapt path when backend module differs and document it in README).

Required scripts in `frontend/package.json`:

```json
{
  "scripts": {
    "generate:api": "openapi-typescript ../backend/application/src/main/resources/static/api/v1/specs/openapi.yaml -o src/shared/api/generated/schema.d.ts",
    "check:api": "npm run generate:api && tsc --noEmit"
  }
}
```

Generated artifacts:
- `src/shared/api/generated/schema.d.ts` (never edited by hand)
- `src/shared/api/client.ts` (auth-aware fetcher built on `openapi-fetch`)

## Rules

- No handwritten DTOs duplicating OpenAPI schemas.
- No raw `fetch` calls or hardcoded backend URLs in components.
- Components do not build URLs; the typed client owns paths.
- TanStack Query for all backend reads/writes; no ad-hoc global state for server data.
- Every async surface renders loading / empty / error / success.
- Strict TypeScript; `any` only with isolation + justification.
- Semantic HTML and keyboard accessibility.
- **BEM class names** for all styles (`block`, `block__element`, `block--modifier`).
  Plain CSS only; no CSS Modules / Tailwind / styled-components.
  See `bem-naming-rules.md` for the full convention and examples.
- One block per directory: `<block-name>.tsx` + `<block-name>.css` + optional `components/`.
- Frontend never accesses the DB, service-account keys, or any secrets directly — backend APIs only.
- No secrets in frontend env vars.

## Auth

Follow `templates/generated-project/auth/google-sso-clerk-blueprint.md`.

UI requirements:
- Login screen always renders (mock mode supports it without external IdP).
- Send `Authorization: Bearer <jwt>` to backend for protected calls.
- `/api/v1/auth/me` bootstraps user state.
- `401` → clear local auth state + redirect to login.
- `403` → access-denied UI.

## Testing

Prefer behavior tests over snapshots. Cover auth mode rendering/switching,
loading/empty/error/success states, form validation, critical flows.
