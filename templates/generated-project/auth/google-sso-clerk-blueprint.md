# Google SSO + Mock Auth Blueprint

Use this as the canonical auth contract for generated full-stack Java backend MVPs.

## Preferred Architecture

- Preferred provider: Clerk with Google social connection enabled.
- Google OAuth is handled by Clerk.
- Frontend authenticates user through Clerk UI/SDK.
- Backend validates JWT as OAuth2 Resource Server.
- Mock mode exists for local/demo startup without external credentials.

Direct Google OIDC without Clerk is allowed only if project standards explicitly require it.

## Required Modes

`AUTH_MODE` must support:

- `auto`: use Clerk SSO when required Clerk settings exist; otherwise fall back to mock mode
- `sso`: require Clerk SSO settings and fail fast on startup when they are missing
- `mock`: disable external IdP dependency and use local mock login flow

## Local Startup Contract

Generated project must be usable locally in both cases:

- without SSO keys: login through mock flow and enter the application
- with Clerk development keys: sign in through Google/Clerk and enter the application

Clerk development instances support Google social login with shared OAuth credentials and redirect URIs. Production requires custom Google OAuth credentials configured in Clerk.

## Frontend Contract

- Use Clerk as the frontend auth SDK when SSO mode is enabled.
- Render a visible login screen.
- Provide a Google sign-in option through Clerk.
- In mock mode, render a local login form that requests only demo-safe identity fields.
- Never store secrets in frontend config.

Protected API calls:

- same-origin: rely on Clerk-authenticated request behavior when applicable
- cross-origin: attach `Authorization: Bearer <jwt>` using Clerk `getToken()`

Frontend auth state:

- backend is the source of truth
- `GET /api/v1/auth/me` is the canonical bootstrap endpoint
- `401` clears local session state and redirects to login
- `403` shows access denied state

## Backend Contract

Required dependencies:

- `spring-boot-starter-security`
- `spring-boot-starter-oauth2-resource-server`

Protected APIs must require Bearer JWT.

Backend must validate:

- JWT signature against IdP JWKS
- `iss`
- `aud`
- `exp`
- `nbf` when present

Provider-specific rules:

- Clerk mode:
  - issuer from Clerk issuer URL
  - audience from configured Clerk token audience/template
- direct Google OIDC mode:
  - issuer from Google
  - audience must equal `GOOGLE_CLIENT_ID`

Backend must map trusted claims into application principal:

- `sub`
- `email`
- roles/groups/permissions claim defined by project

## Required Endpoints

Public:

- `GET /<app-context-path>/actuator/health`
- `GET /<app-context-path>/actuator/prometheus`
- `POST /api/v1/auth/mock/login` in `mock` mode only

Protected:

- `GET /api/v1/auth/me`

`GET /api/v1/auth/me` must return the authenticated user payload in both `mock` and `sso` modes.

## Mock Mode Contract

- mock login endpoint exists only in `mock` mode, or in `auto` when SSO settings are absent
- mock login issues short-lived backend-signed JWT
- mock JWT uses the same Bearer-token contract as real SSO mode
- mock user identity and signing secret are read from properties/env

## Required App Runtime Placeholders

- `AUTH_MODE`
- `AUTH_ISSUER_URI`
- `AUTH_JWKS_URI`
- `AUTH_AUDIENCE`
- `AUTH_MOCK_USER`
- `AUTH_MOCK_JWT_SECRET`
- `CLERK_PUBLISHABLE_KEY`
- `CLERK_SECRET_KEY`
- `CLERK_SIGN_IN_FORCE_REDIRECT_URL`
- `CLERK_SIGN_UP_FORCE_REDIRECT_URL`

## External Provider Setup Values

These are not normal application runtime secrets in the preferred Clerk flow:

- `GOOGLE_CLIENT_ID`
- `GOOGLE_CLIENT_SECRET`

Rules:

- for preferred `Google -> Clerk -> app` flow, Google OAuth client credentials are configured in Clerk Dashboard
- do not require frontend runtime to read Google client secret
- do not inject Google client secret into browser code
- expose Google OAuth values in project docs/operator checklist only when the deployment team must configure Clerk or direct Google OIDC

## OpenAPI Contract

OpenAPI must contain:

- global or per-operation bearer security scheme
- `GET /api/v1/auth/me`
- `POST /api/v1/auth/mock/login` when mock mode exists
- explicit `401` and `403` responses for protected endpoints
- shared error schemas for auth and validation failures

## Acceptance Checks

Mock mode:

1. Start app with `AUTH_MODE=mock`.
2. Call `POST /api/v1/auth/mock/login`.
3. Use returned JWT against `GET /api/v1/auth/me`.
4. Verify protected business endpoint returns `200` with token and `401` without token.

SSO mode:

1. Start app with valid Clerk configuration and Google social connection enabled.
2. Sign in through Google via Clerk UI.
3. Frontend obtains JWT and sends Bearer token to backend.
4. `GET /api/v1/auth/me` succeeds.
5. Protected business endpoint returns `200` with valid token, `401` without token, `403` for insufficient authority.
