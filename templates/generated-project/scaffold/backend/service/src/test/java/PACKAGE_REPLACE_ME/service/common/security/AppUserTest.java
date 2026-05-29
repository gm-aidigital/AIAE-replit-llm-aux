package PACKAGE_REPLACE_ME.service.common.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AppUserTest {

    @Test
    void shouldReturnTrueWhenAnyRequiredRolePresentTest() {
        // Given:
        AppUser user = new AppUser("sub-1", "alice@example.com",
            List.of("ROLE_HR_MANAGER", "ROLE_EMPLOYEE"));

        // When / Then:
        assertThat(user.hasAnyRole("ROLE_ADMIN", "ROLE_EMPLOYEE")).isTrue();
        assertThat(user.subject()).isEqualTo("sub-1");
        assertThat(user.email()).isEqualTo("alice@example.com");
        assertThat(user.roles()).contains("ROLE_HR_MANAGER");
    }

    @Test
    void shouldReturnFalseWhenNoRequiredRolePresentTest() {
        // Given:
        AppUser user = new AppUser("sub-2", "bob@example.com", List.of("ROLE_EMPLOYEE"));

        // When / Then:
        assertThat(user.hasAnyRole("ROLE_ADMIN", "ROLE_HR_MANAGER")).isFalse();
        assertThat(user.hasAnyRole()).isFalse();
    }
}
