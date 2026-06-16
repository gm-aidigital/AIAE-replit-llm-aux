package PACKAGE_REPLACE_ME.cache;

import PACKAGE_REPLACE_ME.domain.ToWarmUp;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the L2 cache warm-up infrastructure.
 *
 * <p>Actual cache regions and query hints are configured via
 * {@code src/main/resources/ehcache.xml} and Hibernate properties in
 * {@code application.yml}. This config only wires the optional startup
 * warm-up service; if {@code app.cache.warmup-enabled} is {@code false}
 * (the default), no dictionary loading occurs.
 */
@Configuration
@EnableConfigurationProperties(CacheProperties.class)
public class CacheConfig {

    /**
     * Creates the cache warm-up service that loads dictionaries on startup.
     *
     * @param cacheProperties warm-up configuration
     * @param repositoriesToWarmUp all repositories implementing {@link ToWarmUp}
     * @return the warm-up service bean
     */
    @Bean
    public CacheWarmUpService cacheWarmUpService(
        CacheProperties cacheProperties,
        List<ToWarmUp<?>> repositoriesToWarmUp
    ) {
        return new CacheWarmUpService(cacheProperties, repositoriesToWarmUp);
    }
}
