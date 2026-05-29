package PACKAGE_REPLACE_ME.service.common.security;

import java.util.List;

/**
 * Immutable caller context passed through the service layer for authorization
 * checks. Built by {@code AppUserFactory} in the application module from the
 * Spring Security authentication; lives in the service module so service-impl
 * methods can accept it as a parameter without pulling Spring Security into
 * the service module's classpath.
 *
 * @param subject Stable Clerk user id ({@code sub} claim).
 * @param email   Lowercased canonical email — the principal identifier used
 *                everywhere ({@code user_roles.user_id}, audit, joins).
 * @param roles   Authorities from JWT (e.g. {@code ROLE_HR_MANAGER}).
 */
public record AppUser(String subject, String email, List<String> roles) {

    /**
     * Tells whether the caller has any of the given roles.
     *
     * @param required role names to check against
     * @return true if at least one required role is present
     */
    public boolean hasAnyRole(String... required) {
        for (String r : required) {
            if (roles.contains(r)) {
                return true;
            }
        }
        return false;
    }
}
