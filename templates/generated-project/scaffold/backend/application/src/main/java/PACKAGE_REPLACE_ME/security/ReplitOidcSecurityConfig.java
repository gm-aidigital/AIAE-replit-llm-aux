// Replit Auth (OpenID Connect) security chain.
// Activates when AUTH_MODE=replit. Uses Replit as the OIDC IdP via the
// public-client + PKCE flow (no client secret) — REPL_ID env var is the
// pre-registered OAuth client_id, REPLIT_DOMAINS supplies the host.
//
// GOTCHA: Spring's {baseUrl} placeholder in redirectUri resolves from the
// raw servlet request. Behind Replit's edge proxy that would be
// http://localhost:5000 → IdP rejects redirect_uri mismatch. Fix:
// server.forward-headers-strategy=framework in application.yml so Spring
// honours X-Forwarded-Proto / X-Forwarded-Host.
//
// Full background: templates/generated-project/auth/google-sso-clerk-blueprint.md
// → "Replit Auth as a fourth mode".

package PACKAGE_REPLACE_ME.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.LinkedHashMap;

/**
 * Session-based OIDC chain for Replit Auth. Replaces the stateless
 * resource-server chain in {@link SecurityConfig} (which is disabled by
 * {@link AuthConstants#NON_REPLIT_MODE_CONDITION}) when AUTH_MODE=replit.
 */
@Configuration
@ConditionalOnProperty(name = AuthConstants.AUTH_MODE_PROPERTY,
                       havingValue = AuthConstants.AUTH_MODE_REPLIT)
public class ReplitOidcSecurityConfig {

    /** Spring registration-id; surfaces in /oauth2/authorization/{id} and the callback path. */
    public static final String REGISTRATION_ID = "replit";

    private static final String ISSUER_URI = "https://replit.com/oidc";

    /** Replit-provisioned env var — UUID of this Repl, used as the OIDC client_id. */
    @Value("${REPL_ID:}")
    private String replId;

    /**
     * Minimal anonymous surface: SPA shell + static assets + IdP redirect
     * endpoints + health probe. Everything else (API, actuator/prometheus,
     * swagger, OpenAPI spec) is gated by login per "gate the whole app".
     */
    private static final String[] PUBLIC_PATHS = {
        "/", "/index.html", "/favicon.ico", "/assets/**",
        "/*.css", "/*.js", "/*.png", "/*.svg", "/error",
        "/actuator/health",
        "/oauth2/**", "/login/oauth2/**"
    };

    /**
     * Registers Replit as an OIDC IdP. Public client (no secret) with PKCE
     * — Spring auto-attaches the PKCE code_challenge when
     * {@code clientAuthenticationMethod} is {@code NONE}.
     *
     * <p>Discovery via {@link ClientRegistrations#fromIssuerLocation(String)}
     * is the ONLY path that populates authorization / token / jwks / userinfo
     * / end-session endpoints from {@code /.well-known/openid-configuration}.
     * The static builder ({@code ClientRegistration.withRegistrationId(...)
     * .issuerUri(...)}) just stores the issuer claim and throws
     * {@code authorizationUri cannot be empty} at build time.
     *
     * @return single-entry repository keyed by {@link #REGISTRATION_ID}.
     */
    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
        if (replId == null || replId.isBlank()) {
            throw new IllegalStateException(
                "AUTH_MODE=replit requires REPL_ID env var "
                + "(provisioned automatically inside a Replit workspace).");
        }
        ClientRegistration replit = ClientRegistrations.fromIssuerLocation(ISSUER_URI)
            .registrationId(REGISTRATION_ID)
            .clientId(replId)
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .scope("openid", "profile", "email", "offline_access")
            .userNameAttributeName("sub")
            .clientName("Replit")
            .build();
        return new InMemoryClientRegistrationRepository(replit);
    }

    /**
     * Builds the OIDC login + session security chain. Browser navigations
     * to a protected page are 302-redirected to the IdP; XHR / fetch
     * requests receive 401 JSON so the React {@code AuthGate} can render
     * the sign-in screen without a top-level navigation.
     *
     * @param http Spring Security HTTP builder.
     * @return configured chain.
     * @throws Exception propagated by Spring Security when the chain cannot be built.
     */
    @Bean
    SecurityFilterChain replitSecurityFilterChain(HttpSecurity http) throws Exception {
        // SPA-friendly CSRF: cookie is readable by JS (XSRF-TOKEN) and the
        // non-XOR handler lets the cookie value match the X-XSRF-TOKEN
        // header byte-for-byte — the canonical Spring Security 6 SPA setup.
        // Default XorCsrfTokenRequestAttributeHandler masks the token per
        // response → cookie != header → every POST 403s.
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName(null);
        return http
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(csrfHandler))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_PATHS).permitAll()
                .anyRequest().authenticated())
            .oauth2Login(o -> o.defaultSuccessUrl("/", true))
            // Spring default: POST /logout, CSRF-protected. Do NOT re-enable
            // GET /logout — allows image-tag cross-site forced logout.
            .logout(lo -> lo
                .logoutSuccessUrl("/")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID", "XSRF-TOKEN"))
            .exceptionHandling(eh -> eh.authenticationEntryPoint(htmlVsApiEntryPoint()))
            .build();
    }

    /**
     * Routes auth failures: HTML browser nav → OAuth login redirect;
     * everything else (XHR/JSON) → 401 so the SPA can react gracefully.
     *
     * @return delegating entry point with HTML / default split.
     */
    private static AuthenticationEntryPoint htmlVsApiEntryPoint() {
        MediaTypeRequestMatcher htmlMatcher = new MediaTypeRequestMatcher(MediaType.TEXT_HTML);
        htmlMatcher.setUseEquals(false);
        LinkedHashMap<RequestMatcher, AuthenticationEntryPoint> entries = new LinkedHashMap<>();
        entries.put(htmlMatcher,
            new LoginUrlAuthenticationEntryPoint("/oauth2/authorization/" + REGISTRATION_ID));
        DelegatingAuthenticationEntryPoint entryPoint = new DelegatingAuthenticationEntryPoint(entries);
        entryPoint.setDefaultEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED));
        return entryPoint;
    }
}
