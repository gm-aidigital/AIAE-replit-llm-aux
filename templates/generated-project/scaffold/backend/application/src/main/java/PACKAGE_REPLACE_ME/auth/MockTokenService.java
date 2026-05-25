package PACKAGE_REPLACE_ME.auth;

import PACKAGE_REPLACE_ME.security.AuthConstants;
import PACKAGE_REPLACE_ME.security.AuthProperties;
import PACKAGE_REPLACE_ME.service.auth.models.MockLoginRecord;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Issues backend-signed mock JWTs for the local/dev auth flow. Active when
 * {@code AUTH_MODE} is {@code mock} or {@code auto} without Clerk keys.
 * Replace or extend {@link #issueToken(String)} when adding role lookup
 * (e.g. a {@code UserRoleService} bean).
 */
@Service
@RequiredArgsConstructor
public class MockTokenService {

    private final AuthProperties authProperties;

    /**
     * Issues a signed mock JWT for the given email.
     *
     * @param email demo account email to authenticate as; must be non-blank.
     *              Caller is responsible for lowercasing/stripping before passing.
     * @return token + UTC expiry. The same value sits in the JWT {@code exp} claim.
     * @throws IllegalStateException when {@code AUTH_MOCK_JWT_SECRET} is unset
     *                               or shorter than 32 characters (HS256 minimum).
     */
    public MockLoginRecord issueToken(String email) {
        String secret = authProperties.getMock().getJwtSecret();
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                "AUTH_MOCK_JWT_SECRET not configured (min 32 chars)");
        }
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        long now = System.currentTimeMillis();
        long expiresAtMs = now + AuthConstants.MOCK_JWT_TTL_SECS * 1000L;
        String token = Jwts.builder()
            .subject(email)
            .claim("email", email)
            .issuer(AuthConstants.MOCK_JWT_ISSUER)
            .issuedAt(new Date(now))
            .expiration(new Date(expiresAtMs))
            .signWith(key)
            .compact();
        return new MockLoginRecord(token, Instant.ofEpochMilli(expiresAtMs));
    }
}
