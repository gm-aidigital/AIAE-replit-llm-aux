// SecurityConfig — dual-mode auth chain.
// AUTH_MODE=sso  → Clerk JwtDecoder against issuer JWKS.
// AUTH_MODE=mock → MockJwtDecoder (HS256, backend-signed).
// AUTH_MODE=auto → SSO if CLERK_SECRET_KEY set, else mock.
// Single SecurityFilterChain; only JwtDecoder bean differs per mode.
//
// GOTCHA: OAuth2 resource-server auto-config triggers on empty issuer-uri
// and crashes startup. Fix: always provide @Bean JwtDecoder (hard-fallback
// branch below); never set spring.security.oauth2.resourceserver.jwt.* in YAML.
// Full: `.agents/skills/backend-java-feature/references/spring-boot-gotchas.md`
// → "OAuth2 Resource Server auto-config".

package PACKAGE_REPLACE_ME.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // PUBLIC_PATHS are application-relative (Spring Security
                // already strips the servlet context-path before matching),
                // so they do NOT include "/<context-path>/" — see
                // AuthConstants.PUBLIC_PATHS.
                .requestMatchers(AuthConstants.PUBLIC_PATHS).permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> {}))
            .build();
    }

    // ----- JwtDecoder selection -----
    //
    // The three @ConditionalOnProperty branches below are mutually exclusive
    // by design — exactly one fires in every supported (auth-mode, has-keys)
    // combination. If none did, OAuth2 auto-config would wake up and
    // attempt to fetch JWKS from an empty URL → startup crash. The
    // mockJwtDecoder branch carries `matchIfMissing = true` so AUTH_MODE
    // can even be omitted in tests.

    /** AUTH_MODE=sso (explicit). Requires app.auth.sso.issuer-uri to be set. */
    @Bean
    @ConditionalOnProperty(name = AuthConstants.AUTH_MODE_PROPERTY,
                           havingValue = AuthConstants.AUTH_MODE_SSO)
    JwtDecoder ssoJwtDecoder(AuthProperties props) {
        String issuer = props.getSso().getIssuerUri();
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalStateException(
                "AUTH_MODE=sso requires app.auth.sso.issuer-uri (Clerk tenant issuer URL)");
        }
        return JwtDecoders.fromIssuerLocation(issuer);
    }

    /**
     * AUTH_MODE=auto AND Clerk keys present → real Clerk SSO.
     *
     * SpEL composite (see AuthConstants.SSO_AUTO_CONDITION) because
     * @ConditionalOnProperty is NOT repeatable — stacking two of them
     * silently drops the second and the bean wires up regardless of
     * whether CLERK_SECRET_KEY is set.
     */
    @Bean
    @ConditionalOnExpression(AuthConstants.SSO_AUTO_CONDITION)
    @ConditionalOnMissingBean(JwtDecoder.class)
    JwtDecoder autoSsoJwtDecoder(AuthProperties props) {
        return JwtDecoders.fromIssuerLocation(props.getSso().getIssuerUri());
    }

    /**
     * AUTH_MODE=mock OR AUTH_MODE=auto without Clerk keys OR any other case
     * where neither SSO branch fired. This is the **hard fallback** that
     * guarantees a JwtDecoder bean always exists, so OAuth2 auto-config
     * never tries to build one from empty config.
     */
    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    JwtDecoder mockJwtDecoderFallback(AuthProperties props) {
        return new MockJwtDecoder(props.getMock().getJwtSecret());
    }
}
