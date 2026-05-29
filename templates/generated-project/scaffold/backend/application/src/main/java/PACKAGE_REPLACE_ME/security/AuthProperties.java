// @ConfigurationProperties bean — typed home for Clerk SSO settings.
// Maps from application.yml `app.auth.sso.*` and the AUTH_* env vars.

package PACKAGE_REPLACE_ME.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotNull;

/**
 * Typed configuration for Clerk SSO authentication.
 */
@Validated
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    @NotNull
    private Sso sso = new Sso();

    public Sso getSso() {
        return sso;
    }

    public void setSso(Sso sso) {
        this.sso = sso;
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
