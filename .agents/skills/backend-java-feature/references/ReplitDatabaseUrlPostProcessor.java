// ReplitDatabaseUrlPostProcessor.java
//
// Reference copy of the Java EnvironmentPostProcessor used by the scaffold.
// The actual class shipped to generated projects lives at
//   templates/generated-project/scaffold/backend/application/src/main/java/
//   PACKAGE_REPLACE_ME/config/ReplitDatabaseUrlPostProcessor.java
// — copy it into the generated project verbatim, swap PACKAGE_REPLACE_ME
// for the real base package, and register it via:
//   backend/application/src/main/resources/META-INF/spring/
//     org.springframework.boot.env.EnvironmentPostProcessor.imports
//
// !!! CRITICAL bug to avoid !!!
// Do NOT gate on `env.getActiveProfiles()` inside postProcessEnvironment().
// At this lifecycle point Spring has not yet resolved active profiles, so
// the array is empty and any `contains("replit")` check silently no-ops.
// Gate on DATABASE_URL presence instead — it's the same signal anyway.

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
        String databaseUrl = env.getProperty(DATABASE_URL);
        if (databaseUrl == null || databaseUrl.isBlank()) {
            return;
        }
        if (!databaseUrl.startsWith("postgresql://") && !databaseUrl.startsWith("postgres://")) {
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
