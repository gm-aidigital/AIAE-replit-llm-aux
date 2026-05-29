# Clerk SSO Auth Blueprint

> **SSO-ONLY (authoritative).** This template supports **Clerk SSO only** — the
> mock, auto, and replit modes have been REMOVED. The backend validates Clerk
> JWTs against the Clerk JWKS (`spring-boot-starter-oauth2-resource-server`) and
> fails fast at startup when `AUTH_ISSUER_URI`/`AUTH_JWKS_URI` is unset. Do NOT
> generate mock-login, `MockJwtDecoder`, or Replit-OIDC code.

> **STACK REMINDER.** Template = Java 21 + Spring Boot 3.x. Replit-managed
> Clerk's "auto-mounted Express middleware" is Node-only — it does NOT apply.
> Spring validates Clerk JWTs via `spring-boot-starter-oauth2-resource-server`
> + Clerk JWKS. Re-read `custom_instruction/instructions.md` STACK LOCK if
> tempted toward Flask/Express.

Single source of truth for auth. Other files reference this; never restate.

## Two ways to wire Clerk on Replit

| | Replit-managed Clerk Auth | Standalone Clerk Dashboard |
|---|---|---|
| Tenant | Auto-provisioned by Replit per app (dev + prod separate). | Created manually in Clerk Dashboard. |
| Keys | `CLERK_PUBLISHABLE_KEY` + `CLERK_SECRET_KEY` **auto-injected** as Replit Secrets; dev↔prod switched automatically on Deploy. | Copied manually from the Dashboard into Replit Secrets / `.env`. |
| Google social login | Toggle in Replit's **Auth pane**. | Configured in Clerk Dashboard. |
| Manual Secret editing | **Forbidden** — breaks the integration. | Allowed. |
| Portability to local docker-compose | Not portable (tied to the workspace). | Fully portable. |
| Frontend SDK | `@clerk/clerk-react` via `AuthProvider` in `app/AppRoot.tsx` (not raw `<ClerkProvider>` in `main.tsx`). | Same pattern; set `VITE_CLERK_PUBLISHABLE_KEY` in `.env` for local-dev. |
| Backend (Spring Boot) | Spring verifies JWTs via `oauth2-resource-server` + Clerk JWKS. | Same Spring path. |

**Choose managed** when the app ships primarily on Replit and you don't need the
same Clerk tenant in local docker-compose. **Choose standalone** when the app
must run on Replit AND engineering's local docker-compose with the same
identities, or compliance requires owning the tenant. Managed-Clerk credentials
do not export to docker-compose — for local parity, provision a standalone
tenant and set `AUTH_ISSUER_URI` / `CLERK_*` in `.env`.

## Preferred architecture

`Google → Clerk → application`. Clerk issues the JWT; Spring verifies it as an
OAuth2 Resource Server (signature against Clerk JWKS, plus `iss` / `aud` / `exp`
/ `nbf`). Direct Google OIDC without Clerk only when project standards require it.

## Backend contract

Dependencies: `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server`.

`SecurityConfig` provides a single `@Bean JwtDecoder` (a `NimbusJwtDecoder` built
from `app.auth.sso.jwk-set-uri`, or discovered from `issuer-uri`) and a stateless
filter chain. It **fails fast** at startup if neither issuer nor JWKS is set —
there is no fallback. Never set `spring.security.oauth2.resourceserver.jwt.*` in
YAML (auto-config crashes on an empty issuer); the bean owns the decoder.

Protected endpoints require a Bearer JWT. The backend validates:
- signature against the Clerk JWKS endpoint,
- `iss`, `aud` (when configured), `exp`, and `nbf`.

For managed Clerk, set `AUTH_ISSUER_URI` explicitly as a Secret (`iss` = the
tenant issuer URL; `aud` = the Clerk token audience/template when used).

### Principal-key contract (REQUIRED)

One claim is the canonical principal id, used EVERYWHERE ("who the user is"):
`Authentication#getName()`, seed `user_roles.user_id`, audit /
`usage_events.user_id`, any "logged-in person" FK.

**Canonical claim: `email`** (lowercased, trimmed).
- `sub` is provider-internal (Clerk user id); it changes on re-provision — do
  not key business tables on it.
- `email` is stable and reads naturally in seed rows / logs.
- Clerk JWTs MUST carry an email claim. `SecurityConfig` sets
  `JwtAuthenticationConverter#principalClaimName="email"`, so
  `Authentication#getName()` (→ the `user_id` column) returns the email, falling
  back to `sub` only when the claim is absent.
- The usage aspect lifts the display name from the JWT (`full_name` → `name` →
  `preferred_username`, then composed `first_name`+`last_name` /
  `given_name`+`family_name`) into `usage_events.attributes->>'user_name'`.

  Recommended Clerk JWT template:
  ```json
  {
    "email":      "{{user.primary_email_address}}",
    "full_name":  "{{user.full_name}}",
    "first_name": "{{user.first_name}}",
    "last_name":  "{{user.last_name}}"
  }
  ```

**Hard rules** (the #1 source of "logged in but no data" bugs):
1. Every Liquibase-seeded `user_roles.user_id` equals the exact lowercased email
   the Clerk flow produces. Seeding a role slug (e.g. `mock-hr-manager`) while the
   principal name is `alice@company.com` → login succeeds, authorization denies
   every protected endpoint.
2. Audit / usage `user_id` stores the same email — cross-table joins on `user_id`
   work without translation.

### Authorization roles (REQUIRED for admin/role-based apps)

JWT validation answers "who is this user?"; app authorization answers "what can
they do?". Do not rely on Spring's default `JwtGrantedAuthoritiesConverter` (it
reads `scope`/`scp`, which Clerk templates usually omit → `403 insufficient_scope`).

When the app has roles, admin screens, or any `hasRole(...)` rule:
1. Configure a custom `JwtAuthenticationConverter`.
2. Resolve the canonical user id from the validated JWT email.
3. Load roles server-side (`user_roles.user_id = lower(email)`).
4. Convert them to Spring authorities with the `ROLE_` prefix.

Frontend role state is display-only; the backend authorizes from the validated
JWT + backend role lookup. Seed a role table with Liquibase before protecting
admin endpoints.

## Frontend contract

- The sign-in screen renders Clerk's `<SignIn/>` (the only auth mode);
  `AuthProvider` wraps the app in `<ClerkProvider>` reading the publishable key.
  On Replit-managed Clerk, `vite.config.ts` maps `CLERK_PUBLISHABLE_KEY` →
  `import.meta.env.VITE_CLERK_PUBLISHABLE_KEY`.
- Protected calls send `Authorization: Bearer <jwt>` — the token comes from Clerk
  `useAuth().getToken()`, bridged into `runtime.ts` by `AuthProvider`.
- Backend is source of truth. `GET /api/v1/auth/me` bootstraps user state.
- `401` → clear local state + redirect to sign-in. `403` → access-denied UI.
- Never store secrets in frontend env.

## Endpoints

Public: `GET /<app-context-path>/actuator/health`,
`GET /<app-context-path>/actuator/prometheus`.
Protected: `GET /api/v1/auth/me` — canonical authenticated-user payload.

## Required env placeholders

Replit Workspace + Deployment (managed Clerk — auto-injected, do not edit):
```
CLERK_PUBLISHABLE_KEY    # auto
CLERK_SECRET_KEY         # auto
```
Backend (read in `application.yml`):
```
AUTH_ISSUER_URI          # Clerk tenant issuer URL — REQUIRED (app fails fast without issuer or JWKS)
AUTH_JWKS_URI            # optional; derived from issuer if blank
AUTH_AUDIENCE            # Clerk token audience (optional)
```
Frontend-readable (Vite exposes only `VITE_*`):
```
VITE_CLERK_PUBLISHABLE_KEY
VITE_CLERK_SIGN_IN_FORCE_REDIRECT_URL
VITE_CLERK_SIGN_UP_FORCE_REDIRECT_URL
```
External provider setup (standalone Clerk Dashboard only — configured inside
Clerk, never injected into the browser): `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`.

## OpenAPI requirements

- `bearerAuth` security scheme in `components.securitySchemes`.
- `GET /api/v1/auth/me` documented with explicit `401` and `403` responses.
- Shared error schemas for auth/validation failures.

## Acceptance checks

1. Enable Clerk Auth in Replit's Auth pane (or paste standalone keys); toggle Google.
2. Set `AUTH_ISSUER_URI` (managed: from the tenant; standalone: from the Dashboard).
3. Sign in via Google through Clerk; the React SDK obtains a JWT.
4. `GET /api/v1/auth/me` → `200` with a valid token, `401` without, `403` for insufficient authority.
5. Start the backend with no issuer/JWKS → it **fails fast** (no silent unauthenticated mode).

## Replit-managed Clerk: hard rules

- **Do NOT** manually edit `CLERK_PUBLISHABLE_KEY` / `CLERK_SECRET_KEY` in Replit
  Secrets — it breaks the automatic dev↔prod switch.
- **Do NOT** configure Google OAuth in the Clerk Dashboard for the managed tenant
  — use Replit's Auth pane.
- Managed Clerk credentials don't export to local docker-compose; provision a
  standalone tenant for local parity.
