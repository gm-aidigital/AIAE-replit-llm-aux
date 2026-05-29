// SecurityConfig — stateless Bearer-JWT auth chain.
// AUTH_MODE=sso    → Clerk JwtDecoder against issuer JWKS.
// AUTH_MODE=mock   → MockJwtDecoder (HS256, backend-signed).
// AUTH_MODE=auto   → SSO if CLERK_SECRET_KEY plus issuer/JWKS are set, else mock.
// AUTH_MODE=replit → DISABLED HERE; ReplitOidcSecurityConfig owns the chain
//                    (session-cookie oauth2Login, not Bearer). Every bean
//                    below is gated by AuthConstants.NON_REPLIT_MODE_CONDITION
//                    so the two chains never coexist.
// Single SecurityFilterChain; only JwtDecoder bean differs per Bearer mode.
//
// GOTCHA: OAuth2 resource-server auto-config triggers on empty issuer-uri
// and crashes startup. Fix: always provide @Bean JwtDecoder (hard-fallback
// branch below); never set spring.security.oauth2.resourceserver.jwt.* in YAML.
// Full: `.agents/skills/backend-java-feature/references/spring-boot-gotchas.md`
// → "OAuth2 Resource Server auto-config".

package PACKAGE_REPLACE_ME.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Configures stateless API security, JWT decoder selection, CORS, and browser security headers.
 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfig {

    /**
     * Comma-separated allow-list for CORS Origin header. Defaults match the
     * Replit workspace + common local-dev ports. Override via env
     * `APP_SECURITY_CORS_ALLOWED_ORIGINS` for staging/prod.
     */
    @Value("${app.security.cors.allowed-origins:"
        + "https://*.replit.dev,https://*.repl.co,http://localhost:5173,http://localhost:5000}")
    private String corsAllowedOrigins;

    /**
     * CSP `frame-ancestors` directive — controls which parent pages may
     * embed the SPA in an iframe. Default `*` honors the company guideline
     * "embeddable in the central platform"; tighten to specific origins in
     * deployment via env `APP_SECURITY_CSP_FRAME_ANCESTORS`.
     */
    @Value("${app.security.csp.frame-ancestors:'self' https://*.replit.dev https://*.repl.co}")
    private String cspFrameAncestors;

    /**
     * Builds the single stateless security chain for API and actuator endpoints.
     *
     * @param http Spring Security HTTP builder
     * @return configured security filter chain
     * @throws Exception propagated by Spring Security when the chain cannot be built
     */
    @Bean
    @ConditionalOnExpression(AuthConstants.NON_REPLIT_MODE_CONDITION)
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Security headers — explicit policy, no Spring defaults relied on.
            // Iframe embedding REQUIRED by company guidelines → X-Frame-Options
            // disabled, CSP frame-ancestors carries the policy instead.
            .headers(h -> h
                .frameOptions(frame -> frame.disable())
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; "
                    + "frame-ancestors " + cspFrameAncestors + "; "
                    + "script-src 'self' 'unsafe-inline'; "
                    + "style-src 'self' 'unsafe-inline'; "
                    + "img-src 'self' data: https:; "
                    + "connect-src 'self' https:; "
                    + "font-src 'self' data:"))
                .referrerPolicy(r -> r.policy(
                    ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .contentTypeOptions(opts -> { })        // X-Content-Type-Options: nosniff
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31_536_000L)))     // 1 year
            .authorizeHttpRequests(auth -> auth
                // PUBLIC_PATHS are application-relative (Spring Security
                // already strips the servlet context-path before matching),
                // so they do NOT include "/<context-path>/" — see
                // AuthConstants.PUBLIC_PATHS.
                .requestMatchers(AuthConstants.PUBLIC_PATHS).permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt
                .jwtAuthenticationConverter(emailAsPrincipalConverter())))
            .build();
    }

    /**
     * Binds {@code Authentication#getName()} to the {@code email} claim so
     * downstream code (audit, usage_events.user_id, app authorisation joins)
     * shares one human-readable canonical identifier across every JWT-based
     * auth path. Per the blueprint contract: user_id = lowercased email
     * everywhere.
     *
     * Behavior matrix:
     * <ul>
     *   <li>mock — {@code MockTokenService} always emits an {@code email}
     *       claim → {@code getName()} returns the email.</li>
     *   <li>Clerk SSO with a JWT template that emits {@code email} →
     *       {@code getName()} returns the email.</li>
     *   <li>Clerk SSO without an email-emitting template → claim is absent,
     *       Spring falls back to {@code jwt.getSubject()} (Clerk
     *       {@code user_xxx}). Logging still works; configure the template
     *       tenant-side when you want human-readable rows.</li>
     * </ul>
     *
     * @return converter that pins principal name to the email claim, with
     *         Spring's built-in subject fallback when the claim is missing
     */
    private static JwtAuthenticationConverter emailAsPrincipalConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        // Spring's JwtAuthenticationToken#getName() falls back to
        // jwt.getSubject() when the configured claim is null/blank, so this
        // is safe even when the IdP doesn't ship the email claim.
        converter.setPrincipalClaimName("email");
        return converter;
    }

    /**
     * CORS source — reads allow-list from `app.security.cors.allowed-origins`.
     * `allowedOriginPatterns` (not `allowedOrigins`) is required when wildcards
     * are present (e.g. `https://*.replit.dev`).
     *
     * @return URL-pattern CORS configuration applied to all paths.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(Arrays.stream(corsAllowedOrigins.split(","))
            .map(String::trim).filter(s -> !s.isEmpty()).toList());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-Id", "Accept"));
        cfg.setExposedHeaders(List.of("X-Correlation-Id"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }

    // ----- JwtDecoder selection -----
    //
    // The three @ConditionalOnProperty branches below are mutually exclusive
    // by design — exactly one fires in every supported (auth-mode, has-keys)
    // combination. If none did, OAuth2 auto-config would wake up and
    // attempt to fetch JWKS from an empty URL → startup crash. The
    // mockJwtDecoder branch carries `matchIfMissing = true` so AUTH_MODE
    // can even be omitted in tests.

    /**
     * Builds a JWT decoder for explicit SSO mode.
     *
     * @param props auth configuration
     * @return SSO JWT decoder
     */
    @Bean
    @ConditionalOnProperty(name = AuthConstants.AUTH_MODE_PROPERTY,
                           havingValue = AuthConstants.AUTH_MODE_SSO)
    @ConditionalOnExpression(AuthConstants.NON_REPLIT_MODE_CONDITION)
    JwtDecoder ssoJwtDecoder(AuthProperties props) {
        return buildSsoDecoder(props);
    }

    /**
     * AUTH_MODE=auto AND Clerk keys + issuer/JWKS present → real Clerk SSO.
     *
     * SpEL composite (see AuthConstants.SSO_AUTO_CONDITION) because
     * @ConditionalOnProperty is NOT repeatable — stacking two of them
     * silently drops the second and the bean wires up regardless of
     * whether CLERK_SECRET_KEY is set.
     *
     * @param props auth configuration
     * @return SSO JWT decoder
     */
    @Bean
    @ConditionalOnExpression(AuthConstants.SSO_AUTO_CONDITION)
    @ConditionalOnMissingBean(JwtDecoder.class)
    JwtDecoder autoSsoJwtDecoder(AuthProperties props) {
        return buildSsoDecoder(props);
    }

    /**
     * Builds a Nimbus JwtDecoder with explicit validation: iss (default),
     * exp/nbf/timestamps (default), and aud when configured. Prefers
     * {@code jwk-set-uri} when provided; falls back to issuer auto-discovery.
     *
     * @param props auth configuration bound from {@code app.auth.*}
     * @return decoder configured for Clerk (or any OIDC issuer)
     * @throws IllegalStateException when issuer-uri is missing — past sessions
     *         saw {@code JwtDecoders.fromIssuerLocation(null)} crash opaquely
     */
    private static JwtDecoder buildSsoDecoder(AuthProperties props) {
        String issuer = props.getSso().getIssuerUri();
        String jwkSetUri = props.getSso().getJwkSetUri();
        String audience = props.getSso().getAudience();

        if ((issuer == null || issuer.isBlank()) && (jwkSetUri == null || jwkSetUri.isBlank())) {
            throw new IllegalStateException(
                "SSO mode requires app.auth.sso.issuer-uri or app.auth.sso.jwk-set-uri "
                + "(set AUTH_ISSUER_URI or AUTH_JWKS_URI). Past sessions saw an opaque "
                + "JwtDecoders crash on empty issuer; this fail-fast surfaces the misconfig.");
        }

        NimbusJwtDecoder decoder = (jwkSetUri != null && !jwkSetUri.isBlank())
            ? NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build()
            : (NimbusJwtDecoder) JwtDecoders.fromIssuerLocation(issuer);

        OAuth2TokenValidator<Jwt> defaultValidator = (issuer != null && !issuer.isBlank())
            ? JwtValidators.createDefaultWithIssuer(issuer)
            : JwtValidators.createDefault();

        if (audience != null && !audience.isBlank()) {
            // aud claim is either a string OR a list — accept both, match any.
            OAuth2TokenValidator<Jwt> audValidator = new JwtClaimValidator<Object>(
                JwtClaimNames.AUD,
                claim -> claim instanceof java.util.Collection<?> c
                    ? c.contains(audience)
                    : audience.equals(claim));
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(defaultValidator, audValidator));
        } else {
            decoder.setJwtValidator(defaultValidator);
        }
        return decoder;
    }

    /**
     * AUTH_MODE=mock OR AUTH_MODE=auto without Clerk keys OR any other case
     * where neither SSO branch fired. This is the **hard fallback** that
     * guarantees a JwtDecoder bean always exists, so OAuth2 auto-config
     * never tries to build one from empty config.
     *
     * @param props auth configuration
     * @return mock JWT decoder
     */
    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    @ConditionalOnExpression(AuthConstants.NON_REPLIT_MODE_CONDITION)
    JwtDecoder mockJwtDecoderFallback(AuthProperties props) {
        return new MockJwtDecoder(props.getMock().getJwtSecret());
    }
}
