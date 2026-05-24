# Google SSO + Mock Auth Blueprint

> **STACK REMINDER.** This template generates **Java 21 LTS + Spring Boot 3.x**
> backends. Nothing in this document changes that. Replit-managed Clerk's
> "auto-mounted Express middleware" is for Node apps and **does not apply**
> here. Our Spring backend validates Clerk JWTs via
> `spring-boot-starter-oauth2-resource-server` + Clerk JWKS — no Express,
> no Node, no Python. If reading this blueprint makes you think "Flask would
> be simpler" or "let me use Express for the middleware path", stop and
> re-read `replit.md` STACK LOCK and `instructions.md` ABSOLUTE STACK LOCK.

Single source of truth for auth in generated projects. Other files reference
this one; do not restate the contract elsewhere.

## Two ways to wire Clerk on Replit

| | Replit-managed Clerk Auth | Standalone Clerk Dashboard |
|---|---|---|
| Tenant | Auto-provisioned by Replit per app (dev + prod separate). | Created manually in Clerk Dashboard. |
| Keys | `CLERK_PUBLISHABLE_KEY` and `CLERK_SECRET_KEY` **auto-injected** as Replit Secrets; dev↔prod switched automatically on Deploy. | Copied manually from Clerk Dashboard into Replit Secrets / `.env`. |
| Google social login | Toggle in Replit's **Auth pane** (no Clerk Dashboard touch needed). | Configured in Clerk Dashboard. |
| Manual Secret editing | **Forbidden** — breaks the integration. | Allowed. |
| Portability to local-dev / docker-compose | Not supported. Credentials are tied to the Replit workspace. | Fully portable. |
| Frontend SDK | `@clerk/clerk-react` with `<ClerkProvider>` (Replit injects the publishable key automatically). | Same `@clerk/clerk-react`, but you set `VITE_CLERK_PUBLISHABLE_KEY` yourself. |
| Backend (**Spring Boot — the only allowed backend**) | Replit's auto-wired *Node/Express* middleware is **irrelevant** here. Spring verifies JWTs via `spring-boot-starter-oauth2-resource-server` + Clerk's JWKS endpoint. Code path is the same whether Clerk is Replit-managed or standalone. | Same Spring path. |

### Choose the managed path when

- The app will ship primarily on Replit (Workspace + Deployment).
- Non-technical users / managers should never have to touch a Clerk Dashboard.
- You don't need to export the same Clerk tenant to a local docker-compose
  environment.

### Choose the standalone Clerk Dashboard path when

- The same app must run on Replit AND on engineering's local docker-compose
  with the same identities.
- You want custom Clerk branding/templates beyond what Replit's Auth pane exposes.
- Compliance requires owning the Clerk tenant on the company's own Clerk account.

The template defaults to **managed-Clerk on Replit + mock-auth locally**.
That keeps the Replit demo zero-config while the local-dev path stays
isolated (mock JWT). Engineering can later swap mock for standalone Clerk
during handoff.

## Preferred architecture

`Google → Clerk → application`. Clerk issues the JWT; Spring verifies it as
an OAuth2 Resource Server. Whether Clerk is Replit-managed or standalone is
an operational detail — the JWT validation code path is identical.

Direct Google OIDC without Clerk is allowed only when project standards
explicitly require it.

## When to skip Clerk entirely and use Replit Auth

If the generated app will only ever ship on Replit (no local-dev export, no
engineering handoff), [Replit Auth](https://docs.replit.com/references/auth-and-identity/authentication)
is the simplest path — identity is handled by Replit itself, no Clerk at
all. Reach for it only when the demo is Replit-only and you accept that
exporting later means rewriting the auth layer.

## Modes (`AUTH_MODE`)

| Value | Behavior |
|---|---|
| `auto` (default) | Use Clerk SSO when `CLERK_SECRET_KEY` is set; fall back to mock otherwise. |
| `sso` | Require Clerk keys; fail startup if missing. |
| `mock` | Skip external IdP; expose local mock login. |

Generated projects must start in both `mock` (no keys) and `sso` (keys present)
without code rewrites. On Replit-managed Clerk, the keys are automatically
present in Workspace and Deployment, so `auto` resolves to `sso`. Locally
(docker-compose) the keys are absent, so `auto` resolves to `mock`.

### Auto-mode detection (backend, mandatory implementation pattern)

On `AUTH_MODE=auto` the backend MUST decide between SSO and mock by checking
whether `CLERK_SECRET_KEY` is non-empty. Standard Spring pattern:

```java
@Configuration
@ConditionalOnProperty(name = "AUTH_MODE", havingValue = "auto", matchIfMissing = true)
public class AutoAuthConfig {
    @Configuration
    @ConditionalOnProperty(name = "CLERK_SECRET_KEY")
    static class WhenClerkKeysPresent { /* enable OAuth2ResourceServer chain */ }

    @Configuration
    @ConditionalOnMissingBean(name = "clerkJwtDecoder")
    static class WhenClerkKeysMissing { /* enable MockAuthConfig */ }
}
```

`AUTH_MODE=sso` and `AUTH_MODE=mock` use their own `@Configuration` classes
without `@ConditionalOnProperty` on `CLERK_SECRET_KEY`. This makes the
auto-detection an implementation detail of the `auto` branch only.

## Frontend contract

- Login screen always renders.
- Mock mode: local form requesting demo-safe identity fields only.
- SSO mode: Clerk SDK (`@clerk/clerk-react`) with `<ClerkProvider>` reading
  the publishable key. On Replit-managed Clerk, the publishable key is
  injected by Replit as `CLERK_PUBLISHABLE_KEY` — Vite normally only exposes
  `VITE_*` env vars, so the frontend reads it through Replit's runtime
  injection mechanism (see Replit Auth pane docs) OR an alias
  `VITE_CLERK_PUBLISHABLE_KEY=$CLERK_PUBLISHABLE_KEY` set in the workflow.
- Protected calls send `Authorization: Bearer <jwt>` (token from Clerk
  `useAuth().getToken()` in SSO mode, from `POST /api/v1/auth/mock/login`
  in mock mode).
- Backend is source of truth. `GET /api/v1/auth/me` bootstraps state.
- `401` → clear local state + redirect to login. `403` → access-denied UI.
- Never store secrets in frontend env.

## Backend contract

Dependencies: `spring-boot-starter-security`,
`spring-boot-starter-oauth2-resource-server`.

Protected endpoints require Bearer JWT. Backend validates:
- signature against IdP JWKS (Clerk's JWKS endpoint for managed Clerk too —
  Replit doesn't change this path),
- `iss`, `aud`, `exp`, and `nbf` when present.

Provider-specific:
- **Clerk** (managed or standalone): `iss` = Clerk issuer URL for the
  tenant; `aud` = Clerk token audience/template.
  For Replit-managed Clerk, the issuer is discoverable from `CLERK_SECRET_KEY`
  via the Clerk Backend API, OR set `AUTH_ISSUER_URI` explicitly as a Secret.
- **Direct Google OIDC**: `iss` = Google issuer; `aud` = `GOOGLE_CLIENT_ID`.

Map trusted claims (`sub`, `email`, roles/groups) into the application principal.

Spring config keys:
```
spring.security.oauth2.resourceserver.jwt.issuer-uri
spring.security.oauth2.resourceserver.jwt.jwk-set-uri   # optional
spring.security.oauth2.resourceserver.jwt.audiences
```

Return `401` for missing/invalid token; `403` for insufficient authority.
Never treat frontend session state alone as proof of authentication.

> **Note for Replit-managed Clerk.** Replit auto-mounts an Express middleware
> for Node apps. **We do not use Node anywhere in this template.** Spring's
> OAuth2 Resource Server filter chain replaces that middleware completely.
> The JWT-validation contract is the same; only the implementation lives
> in `application/.../security/` Java, not in `middleware.js`.

## Endpoints

Public:
- `GET /<app-context-path>/actuator/health`
- `GET /<app-context-path>/actuator/prometheus`
- `POST /api/v1/auth/mock/login` — exists only in `mock` mode (or `auto` without keys)

Protected:
- `GET /api/v1/auth/me` — canonical authenticated-user payload, identical
  shape in both modes.

## Mock mode

- Issues a short-lived backend-signed JWT.
- Same Bearer-token wire contract as SSO mode.
- Mock user identity and signing secret read from properties/env.

## Required env placeholders

Replit Workspace + Deployment (when using managed Clerk):
```
# Auto-injected by Replit — do not set manually
CLERK_PUBLISHABLE_KEY    # auto
CLERK_SECRET_KEY         # auto
```

Backend (read in `application.yml` / `application-<profile>.yml`):
```
AUTH_MODE                # auto|sso|mock
AUTH_ISSUER_URI          # Clerk tenant issuer URL (or empty in mock)
AUTH_JWKS_URI            # optional, derived from issuer if blank
AUTH_AUDIENCE            # Clerk token audience
AUTH_MOCK_USER           # demo user identity for mock mode
AUTH_MOCK_JWT_SECRET     # signing secret for mock JWTs
```

Frontend-readable (Vite exposes only vars prefixed `VITE_*` to the browser):
```
VITE_AUTH_MODE
VITE_CLERK_PUBLISHABLE_KEY     # alias of CLERK_PUBLISHABLE_KEY (set in workflow)
VITE_CLERK_SIGN_IN_FORCE_REDIRECT_URL
VITE_CLERK_SIGN_UP_FORCE_REDIRECT_URL
```

External provider setup (only for standalone Clerk Dashboard path, not the
managed integration):
```
GOOGLE_CLIENT_ID
GOOGLE_CLIENT_SECRET
```

Never inject the Google client secret into the browser.

## OpenAPI requirements

- Bearer security scheme in `components.securitySchemes`.
- `GET /api/v1/auth/me` documented.
- `POST /api/v1/auth/mock/login` documented when mock mode exists.
- Explicit `401` and `403` responses on protected operations.
- Shared error schemas for auth/validation failures.

## Acceptance checks

**Mock mode**
1. Start with `AUTH_MODE=mock` (or `auto` without Clerk keys).
2. `POST /api/v1/auth/mock/login` → returns JWT.
3. `GET /api/v1/auth/me` with token → `200`. Without → `401`.

**SSO mode — Replit-managed Clerk**
1. Enable Clerk Auth in Replit's Auth pane; toggle Google as a provider.
2. Click Run; Replit auto-injects `CLERK_PUBLISHABLE_KEY` / `CLERK_SECRET_KEY`.
3. Sign in via Google through Clerk; the React SDK obtains a JWT.
4. `GET /api/v1/auth/me` → `200`.
5. Protected endpoint: `200` with valid token, `401` without, `403` for insufficient authority.

**SSO mode — standalone Clerk Dashboard**
Same as above, but Clerk keys are pasted manually into Replit Secrets / `.env`
from the Clerk Dashboard, and the tenant lives on the company's Clerk
account rather than Replit's.

## Replit-managed Clerk: hard rules

- **Do NOT** manually edit `CLERK_PUBLISHABLE_KEY` / `CLERK_SECRET_KEY` in
  the Replit Secrets pane. Doing so breaks the automatic dev↔prod switch
  and is explicitly unsupported by Replit's docs.
- **Do NOT** configure Google OAuth inside the Clerk Dashboard for the
  Replit-managed tenant — use Replit's Auth pane.
- Managed Clerk credentials cannot be exported to local docker-compose.
  Local-dev runs in mock mode unless a separate standalone-Clerk tenant is
  provisioned for engineering.
