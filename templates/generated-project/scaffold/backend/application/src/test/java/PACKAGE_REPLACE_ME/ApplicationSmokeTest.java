package PACKAGE_REPLACE_ME;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test — Spring context loads end-to-end with the test profile.
 * Catches the bulk of wiring/regression failures (missing beans, conflicting
 * conditionals, broken @ConfigurationProperties binding, datasource config
 * crashes) with zero per-bean assertions.
 */
@SpringBootTest
@ActiveProfiles("test")
class ApplicationSmokeTest {

    @Test
    void shouldLoadSpringContextTest() {
    }
}
