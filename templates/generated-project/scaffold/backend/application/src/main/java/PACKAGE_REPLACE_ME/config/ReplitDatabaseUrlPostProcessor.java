// ReplitDatabaseUrlPostProcessor.java
//
// Spring Boot EnvironmentPostProcessor: runs before the application context
// is built and translates Replit's DATABASE_URL libpq URL into the standard
// spring.datasource.url / username / password properties. After this runs,
// Spring Boot's auto-configuration builds the HikariDataSource normally —
// no custom @Bean, no override of Spring's DataSource construction.
//
// Why this over a @Configuration @Bean DataSource? See:
//   .agents/skills/backend-java-feature/references/database-url-translation.md
//
// !!! CRITICAL — DO NOT GATE ON PROFILE !!!
// EnvironmentPostProcessor.postProcessEnvironment() is called BEFORE Spring
// resolves the active profile set from `SPRING_PROFILES_ACTIVE` env var.
// Calling `env.getActiveProfiles()` here returns an empty array; any
// `if (!profiles.contains("replit")) return;` check would silently no-op
// in every environment and the datasource properties would never be set.
//
// Correct gate: the presence of the DATABASE_URL env var itself. On Replit
// it's always injected. On local-dev it's absent, the post-processor
// short-circuits, and `application-local.yml` provides the datasource the
// normal way. On tests it's absent too — Testcontainers / @AutoConfigureTestDatabase
// take over. No profile reasoning needed.
//
// Registration: this class is wired in via
//   META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor.imports
// (one line: the FQN of this class).

package PACKAGE_REPLACE_ME.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class ReplitDatabaseUrlPostProcessor implements EnvironmentPostProcessor {

    private static final String SOURCE_NAME = "replit-database-url";
    private static final String DATABASE_URL = "DATABASE_URL";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env,
                                       SpringApplication application) {
        // Gate on DATABASE_URL presence — NOT on profile (see header comment).
        String databaseUrl = env.getProperty(DATABASE_URL);
        if (databaseUrl == null || databaseUrl.isBlank()) {
            return;
        }
        if (!databaseUrl.startsWith("postgresql://") && !databaseUrl.startsWith("postgres://")) {
            // Already a JDBC URL or something we don't recognise — leave it alone.
            return;
        }

        URI uri = URI.create(databaseUrl);
        String userInfo = uri.getUserInfo();
        if (userInfo == null) {
            throw new IllegalStateException(
                "DATABASE_URL must include user:password — got: " + maskUrl(databaseUrl)
            );
        }
        String[] creds = userInfo.split(":", 2);
        int port = uri.getPort() == -1 ? 5432 : uri.getPort();

        Map<String, Object> props = new HashMap<>();
        props.put("spring.datasource.url",
            "jdbc:postgresql://" + uri.getHost() + ":" + port + uri.getPath());
        props.put("spring.datasource.username",
            URLDecoder.decode(creds[0], StandardCharsets.UTF_8));
        props.put("spring.datasource.password",
            creds.length > 1 ? URLDecoder.decode(creds[1], StandardCharsets.UTF_8) : "");
        props.put("spring.datasource.hikari.data-source-properties.ssl", "true");
        props.put("spring.datasource.hikari.data-source-properties.sslmode", "require");

        env.getPropertySources().addFirst(new MapPropertySource(SOURCE_NAME, props));
    }

    private static String maskUrl(String url) {
        return url.replaceAll("://[^@]+@", "://***@");
    }
}
