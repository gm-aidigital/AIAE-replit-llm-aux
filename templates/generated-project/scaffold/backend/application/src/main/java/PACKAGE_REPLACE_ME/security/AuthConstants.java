// Single home for auth-related literal strings, so @Profile / @ConditionalOnProperty
// annotations and runtime checks share one definition. Annotation parameters
// must be compile-time constants, so use String constants (not properties).

package PACKAGE_REPLACE_ME.security;

/**
 * Shared constants for auth configuration, mock JWTs, and public routes.
 */
public final class AuthConstants {

    private AuthConstants() { }

    /** Spring property name. */
    public static final String AUTH_MODE_PROPERTY = "app.auth.mode";

    /** Property values. */
    public static final String AUTH_MODE_AUTO = "auto";
    public static final String AUTH_MODE_SSO = "sso";
    public static final String AUTH_MODE_MOCK = "mock";
    /** Replit OIDC mode — public client, PKCE, session cookie. Separate filter chain. */
    public static final String AUTH_MODE_REPLIT = "replit";

    /**
     * SpEL: "AUTH_MODE != replit". Gates the stateless resource-server chain
     * and every {@code JwtDecoder} bean off when Replit OIDC is active —
     * Replit mode runs a session-cookie {@code oauth2Login} chain in
     * {@code ReplitOidcSecurityConfig} and the two filter chains MUST NOT
     * both apply (Bearer + session cookie on the same path = ambiguous
     * principal, plus the OAuth2 resource-server auto-config crashes startup
     * when no decoder is needed but the chain is wired).
     */
    public static final String NON_REPLIT_MODE_CONDITION =
        "'${app.auth.mode:auto}' != 'replit'";

    /**
     * SpEL expression for "AUTH_MODE=auto AND Clerk keys plus issuer/JWKS are present".
     * Used via @ConditionalOnExpression on the auto-mode SSO JwtDecoder bean.
     *
     * Why SpEL and not @ConditionalOnProperty: @ConditionalOnProperty is NOT
     * repeatable, so two annotations on one bean silently drop the second.
     * The env var `CLERK_SECRET_KEY` is resolved via Spring's
     * SystemEnvironmentPropertySource (uppercase env vars are queryable
     * with their original name via the ${...} placeholder). Issuer/JWKS is
     * required because Spring cannot validate Clerk JWTs from only the secret key.
     */
    public static final String SSO_AUTO_CONDITION =
        "'${app.auth.mode:auto}' == 'auto' "
            + "and '${CLERK_SECRET_KEY:}' != '' "
            + "and ('${app.auth.sso.issuer-uri:}' != '' or '${app.auth.sso.jwk-set-uri:}' != '')";

    /** Path patterns that must remain public. */
    public static final String[] PUBLIC_PATHS = {
        "/",
        "/index.html",
        "/favicon.ico",
        "/assets/**",
        "/error",
        "/*.css",
        "/*.js",
        "/*.png",
        "/*.svg",
        "/login",
        "/login/**",
        "/actuator/health",
        "/actuator/prometheus",
        "/api/v1/auth/mock/login",
        "/api/v1/specs/**",
        "/swagger-ui/**",
        "/v3/api-docs/**"
    };

    /** Mock JWT header constants. */
    public static final String MOCK_JWT_ISSUER = "replit-mvp-mock";
    public static final long MOCK_JWT_TTL_SECS = 3600L;
}
