// SecurityConfig — stateless Clerk-SSO Bearer-JWT security chain.
// Clerk SSO is the ONLY supported auth mode: the app validates Clerk-issued
// JWTs against the Clerk JWKS endpoint. There is NO mock/replit fallback — a
// missing issuer/JWKS fails fast at startup (see jwtDecoder()).
//
// GOTCHA: OAuth2 resource-server auto-config triggers on an empty issuer-uri
// and crashes startup. Fix: always provide the @Bean JwtDecoder here; never
// set spring.security.oauth2.resourceserver.jwt.* in YAML. Full:
// `.agents/skills/backend-java-feature/references/spring-boot-gotchas.md`
// → "OAuth2 Resource Server auto-config".

package PACKAGE_REPLACE_ME.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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
import java.util.Collection;
import java.util.List;

/**
 * Configures stateless Clerk-SSO API security, the JWT decoder, CORS, and
 * browser security headers.
 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfig {

    /**
     * Comma-separated allow-list for the CORS Origin header. Defaults match the
     * Replit workspace + common local-dev ports. Override via env
     * {@code APP_SECURITY_CORS_ALLOWED_ORIGINS} for staging/prod.
     */
    @Value("${app.security.cors.allowed-origins:"
        + "https://*.replit.dev,https://*.repl.co,http://localhost:5173,http://localhost:5000}")
    private String corsAllowedOrigins;

    /**
     * CSP {@code frame-ancestors} directive — controls which parent pages may
     * embed the SPA in an iframe. Tighten via env
     * {@code APP_SECURITY_CSP_FRAME_ANCESTORS} in deployment.
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
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Security headers — explicit policy, no Spring defaults relied on.
            // Iframe embedding allowed by company guidelines → X-Frame-Options
            // disabled; CSP frame-ancestors carries the policy instead.
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
                .requestMatchers(AuthConstants.PUBLIC_PATHS).permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt
                .jwtAuthenticationConverter(emailAsPrincipalConverter())))
            .build();
    }

    /**
     * Binds {@code Authentication#getName()} to the {@code email} claim so audit
     * / {@code usage_events.user_id} / authorization joins share one canonical
     * identifier (lowercased email). Spring falls back to {@code jwt.getSubject()}
     * when the IdP doesn't ship an email claim. Package-private so it is
     * unit-testable / spy-able (no static).
     *
     * @return converter that pins the principal name to the email claim
     */
    JwtAuthenticationConverter emailAsPrincipalConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName("email");
        return converter;
    }

    /**
     * CORS source — reads the allow-list from {@code app.security.cors.allowed-origins}.
     * {@code allowedOriginPatterns} (not {@code allowedOrigins}) is required when
     * wildcards are present (e.g. {@code https://*.replit.dev}).
     *
     * @return URL-pattern CORS configuration applied to all paths
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

    /**
     * The Clerk-SSO {@link JwtDecoder}. {@code @ConditionalOnMissingBean} lets a
     * test supply a stub decoder; in production this is the only decoder and it
     * FAILS FAST when neither issuer-uri nor jwk-set-uri is configured — SSO is
     * required, there is no mock fallback.
     *
     * @param props auth configuration bound from {@code app.auth.*}
     * @return decoder configured for Clerk (or any OIDC issuer)
     */
    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    JwtDecoder jwtDecoder(AuthProperties props) {
        return buildSsoDecoder(props);
    }

    /**
     * Builds a Nimbus JwtDecoder with explicit validation: iss (default),
     * exp/nbf/timestamps (default), and aud when configured. Prefers
     * {@code jwk-set-uri} when provided; falls back to issuer auto-discovery.
     * Package-private so it is unit-testable / spy-able (no static).
     *
     * @param props auth configuration bound from {@code app.auth.*}
     * @return decoder configured for Clerk (or any OIDC issuer)
     */
    JwtDecoder buildSsoDecoder(AuthProperties props) {
        String issuer = props.getSso().getIssuerUri();
        String jwkSetUri = props.getSso().getJwkSetUri();
        String audience = props.getSso().getAudience();

        if ((issuer == null || issuer.isBlank()) && (jwkSetUri == null || jwkSetUri.isBlank())) {
            throw new IllegalStateException(
                "Clerk SSO is required but unconfigured: set AUTH_ISSUER_URI or AUTH_JWKS_URI "
                + "(app.auth.sso.issuer-uri / jwk-set-uri). This template has no mock fallback.");
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
                claim -> claim instanceof Collection<?> c
                    ? c.contains(audience)
                    : audience.equals(claim));
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(defaultValidator, audValidator));
        } else {
            decoder.setJwtValidator(defaultValidator);
        }
        return decoder;
    }
}
