# Google SSO + Mock Auth Blueprint

> **STACK REMINDER.** Template = Java 21 + Spring Boot 3.x. Replit-managed
> Clerk's "auto-mounted Express middleware" is Node-only — does NOT apply.
> Spring validates Clerk JWTs via `spring-boot-starter-oauth2-resource-server`
> + Clerk JWKS. Re-read `custom_instruction/instructions.md` STACK LOCK if
> tempted toward Flask/Express.

Single source of truth for auth. Other files reference this; never restate.

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

### Choose managed when

- App ships primarily on Replit (Workspace + Deployment).
- Non-technical users should never touch a Clerk Dashboard.
- Same Clerk tenant doesn't need to export to local docker-compose.

### Choose standalone when

- Same app runs on Replit AND engineering's local docker-compose with same identities.
- Custom Clerk branding beyond Replit's Auth pane.
- Compliance requires owning the Clerk tenant on the company's own account.

Template default: **managed-Clerk on Replit + mock-auth locally**. Engineering
swaps mock for standalone Clerk during handoff.

## Preferred architecture

`Google → Clerk → application`. Clerk issues JWT; Spring verifies as OAuth2
Resource Server. JWT validation code path is identical for managed vs standalone.

Direct Google OIDC without Clerk only when project standards require it.

## Skip Clerk entirely — use Replit Auth

For Replit-only demos (no local-dev export, no handoff),
[Replit Auth](https://docs.replit.com/references/auth-and-identity/authentication)
is zero-config — identity handled by Replit. Accept that exporting later
means rewriting the auth layer.

## Modes (`AUTH_MODE`)

| Value | Behavior |
|---|---|
| `auto` (default) | Use Clerk SSO when `CLERK_SECRET_KEY` is set; fall back to mock otherwise. |
| `sso` | Require Clerk keys; fail startup if missing. |
| `mock` | Skip external IdP; expose local mock login. |

Generated projects start in both `mock` (no keys) and `sso` (keys present)
without code rewrites. Managed Clerk: keys auto-present → `auto` resolves
`sso`. Locally: keys absent → `auto` resolves `mock`.

### Auto-mode detection (backend pattern)

On `AUTH_MODE=auto` backend decides via `CLERK_SECRET_KEY` non-empty check:

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

`AUTH_MODE=sso`/`mock` use their own `@Configuration` without
`@ConditionalOnProperty` on `CLERK_SECRET_KEY` — auto-detection is an
implementation detail of the `auto` branch.

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

### Principal-key contract (REQUIRED)

One claim is canonical principal id; used EVERYWHERE ("who the user is"):
`Authentication#getName()`, seed `user_roles.user_id`, audit/usage-event
`user_id`, any "logged-in person" FK.

**Canonical claim: `email`** (lowercased, trimmed).

- `sub` is provider-internal (Clerk user id, Google subject); changes on
  re-provision. Don't key business tables on it.
- `email` is stable for the demo/handoff window; reads naturally in seed/logs.
- Both mock + SSO modes MUST make `getName()` return lowercased email.
  `MockJwtDecoder` puts `email` into `sub` AND sets it as principal name;
  Clerk's JWT carries `email` directly.

**Hard rules** (#1 source of "logged in but no data" bugs):

1. Every Liquibase-seeded `user_roles.user_id` = the exact lowercased email
   the mock/SSO flow produces. Anti-pattern: seeding `user_id = 'mock-hr-manager'`
   while principal name is `alice.johnson@company.com` → login succeeds,
   authorization silently denies every protected endpoint.
2. Mock-login endpoint accepts the same email values as the seed. Wire
   `AUTH_MOCK_USER` + "demo accounts" to seed emails, not role slugs.
3. Audit/usage-event `user_id` stores the same email — cross-table joins
   on `user_id` work without translation.

`mock` mode: `AUTH_MOCK_USER` is an email (e.g. `alice.johnson@company.com`),
never a role name.

Spring config keys:
```
spring.security.oauth2.resourceserver.jwt.issuer-uri
spring.security.oauth2.resourceserver.jwt.jwk-set-uri   # optional
spring.security.oauth2.resourceserver.jwt.audiences
```

Return `401` missing/invalid token; `403` insufficient authority. Never
trust frontend session state alone.

> Replit-managed Clerk's Express middleware is Node-only — irrelevant here.
> Spring's OAuth2 Resource Server filter chain replaces it; same JWT
> contract, Java implementation in `application/.../security/`.

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
Same as above; Clerk keys pasted manually into Replit Secrets / `.env`
from the Clerk Dashboard; tenant on the company's account.

## Replit-managed Clerk: hard rules

- **Do NOT** manually edit `CLERK_PUBLISHABLE_KEY` / `CLERK_SECRET_KEY` in
  Replit Secrets — breaks automatic dev↔prod switch (unsupported by Replit docs).
- **Do NOT** configure Google OAuth in Clerk Dashboard for the managed tenant
  — use Replit's Auth pane.
- Managed Clerk credentials don't export to local docker-compose. Local-dev
  runs in mock mode unless engineering provisions a standalone tenant.
