// EnvironmentPostProcessor: parses Replit's DATABASE_URL libpq URL into
// standard spring.datasource.url/username/password before context build.
// Spring Boot then builds HikariDataSource normally — no custom @Bean.
//
// CRITICAL: gate on DATABASE_URL presence, NOT profile. postProcessEnvironment()
// runs BEFORE SPRING_PROFILES_ACTIVE resolves; getActiveProfiles() is empty
// — any profile check silently no-ops everywhere.
//
// Registration: META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor.imports
// (one line: FQN of this class).
//
// Full rationale: `.agents/skills/backend-java-feature/references/database-url-translation.md`.

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

/**
 * Translates Replit's libpq-style {@code DATABASE_URL} into Spring datasource properties.
 */
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

        // Respect sslmode from the URL query string. Replit's managed
        // Postgres tiers behave differently:
        //   - Replit production-grade DB → URL carries `sslmode=require`
        //   - Replit Helium dev DB       → URL omits sslmode
        // Do NOT force `require` when the URL is silent; that breaks Helium.
        String sslMode = queryParam(uri.getRawQuery(), "sslmode");

        Map<String, Object> props = new HashMap<>();
        props.put("spring.datasource.url",
            "jdbc:postgresql://" + uri.getHost() + ":" + port + uri.getPath());
        props.put("spring.datasource.username",
            URLDecoder.decode(creds[0], StandardCharsets.UTF_8));
        props.put("spring.datasource.password",
            creds.length > 1 ? URLDecoder.decode(creds[1], StandardCharsets.UTF_8) : "");
        if (sslMode != null && !sslMode.isBlank()) {
            boolean useSsl = !"disable".equalsIgnoreCase(sslMode);
            props.put("spring.datasource.hikari.data-source-properties.ssl",
                String.valueOf(useSsl));
            props.put("spring.datasource.hikari.data-source-properties.sslmode", sslMode);
        }

        env.getPropertySources().addFirst(new MapPropertySource(SOURCE_NAME, props));
    }

    /**
     * Reads one decoded query parameter value from a raw URI query.
     *
     * @param rawQuery raw query string without the leading question mark
     * @param key parameter name to read
     * @return decoded parameter value, or null when absent
     */
    private static String queryParam(String rawQuery, String key) {
        if (rawQuery == null) {
            return null;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && key.equals(pair.substring(0, eq))) {
                return URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static String maskUrl(String url) {
        return url.replaceAll("://[^@]+@", "://***@");
    }
}
