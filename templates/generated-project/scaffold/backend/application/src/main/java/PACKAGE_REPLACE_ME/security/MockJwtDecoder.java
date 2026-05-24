// MockJwtDecoder — backend-signed HS256 JWT decoder for mock auth mode.
// Same JwtDecoder interface Spring Security uses for the real Clerk path,
// so downstream code (SecurityContext + AuthenticationManager) doesn't
// know the difference. Wired only when AUTH_MODE=mock (or auto without
// Clerk keys) — see SecurityConfig.

package PACKAGE_REPLACE_ME.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class MockJwtDecoder implements JwtDecoder {

    private static final String HEADER_TYP_VALUE = "JWT";
    private static final String HEADER_ALG_KEY   = "alg";
    private static final String HEADER_TYP_KEY   = "typ";

    private final SecretKey signingKey;

    public MockJwtDecoder(String secret) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                "AUTH_MOCK_JWT_SECRET must be at least 32 characters for HS256. "
                + "Set app.auth.mock.jwt-secret in application.yml or AUTH_MOCK_JWT_SECRET in env.");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        try {
            var claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

            Map<String, Object> headers = new HashMap<>();
            headers.put(HEADER_ALG_KEY, "HS256");
            headers.put(HEADER_TYP_KEY, HEADER_TYP_VALUE);

            return new Jwt(
                token,
                Instant.ofEpochMilli(claims.getIssuedAt().getTime()),
                Instant.ofEpochMilli(claims.getExpiration().getTime()),
                headers,
                claims
            );
        } catch (SignatureException ex) {
            throw new JwtException("Invalid mock JWT signature", ex);
        } catch (io.jsonwebtoken.JwtException ex) {
            throw new JwtException("Invalid mock JWT: " + ex.getMessage(), ex);
        }
    }
}
