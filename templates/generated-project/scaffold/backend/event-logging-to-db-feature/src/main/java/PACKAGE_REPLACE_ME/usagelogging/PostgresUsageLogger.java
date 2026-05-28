// PostgresUsageLogger — dispatches UsageEvent persistence to a separate
// Spring bean. The actual INSERT lives in UsageEventPersistenceService so
// @Transactional(REQUIRES_NEW) and @Async are applied through a proxy.

package PACKAGE_REPLACE_ME.usagelogging;

import lombok.RequiredArgsConstructor;

/**
 * Dispatches usage events into the configured persistence sink.
 */
@RequiredArgsConstructor
public class PostgresUsageLogger implements UsageLogger {

    private final UsageEventPersistenceService persistenceService;

    @Override
    public void record(UsageEvent event) {
        persistenceService.persist(event);
    }
}
