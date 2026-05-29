package PACKAGE_REPLACE_ME.auth.controllers;

import PACKAGE_REPLACE_ME.api.v1.AuthApi;
import PACKAGE_REPLACE_ME.api.v1.model.UserV1;
import PACKAGE_REPLACE_ME.security.AppUserFactory;
import PACKAGE_REPLACE_ME.service.common.security.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

/**
 * Auth endpoint — returns the authenticated user. Implements the generated
 * {@code AuthApi} from {@code openapi.yaml}. Clerk SSO is the only auth mode:
 * authentication is enforced by the Bearer-JWT security chain before this
 * method runs; an unauthenticated request returns 401 before reaching here.
 */
@RestController
@RequiredArgsConstructor
public class AuthController implements AuthApi {

    private final AppUserFactory appUserFactory;

    /**
     * Returns the authenticated user payload built from the JWT in the
     * security context.
     *
     * @return 200 with {@link UserV1} populated from the JWT claims.
     */
    @Override
    public ResponseEntity<UserV1> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        AppUser caller = appUserFactory.from(auth);
        UserV1 dto = new UserV1();
        dto.setId(caller.subject());
        dto.setEmail(caller.email());
        dto.setRoles(caller.roles());
        return ResponseEntity.ok(dto);
    }
}
