// @ConfigurationProperties bean — single typed home for all auth settings.
// Maps from application.yml `app.auth.*` and corresponding env vars.

package PACKAGE_REPLACE_ME.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Typed configuration for mock and SSO authentication modes.
 */
@Validated
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    /** auto | sso | mock — see AuthConstants. */
    @NotBlank
    private String mode = AuthConstants.AUTH_MODE_AUTO;

    @NotNull
    private Mock mock = new Mock();

    @NotNull
    private Sso sso = new Sso();

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public Mock getMock() {
        return mock;
    }

    public void setMock(Mock mock) {
        this.mock = mock;
    }

    public Sso getSso() {
        return sso;
    }

    public void setSso(Sso sso) {
        this.sso = sso;
    }

    /**
     * Mock-auth settings for local development and demos.
     */
    public static class Mock {
        /** Default user identity when no specific user logs in. */
        private String defaultUser = "demo-user@example.com";
        /** Signing secret for backend-issued mock JWTs. */
        private String jwtSecret;

        public String getDefaultUser() {
            return defaultUser;
        }

        public void setDefaultUser(String defaultUser) {
            this.defaultUser = defaultUser;
        }

        public String getJwtSecret() {
            return jwtSecret;
        }

        public void setJwtSecret(String jwtSecret) {
            this.jwtSecret = jwtSecret;
        }
    }

    /**
     * SSO/OIDC settings for Clerk or another compatible provider.
     */
    public static class Sso {
        /** Clerk issuer URI, e.g. https://clean-clerk.clerk.accounts.dev */
        private String issuerUri;
        /** Optional JWKS override; usually discovered from issuerUri. */
        private String jwkSetUri;
        /** Expected `aud` claim value. */
        private String audience;

        public String getIssuerUri() {
            return issuerUri;
        }

        public void setIssuerUri(String issuerUri) {
            this.issuerUri = issuerUri;
        }

        public String getJwkSetUri() {
            return jwkSetUri;
        }

        public void setJwkSetUri(String jwkSetUri) {
            this.jwkSetUri = jwkSetUri;
        }

        public String getAudience() {
            return audience;
        }

        public void setAudience(String audience) {
            this.audience = audience;
        }
    }
}
