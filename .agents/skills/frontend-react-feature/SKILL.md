---
name: frontend-react-feature
description: Build a React + TypeScript frontend feature in a generated project. Use when adding pages, features, hooks, or wiring server state. Covers typed API boundary, dual-mode auth UI, accessible UX states, and Replit-vs-local runtime split.
metadata:
  user-invocable: "true"
---

# Frontend React Feature

Use for any frontend change in a generated MVP project.

## Canonical references

- Frontend rules: `templates/generated-project/frontend/canonical-react-frontend-rules.md`
- BEM naming (CSS class convention): `templates/generated-project/frontend/bem-naming-rules.md`
- Auth blueprint: `templates/generated-project/auth/google-sso-clerk-blueprint.md`
- Project structure: `templates/generated-project/structure/near-production-project-structure.md`

## Baseline

React · TypeScript (strict) · Vite (unless user explicitly asks for another
framework) · `openapi-typescript` + `openapi-fetch` · TanStack Query ·
Clerk React SDK when SSO is enabled.

## Core rules (delta from canonical-react-frontend-rules.md)

- No `any` unless isolated and justified.
- No raw backend calls from page components.
- No hardcoded backend URLs in components.
- No handwritten DTOs duplicating OpenAPI schemas.
- Every async surface renders loading / empty / error / success.
- Semantic HTML; keyboard-accessible controls.
- **BEM class names** for every style (`block__element--modifier`). Plain
  `.css` files, no CSS Modules / Tailwind / styled-components. One block
  per directory. Blocks have no external margins. See `bem-naming-rules.md`.
- Frontend never reaches the DB, service-account keys, or any secrets directly — backend APIs only.

## Auth UX

Follow the canonical blueprint. Frontend must:
- show a working login screen in mock mode and SSO mode without code changes
- read `AUTH_MODE` from runtime config, not from build target
- attach `Authorization: Bearer <jwt>` to protected calls
- treat backend (`/api/v1/auth/me`) as source of truth
- clear local state on `401`, render access-denied on `403`

## Runtime: Replit vs local-dev

| | Replit | Local-dev |
|---|---|---|
| Dev server | Vite on `5173` (workspace preview, mapped 1:1) | Vite on `5173` |
| Deployment | NOT Vite. Spring Boot serves the built `dist/` from its static resources; `vite.config.ts` writes the build there. | Standalone Spring or Vite preview, your call |
| API base URL | Same-origin in Deployment; `/api` proxied to `localhost:5000` in dev workspace via `vite.config.ts` | `http://localhost:8080/<context-path>` |
| Secrets | Replit Secrets pane | `.env.local` (gitignored) |

`shared/config` reads runtime config from `import.meta.env` and validates it at
boot. Build-time secrets are forbidden.

## React common gotchas (real failures from past generations)

- **Never call `navigate()` during render.** `useNavigate()` returns a
  setter that triggers a state update — calling it directly in the function
  body of a component during rendering produces a React warning and may
  cause infinite re-render. Use `<Navigate to="..." replace />` in JSX
  instead, or wrap the `navigate()` call in `useEffect`.

- **`401` handling lives in ONE place** — the auth-aware fetcher in
  `shared/api/client.ts`. It clears local auth state and exposes a router
  hook (or sets a context flag) that the root layout renders as a
  `<Navigate to="/login" />`. Pages do not check status codes themselves.

- **Don't read `import.meta.env` outside `shared/config/runtime.ts`**.
  Centralising the read lets you validate it once at boot and stops typos
  like `VITE_CLERCK_PUBLISHABLE_KEY` from silently rendering `undefined`.

- **TanStack Query keys are arrays, never strings**. `queryKey: ["users", id]`
  not `queryKey: "users-" + id`. String keys break query invalidation.

## Testing guidance

Prefer behavior tests over snapshots. Cover auth-mode rendering and switching,
loading/empty/error/success states, form validation, critical user flows.
