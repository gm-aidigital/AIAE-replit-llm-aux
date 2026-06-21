---
description: Frontend architecture, API, auth, and styling rules.
paths:
  - "frontend/src/**/*.ts"
  - "frontend/src/**/*.tsx"
  - "frontend/src/**/*.css"
  - "frontend/vite.config.ts"
  - "frontend/package.json"
---

# Frontend Rules

- Use React + TypeScript + Vite.
- Use `openapi-fetch` through `frontend/src/shared/api/client.ts`; raw `fetch`, `axios`, and hardcoded backend URLs are forbidden under `frontend/src`.
- Generated OpenAPI types are not edited manually.
- Use TanStack Query for backend server state.
- Each server resource has one canonical query-key owner. Components reuse that query or its cached data instead of issuing equivalent requests under different keys.
- Cache data according to its lifecycle. Data that is stable for the authenticated session stays cached until account/session change or an explicit successful update; clear user-scoped cache on sign-out and account switch.
- Fetch only when the data is required. Disable queries until their inputs and owning UI surface are active; do not preload hidden tabs, closed dialogs, or unavailable permission branches without a measured reason.
- Mutations update the canonical cache directly when the response is authoritative; otherwise invalidate only affected queries. Broad cache invalidation and refetching unrelated resources are forbidden.
- Polling, eager prefetching, and duplicate detail/list requests require a documented freshness or latency reason. Request count is part of frontend correctness.
- Clerk owns frontend auth; protected calls send Bearer JWT through the shared API client.
- Frontend env vars must not contain secrets.
- Preserve the ports, allowed hosts, runtime alias, and proxy behavior defined by
  the current repository; do not replace them with remembered defaults.
- Preserve the product's established navigation model. For a new project with
  no explicit navigation decision, follow the installed design guidance.
- Use plain CSS with BEM naming. Do not introduce CSS Modules, Tailwind, styled-components, Emotion, or CSS-in-JS.
- Use semantic CSS variables/tokens for colors and spacing instead of new hardcoded color literals.
