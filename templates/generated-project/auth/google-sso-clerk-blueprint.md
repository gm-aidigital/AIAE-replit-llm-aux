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

## Replit Auth as a fourth mode (`AUTH_MODE=replit`)

[Replit Auth](https://docs.replit.com/additional-resources/replit-auth) is
zero-config inside a Replit workspace: `REPL_ID` (UUID) is the pre-registered
OIDC public client id and `REPLIT_DOMAINS` carries the registered host list.
No client secret, no Clerk Dashboard, no Google OAuth toggling.

Trade-off vs Clerk modes: Replit Auth credentials are **tied to the Replit
workspace**, so the auth layer must be rewritten when exporting to local
docker-compose or a non-Replit deployment. Choose `replit` for fast demos
that won't leave Replit; choose Clerk (managed or standalone) when handoff
includes local-dev parity.

### Replit-mode chain (session cookie, not Bearer)

Unlike Clerk/mock which are stateless Bearer JWTs, Replit mode is
**session-cookie + CSRF** (`JSESSIONID` + `XSRF-TOKEN`). The
`oauth2Login` filter chain replaces the stateless resource-server chain.
Both can't run together — the resource-server chain is gated off by
`AuthConstants.NON_REPLIT_MODE_CONDITION` (SpEL on `app.auth.mode != replit`)
on every JwtDecoder bean and the resource-server `SecurityFilterChain`.

The dedicated `application/security/ReplitOidcSecurityConfig.java` carries:

1. `ClientRegistrations.fromIssuerLocation("https://replit.com/oidc")` for
   OIDC discovery — **never** the static
   `ClientRegistration.withRegistrationId(...).issuerUri(...)` builder (it
   throws `authorizationUri cannot be empty`; the setter only stores the
   issuer claim, it does not trigger discovery).
2. `clientId(REPL_ID)` + `clientAuthenticationMethod(NONE)` → PKCE public client.
3. SPA-friendly CSRF: `CookieCsrfTokenRepository.withHttpOnlyFalse()` +
   plain `CsrfTokenRequestAttributeHandler` (NOT the default `Xor…` one — the
   XOR mask makes the cookie value ≠ header value and every POST 403s).
4. `htmlVsApiEntryPoint`: browser navigations get a 302 to
   `/oauth2/authorization/replit`; XHR/JSON gets `401` so the SPA AuthGate
   can render the sign-in screen without a hard navigation.
5. Tight `PUBLIC_PATHS` (SPA shell + static + `/oauth2/**` + `/login/oauth2/**`
   + `/actuator/health`). When "gate the whole app" is the requirement do NOT
   leave `/actuator/prometheus`, `/swagger-ui/**`, `/v3/api-docs/**`,
   `/api/v1/specs/**` open — unauthenticated visitors can scrape metrics
   and the full OpenAPI surface otherwise.

### Required Spring config for Replit mode

`application.yml` MUST contain:

```yaml
server:
  forward-headers-strategy: framework
```

Spring resolves `{baseUrl}` from the raw servlet request, which inside the
Replit container is `http://localhost:5000`. The IdP rejects the resulting
`redirect_uri` and you get a login loop. `framework` makes Spring honour
`X-Forwarded-Proto` / `X-Forwarded-Host` so `{baseUrl}` resolves to the
public `https://*.replit.dev` host. Harmless when `AUTH_MODE != replit`,
leave it always on.

### Frontend for Replit mode

- No `ClerkProvider`, no token getter. `AuthProvider.tsx`'s `usesClerkAuth()`
  guard already short-circuits to `<>{children}</>` when AUTH_MODE is not SSO.
- All fetches send `credentials: "include"` so the session cookie travels.
- Non-GET fetches read `XSRF-TOKEN` cookie and echo it as `X-XSRF-TOKEN`
  header (see `spring-security-spa-csrf` memory for the canonical snippet).
- On `401` JSON response, the AuthGate component navigates to
  `/oauth2/authorization/replit` (full-page nav, NOT XHR) — Replit's IdP
  redirects back to `/login/oauth2/code/replit` and Spring lands on `/`.
- The mock-mode `pages/Login.tsx` is still shipped (Replit deletes it
  otherwise); it is simply unreachable in Replit mode because the
  authentication entry point redirects to the IdP before the SPA router
  ever sees `/login`.

## Modes (`AUTH_MODE`)

| Value | Behavior | Token transport |
|---|---|---|
| `auto` (default) | Use Clerk SSO when `CLERK_SECRET_KEY` plus `AUTH_ISSUER_URI` or `AUTH_JWKS_URI` are set; fall back to mock otherwise. | Bearer JWT |
| `sso` | Require Clerk keys; fail startup if missing. | Bearer JWT |
| `mock` | Skip external IdP; expose local mock login. | Bearer JWT (backend-signed HS256) |
| `replit` | Replit OIDC (public client + PKCE). Requires `REPL_ID`; no client secret. Session cookie + CSRF, NOT Bearer. | `JSESSIONID` + `X-XSRF-TOKEN` |

Generated projects start in both `mock` and `sso` without code rewrites.
Managed Clerk: keys may be auto-present, but Spring still needs
`AUTH_ISSUER_URI` or `AUTH_JWKS_URI` to validate JWTs. Therefore `auto`
resolves to `sso` only when Clerk secret **and** issuer/JWKS are present;
otherwise it safely remains in mock mode. Explicit `AUTH_MODE=sso` fail-fast
surfaces missing issuer/JWKS instead of booting a half-configured service.

### Auto-mode detection (backend pattern)

On `AUTH_MODE=auto` backend decides via `CLERK_SECRET_KEY` plus issuer/JWKS:

```java
@Configuration
@ConditionalOnProperty(name = "app.auth.mode", havingValue = "auto", matchIfMissing = true)
public class AutoAuthConfig {
    @Configuration
    @ConditionalOnExpression("CLERK_SECRET_KEY present AND (AUTH_ISSUER_URI or AUTH_JWKS_URI present)")
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
  `VITE_*` env vars, so `frontend/vite.config.ts` maps
  `CLERK_PUBLISHABLE_KEY` into `import.meta.env.VITE_CLERK_PUBLISHABLE_KEY`
  when SSO is actually configured.
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

### Authorization roles (REQUIRED for admin/role-based apps)

JWT validation answers "who is this user?". Application authorization answers
"what can this user do?". Do not rely on Spring's default
`JwtGrantedAuthoritiesConverter`: it reads `scope`/`scp`, while mock tokens and
many Clerk JWT templates do not contain those claims. Past generated apps
logged in correctly but returned `403 insufficient_scope` for
`/api/v1/admin/**`.

When the app has roles, admin screens, HR/manager flows, or any
`hasRole(...)`/`hasAuthority(...)` rule:

1. Configure a custom `JwtAuthenticationConverter`.
2. Resolve the canonical user id from the validated JWT email.
3. Load roles server-side from the app role source, normally
   `user_roles.user_id = lower(email)`.
4. Convert them to Spring authorities with the `ROLE_` prefix
   (`ROLE_HR_MANAGER`, `ROLE_EMPLOYEE`, etc.).
5. Use the same converter for mock and SSO tokens.

Frontend role state is display-only. Backend endpoints must authorize from the
validated JWT plus backend role lookup. If no role table exists yet, seed one
with Liquibase before protecting admin endpoints.

### Principal-key contract (REQUIRED)

One claim is canonical principal id; used EVERYWHERE ("who the user is"):
`Authentication#getName()`, seed `user_roles.user_id`, audit/usage event
`user_id`, any "logged-in person" FK.

**Canonical claim: `email`** (lowercased, trimmed).

- `sub` is provider-internal (Clerk user id, Google subject); changes on
  re-provision. Don't key business tables on it.
- `email` is stable for the demo/handoff window; reads naturally in seed/logs.
- Mock mode MUST make `getName()` return lowercased email. `MockTokenService`
  puts `email` into `sub` AND emits an `email` claim → `extractEmail` reads
  it directly.
- Clerk JWTs MUST carry an email claim for `user_email` (and, via
  `JwtAuthenticationConverter#principalClaimName="email"` in `SecurityConfig`,
  for `user_id` / `Authentication#getName()`) to populate. Clerk-side
  configuration to make that happen is out of scope for this template —
  see Clerk's own docs. Backend `extractEmail` accepts `email`,
  `email_address`, `primary_email_address`, or `mail` (first non-blank
  wins), so any of those claim names works without code changes.
- The aspect auto-lifts the user's display name into
  `usage_events.attributes->>'user_name'`. Resolution order:
  `full_name` → `name` → `preferred_username`, then a composed fallback
  of `first_name + last_name` (Clerk template variables) or
  `given_name + family_name` (OIDC standard). When only one half is
  present, that half wins.

  Recommended Clerk JWT template — robust because it ships both the
  pre-computed `full_name` AND the components, so the composed fallback
  kicks in for users whose `{{user.full_name}}` resolves to blank:
  ```json
  {
    "email":      "{{user.primary_email_address}}",
    "full_name":  "{{user.full_name}}",
    "first_name": "{{user.first_name}}",
    "last_name":  "{{user.last_name}}"
  }
  ```
  Resulting row: `user_id` = lowercased email, `user_email` = lowercased
  email, `attributes->>'user_name'` = e.g. `"Gleb Mozhaiskii"`. The
  template's `user_id` claim is unnecessary — backend already uses
  `email` for principal name and JWT `sub` only as silent fallback.

**Hard rules** (#1 source of "logged in but no data" bugs):

1. Every Liquibase-seeded `user_roles.user_id` = the exact lowercased email
   the mock/SSO flow produces. Anti-pattern: seeding `user_id = 'mock-hr-manager'`
   while principal name is `alice.johnson@company.com` → login succeeds,
   authorization silently denies every protected endpoint.
2. Mock-login endpoint accepts the same email values as the seed. Wire
   `AUTH_MOCK_USER` + "demo accounts" to seed emails, not role slugs.
3. Audit/usage event `user_id` stores the same email — cross-table joins
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

Replit Workspace (when `AUTH_MODE=replit`):
```
# Auto-injected by Replit — do not set manually
REPL_ID                  # auto — pre-registered OIDC client_id
REPLIT_DOMAINS           # auto — comma-separated registered host list
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

**Replit Auth mode (`AUTH_MODE=replit`)**
1. Inside a Replit workspace `REPL_ID` is auto-provisioned; start with `AUTH_MODE=replit`.
2. Navigate to any protected page → 302 to `/oauth2/authorization/replit` → Replit IdP → callback to `/login/oauth2/code/replit` → land on `/`.
3. `GET /api/v1/auth/me` → `200` (session cookie carries identity).
4. Sign out: `POST /logout` with `X-XSRF-TOKEN` header → 302 to `/`, `JSESSIONID` + `XSRF-TOKEN` cookies cleared.
5. Unauthenticated XHR to a protected endpoint → `401` JSON (NOT a redirect), so the SPA can render the sign-in CTA.

## Replit Auth (`AUTH_MODE=replit`): hard rules

- **Do NOT** add a Bearer-JWT path for the same chain — the OAuth2 login
  filter and resource-server filter chains cannot both apply. The mock/SSO
  `JwtDecoder` beans MUST be gated off via `NON_REPLIT_MODE_CONDITION`.
- **Do NOT** remove `server.forward-headers-strategy=framework` from
  `application.yml` — login loop guaranteed otherwise.
- **Do NOT** widen `PUBLIC_PATHS` in `ReplitOidcSecurityConfig` to include
  `/actuator/prometheus`, `/swagger-ui/**`, `/v3/api-docs/**`, `/api/v1/specs/**`
  unless the spec explicitly says "metrics and OpenAPI are public" —
  scraping risk.
- **Do NOT** disable CSRF — the chain is session-cookie based; CSRF disable
  opens cross-site request forgery against any authenticated user. Use the
  non-XOR `CsrfTokenRequestAttributeHandler` as documented.
- **Do NOT** key `user_roles.user_id` on `sub` — Replit's `sub` is the
  Replit user id, opaque and not portable. Use `email` (lowercased) like the
  Clerk/mock principal contract above; falls under the same canonical-claim
  rule.
- **Do NOT** rebuild `ClientRegistration` from `withRegistrationId(...).issuerUri(...)`
  — only `ClientRegistrations.fromIssuerLocation(...)` triggers OIDC discovery.
  The static builder leaves authorization/token endpoints empty and Spring throws.

## Replit-managed Clerk: hard rules

- **Do NOT** manually edit `CLERK_PUBLISHABLE_KEY` / `CLERK_SECRET_KEY` in
  Replit Secrets — breaks automatic dev↔prod switch (unsupported by Replit docs).
- **Do NOT** configure Google OAuth in Clerk Dashboard for the managed tenant
  — use Replit's Auth pane.
- Managed Clerk credentials don't export to local docker-compose. Local-dev
  runs in mock mode unless engineering provisions a standalone tenant.
