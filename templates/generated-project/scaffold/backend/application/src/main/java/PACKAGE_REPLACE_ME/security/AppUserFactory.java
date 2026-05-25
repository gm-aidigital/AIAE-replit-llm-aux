package PACKAGE_REPLACE_ME.security;

import PACKAGE_REPLACE_ME.service.common.security.AppUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Builds {@link AppUser} from the Spring Security authentication. Lives in
 * the application module so it can import Spring Security types without
 * polluting the service module.
 */
public final class AppUserFactory {

    private AppUserFactory() { }

    /**
     * Converts the current request authentication into an {@link AppUser}.
     * The {@code email} claim is preferred over {@code sub}; both Clerk and
     * the mock decoder are wired to put the canonical email in {@code email}.
     *
     * @param auth Spring Security authentication from the security context.
     *             Must not be null — the security chain rejects unauthenticated
     *             requests before they reach controllers.
     * @return caller context with stable subject, lowercased email and roles.
     */
    public static AppUser from(Authentication auth) {
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            String email = Optional.ofNullable(jwt.getClaimAsString("email"))
                .filter(e -> !e.isBlank())
                .orElse(jwt.getSubject());
            String canonicalEmail = email != null
                ? email.toLowerCase(Locale.ROOT).strip()
                : "unknown";
            List<String> roles = jwtAuth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
            return new AppUser(jwt.getSubject(), canonicalEmail, roles);
        }
        return new AppUser(auth.getName(), auth.getName(), List.of());
    }
}
