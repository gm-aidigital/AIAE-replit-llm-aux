// Single home for auth-related literal strings, so @Profile / @ConditionalOnProperty
// annotations and runtime checks share one definition. Annotation parameters
// must be compile-time constants, so use String constants (not properties).

package PACKAGE_REPLACE_ME.security;

public final class AuthConstants {

    private AuthConstants() {}

    /** Spring property name. */
    public static final String AUTH_MODE_PROPERTY = "app.auth.mode";

    /** Property values. */
    public static final String AUTH_MODE_AUTO = "auto";
    public static final String AUTH_MODE_SSO  = "sso";
    public static final String AUTH_MODE_MOCK = "mock";

    /**
     * SpEL expression for "AUTH_MODE=auto AND Clerk keys present". Used via
     * @ConditionalOnExpression on the auto-mode SSO JwtDecoder bean.
     *
     * Why SpEL and not @ConditionalOnProperty: @ConditionalOnProperty is NOT
     * repeatable, so two annotations on one bean silently drop the second.
     * The env var `CLERK_SECRET_KEY` is resolved via Spring's
     * SystemEnvironmentPropertySource (uppercase env vars are queryable
     * with their original name via the ${...} placeholder).
     */
    public static final String SSO_AUTO_CONDITION =
        "'${app.auth.mode:auto}' == 'auto' and '${CLERK_SECRET_KEY:}' != ''";

    /** Path patterns that must remain public. */
    public static final String[] PUBLIC_PATHS = {
        "/actuator/health",
        "/actuator/prometheus",
        "/api/v1/auth/mock/login",
        "/api/v1/specs/**",
        "/swagger-ui/**",
        "/v3/api-docs/**"
    };

    /** Mock JWT header constants. */
    public static final String MOCK_JWT_ISSUER   = "replit-mvp-mock";
    public static final long   MOCK_JWT_TTL_SECS = 3600L;
}
