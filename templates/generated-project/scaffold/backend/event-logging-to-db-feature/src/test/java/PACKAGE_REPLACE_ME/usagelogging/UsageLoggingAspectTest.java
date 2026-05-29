// Proves UsageLoggingAspect AUTO-intercepts every public *ServiceImpl method
// with no annotation needed — the exact behaviour that previously failed
// silently when service methods were left un-annotated. Uses a real Spring AOP
// proxy (not a mocked join point) so the pointcut itself is under test.
package PACKAGE_REPLACE_ME.usagelogging;

import PACKAGE_REPLACE_ME.service.demo.services.impl.DemoServiceImpl;
import PACKAGE_REPLACE_ME.usagelogging.config.UsageLoggingProperties;
import PACKAGE_REPLACE_ME.usagelogging.loggers.UsageLogger;
import PACKAGE_REPLACE_ME.usagelogging.models.UsageEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

@SpringJUnitConfig(UsageLoggingAspectTest.TestConfig.class)
class UsageLoggingAspectTest {

    @Configuration
    @EnableAspectJAutoProxy
    static class TestConfig {

        @Bean
        UsageLogger usageLogger() {
            return mock(UsageLogger.class);
        }

        @Bean
        UsageLoggingProperties usageLoggingProperties() {
            UsageLoggingProperties props = new UsageLoggingProperties();
            props.setServiceName("test-service");
            props.setEnvironment("test");
            return props;
        }

        @Bean
        UsageLoggingAspect usageLoggingAspect(UsageLogger logger, UsageLoggingProperties props) {
            return new UsageLoggingAspect(logger, props);
        }

        @Bean
        DemoServiceImpl demoServiceImpl() {
            return new DemoServiceImpl();
        }
    }

    @Autowired
    private UsageLogger usageLogger;

    @Autowired
    private DemoServiceImpl demoService;

    @BeforeEach
    void resetSink() {
        reset(usageLogger);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldAutoLogServiceImplMethodWithDerivedActionTest() {
        // When: a public *ServiceImpl method runs through the Spring proxy
        demoService.doThing();

        // Then: the aspect recorded one event with the derived action name
        ArgumentCaptor<UsageEvent> captor = ArgumentCaptor.forClass(UsageEvent.class);
        verify(usageLogger).record(captor.capture());
        UsageEvent event = captor.getValue();
        assertThat(event.action()).isEqualTo("demo.doThing");
        assertThat(event.status()).isEqualTo("success");
        assertThat(event.service()).isEqualTo("test-service");
        assertThat(event.environment()).isEqualTo("test");
    }

    @Test
    void shouldRecordErrorWhenServiceMethodThrowsTest() {
        // When: the intercepted method throws
        Throwable thrown = catchThrowable(() -> demoService.boom());

        // Then: the exception is rethrown unchanged and logged as an error
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        ArgumentCaptor<UsageEvent> captor = ArgumentCaptor.forClass(UsageEvent.class);
        verify(usageLogger).record(captor.capture());
        UsageEvent event = captor.getValue();
        assertThat(event.action()).isEqualTo("demo.boom");
        assertThat(event.status()).isEqualTo("error");
        assertThat(event.eventType()).isEqualTo("error");
        assertThat(event.errorMessage()).contains("kaboom");
    }

    @Test
    void shouldUseLogUsageActionOverrideWhenAnnotatedTest() {
        // When: the method carries an explicit @LogUsage(action = ...)
        demoService.custom();

        // Then: the override wins over the derived name
        ArgumentCaptor<UsageEvent> captor = ArgumentCaptor.forClass(UsageEvent.class);
        verify(usageLogger).record(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo("custom.action.name");
    }

    @Test
    void shouldCaptureUserEmailAndNameFromJwtPrincipalTest() {
        // Given: an authenticated Clerk JWT in the security context
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none")
            .subject("sub-1")
            .claim("email", "jane@example.com")
            .claim("full_name", "Jane Doe")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build();
        // 2-arg ctor → authenticated token, like the resource-server produces
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, java.util.List.of()));

        // When:
        demoService.doThing();

        // Then: the aspect lifts email + display name off the JWT principal
        ArgumentCaptor<UsageEvent> captor = ArgumentCaptor.forClass(UsageEvent.class);
        verify(usageLogger).record(captor.capture());
        UsageEvent event = captor.getValue();
        assertThat(event.userEmail()).isEqualTo("jane@example.com");
        assertThat(event.userId()).isEqualTo("sub-1");
        assertThat(event.attributes()).containsEntry("user_name", "Jane Doe");
    }
}
