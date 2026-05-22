---
name: frontend-react-feature
description: Build React + TypeScript frontend features with typed API boundaries, dual-mode auth UI, and predictable MVP behavior.
argument-hint: "[feature-summary]"
user-invocable: true
---

# Frontend React Feature

Use this skill for frontend work in generated MVP projects.

## Baseline

- React + TypeScript
- Vite unless another framework is explicitly requested
- Typed API client generated from OpenAPI
- TanStack Query for server state
- Thin pages/routes
- Feature modules with hooks/components
- Accessible UX states (loading, empty, error, success)

Canonical frontend rules:
- `templates/generated-project/frontend/canonical-react-frontend-rules.md`

## Recommended Structure

- `app`: providers, router, app shell, global error boundary
- `pages`: route-level composition only
- `features`: feature modules
- `entities`: reusable domain-level UI/models
- `shared/api`: generated OpenAPI types and typed API client
- `shared/ui`: reusable UI components
- `shared/lib`: framework-agnostic helpers
- `shared/config`: runtime config reader and validation

## Core Rules

- No `any` unless absolutely unavoidable.
- No raw backend calls from page components.
- No direct DB/BigQuery/secrets access from frontend.
- No hardcoded backend URLs in components.
- Keep form validation explicit and user-visible.
- Use semantic HTML and keyboard-accessible controls.

## API Integration Pattern

1. Generate frontend API types from the committed backend OpenAPI YAML.
2. Use `openapi-typescript` for types and `openapi-fetch` for the HTTP client.
3. Keep the auth-aware fetcher in `shared/api`.
4. Use TanStack Query hooks in feature modules.
5. Keep components focused on rendering and local interactions.
6. Handle errors with stable UI states and user-readable messages.

Rules:
- no handwritten DTOs that duplicate OpenAPI schemas
- no raw `fetch` in components
- no multiple competing API client patterns

## Auth UX (Mandatory Dual-Mode)

Frontend must support two auth modes without code rewrites:

- Real mode: Google SSO flow (Clerk preferred, or project-standard OIDC integration).
- Fallback mode: mock local user login.

Requirements:
- Login screen/form must always exist.
- Mode selection must come from config/env, not hardcoded branching by build target.
- If SSO keys are missing, app still runs with mock local user mode.
- Once SSO keys are provided, same flow activates real SSO.
- Frontend must never store server secrets.
- Frontend must send `Authorization: Bearer <jwt>` to backend for protected requests.
- Frontend must treat backend as source of truth for authentication (`/api/v1/auth/me` or protected API responses).
- On `401`, clear local auth state and redirect user to login.
- On `403`, show explicit access-denied UI state.

Auth mode contract to preserve across screens:
- `AUTH_MODE=auto|sso|mock`
- `auto`: fallback to mock mode when required SSO config is missing
- `sso`: fail startup/bootstrap if required SSO config is missing
- `mock`: enable local mock login flow only

Canonical auth flow blueprint:
- `templates/generated-project/auth/google-sso-clerk-blueprint.md`

## BigQuery Rule

If a feature needs BigQuery-backed data, frontend calls backend API only.
Never connect frontend directly to BigQuery.

## Testing Guidance

When tests are required, prioritize:
- auth mode rendering and switching behavior
- loading/empty/error/success states
- form validation behavior
- key user interactions

Avoid large snapshot tests by default.
