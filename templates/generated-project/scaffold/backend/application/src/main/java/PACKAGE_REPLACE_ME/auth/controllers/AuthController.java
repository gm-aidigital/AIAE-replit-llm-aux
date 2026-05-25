package PACKAGE_REPLACE_ME.auth.controllers;

import PACKAGE_REPLACE_ME.api.v1.AuthApi;
import PACKAGE_REPLACE_ME.api.v1.model.MockLoginRequestV1;
import PACKAGE_REPLACE_ME.api.v1.model.MockLoginResponseV1;
import PACKAGE_REPLACE_ME.api.v1.model.UserV1;
import PACKAGE_REPLACE_ME.auth.MockTokenService;
import PACKAGE_REPLACE_ME.security.AppUserFactory;
import PACKAGE_REPLACE_ME.security.AuthConstants;
import PACKAGE_REPLACE_ME.security.AuthProperties;
import PACKAGE_REPLACE_ME.service.auth.models.MockLoginRecord;
import PACKAGE_REPLACE_ME.service.common.error.AppException;
import PACKAGE_REPLACE_ME.service.common.error.ErrorReason;
import PACKAGE_REPLACE_ME.service.common.security.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Auth endpoints — universal across every project, so they ship pre-built.
 * Implements the generated {@code AuthApi} from {@code openapi.yaml}.
 * <p>
 * Both endpoints survive deletion ONLY if the spec drops them — otherwise
 * the build fails on missing {@code AuthApi} implementation. Customise the
 * payload shape in {@code openapi.yaml} first, then this file follows.
 */
@RestController
@RequiredArgsConstructor
public class AuthController implements AuthApi {

    private final MockTokenService mockTokenService;
    private final AuthProperties authProperties;

    @Value("${CLERK_SECRET_KEY:}")
    private String clerkSecretKey;

    /**
     * Returns the authenticated user payload built from the JWT in the
     * security context. Authentication is enforced by the security chain
     * before this method runs; an unauthenticated request returns 401
     * before reaching here.
     *
     * @return 200 with {@link UserV1} populated from the JWT claims.
     */
    @Override
    public ResponseEntity<UserV1> getCurrentUser() {
        AppUser caller = currentUser();
        UserV1 dto = new UserV1();
        dto.setId(caller.subject());
        dto.setEmail(caller.email());
        dto.setRoles(caller.roles());
        return ResponseEntity.ok(dto);
    }

    /**
     * Issues a mock JWT for the given email. Refuses when
     * {@code AUTH_MODE=sso} so production deployments don't accidentally
     * expose a mock-login backdoor.
     *
     * @param body request payload with {@code email} (required, non-blank).
     * @return 200 with the signed token and its UTC expiry.
     * @throws AppException {@link ErrorReason#C002} when email is missing,
     *                      {@link ErrorReason#C004} when AUTH_MODE=sso.
     */
    @Override
    public ResponseEntity<MockLoginResponseV1> mockLogin(MockLoginRequestV1 body) {
        if (isMockLoginDisabled()) {
            throw new AppException(ErrorReason.C004,
                "Mock login is disabled when SSO is active");
        }
        if (body.getEmail() == null || body.getEmail().isBlank()) {
            throw new AppException(ErrorReason.C002, "Email is required");
        }
        String email = body.getEmail().strip().toLowerCase();
        MockLoginRecord record = mockTokenService.issueToken(email);
        MockLoginResponseV1 dto = new MockLoginResponseV1();
        dto.setAccessToken(record.accessToken());
        // dateLibrary=java8-localdatetime → MockLoginResponseV1.expiresAt is
        // LocalDateTime in UTC (see canonical-openapi-rules.md → "UTC convention").
        dto.setExpiresAt(LocalDateTime.ofInstant(record.expiresAt(), ZoneOffset.UTC));
        return ResponseEntity.ok(dto);
    }

    /**
     * Resolves the authenticated caller from the security context.
     *
     * @return current authenticated user
     */
    private AppUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return AppUserFactory.from(auth);
    }

    /**
     * Determines whether mock login must be disabled for the current auth mode.
     *
     * @return true when SSO is explicitly or automatically active
     */
    private boolean isMockLoginDisabled() {
        String mode = authProperties.getMode();
        return AuthConstants.AUTH_MODE_SSO.equals(mode)
            || (AuthConstants.AUTH_MODE_AUTO.equals(mode)
                && StringUtils.hasText(clerkSecretKey)
                && hasSsoIssuerOrJwks());
    }

    /**
     * Auto mode becomes SSO only when Spring has enough metadata to validate JWTs.
     *
     * @return true when issuer or JWKS endpoint is configured.
     */
    private boolean hasSsoIssuerOrJwks() {
        return StringUtils.hasText(authProperties.getSso().getIssuerUri())
            || StringUtils.hasText(authProperties.getSso().getJwkSetUri());
    }
}
