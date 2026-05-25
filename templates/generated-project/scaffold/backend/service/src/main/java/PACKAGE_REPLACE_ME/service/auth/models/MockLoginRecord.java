package PACKAGE_REPLACE_ME.service.auth.models;

import java.time.Instant;

/**
 * Result of a successful mock login.
 *
 * @param accessToken Signed JWT for the mock identity (HS256, app-secret).
 * @param expiresAt   UTC expiry, matches the {@code exp} claim in the JWT.
 */
public record MockLoginRecord(String accessToken, Instant expiresAt) { }
