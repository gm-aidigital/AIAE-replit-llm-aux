package PACKAGE_REPLACE_ME.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the Clerk-SSO {@link JwtDecoder} wiring. {@code buildSsoDecoder}
 * is package-private so it can be exercised directly (no static, no Spring
 * context). Proves the backend builds a real signature-validating decoder and
 * fails fast when SSO is unconfigured.
 */
class SecurityConfigTest {

    private final SecurityConfig securityConfig = new SecurityConfig();

    @Test
    void shouldFailFastWhenSsoIsUnconfiguredTest() {
        // Given: neither issuer-uri nor jwk-set-uri (Clerk SSO is required)
        AuthProperties props = new AuthProperties();

        // When / Then: startup must not silently allow unauthenticated traffic
        assertThatThrownBy(() -> securityConfig.buildSsoDecoder(props))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Clerk SSO is required");
    }

    @Test
    void shouldBuildSignatureValidatingJwksDecoderWhenConfiguredTest() {
        // Given: a Clerk JWKS endpoint
        AuthProperties props = new AuthProperties();
        props.getSso().setJwkSetUri("https://clerk.example.com/.well-known/jwks.json");

        // When:
        JwtDecoder decoder = securityConfig.buildSsoDecoder(props);

        // Then: a real Nimbus decoder (verifies signature against JWKS), not a
        // pass-through — every token is validated before a request is authorized
        assertThat(decoder).isInstanceOf(NimbusJwtDecoder.class);
    }
}
