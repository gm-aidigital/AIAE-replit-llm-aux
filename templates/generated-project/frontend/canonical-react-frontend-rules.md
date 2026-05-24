# Canonical React Frontend Rules

Single source of truth for React frontend. Bulletproof-React-inspired,
adapted for MVP→handoff.

## Stack

- React + TypeScript (strict).
- Vite (other frameworks only on explicit user request).

## Bootstrap rule — copy the scaffold, do NOT regenerate

`frontend/vite.config.ts`, `package.json`, `tsconfig.json`, `index.html`,
`nginx.conf`, `Dockerfile` MUST be copied verbatim from
`templates/generated-project/scaffold/frontend/`.

**Forbidden:** `npm create vite@latest`, `npx create-vite`, any other
Vite/CRA scaffolder. Generators strip Replit-specific settings; regenerating
re-hits these three bugs:

1. **`allowedHosts` missing or wrong** → Replit preview returns
   `Blocked request. This host ("xxx.picard.replit.dev") is not allowed.`
   Vite 5+ requires explicit allow-list behind a proxy with unfamiliar Host.
   - REQUIRED: array including `.replit.dev`, `.repl.co`, `.kirk.replit.dev`,
     `localhost`, `127.0.0.1` (leading dot = subdomain wildcard).
   - FORBIDDEN (silently broken): `allowedHosts: 'all'`, `allowedHosts: '*'`
     (both are literal hostnames, not wildcards — block every request).
     `allowedHosts: true` works but disables security; use the explicit array.
   - Apply to BOTH `server` AND `preview` blocks.

2. **Port set to 5000** → collides with Spring backend. Vite stays on `5173`;
   backend owns `5000`. Reversing breaks Replit Deployment (serves published
   port from Spring's built static files).

3. **Proxy `target` hardcoded `http://localhost:8080`** → wrong port in
   Replit workspace (backend on `5000`). Read from `BACKEND_DEV_PORT`,
   default `5000`. See scaffold for canonical proxy block.

Regeneration (major Vite upgrade): diff against scaffold MUST preserve all three.

- `openapi-typescript` (types) + `openapi-fetch` (HTTP client).
- TanStack Query for server state.
- Clerk React SDK when SSO is enabled (see
  `templates/generated-project/auth/google-sso-clerk-blueprint.md`).
- Vitest + Testing Library for frontend behavior tests.
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

Source: `../backend/application/src/main/resources/api/v1/specs/openapi.yaml`
(adapt path when backend module differs and document it in README).

Required scripts in `frontend/package.json`:

```json
{
  "scripts": {
    "generate:api": "openapi-typescript ../backend/application/src/main/resources/api/v1/specs/openapi.yaml -o src/shared/api/generated/schema.d.ts",
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

MVP is not complete with zero frontend tests when frontend logic is generated.
Use Vitest behavior tests, not snapshot-only suites.

Minimum before publish/completion:
- main route renders without crashing
- auth mode/session state is covered through the same UI path used by the app
- primary server-backed surface covers loading, error, and success states
- forms or critical user actions cover expected outcome plus one validation/error case

Handoff expands this into deeper role-dependent and critical-flow coverage.
