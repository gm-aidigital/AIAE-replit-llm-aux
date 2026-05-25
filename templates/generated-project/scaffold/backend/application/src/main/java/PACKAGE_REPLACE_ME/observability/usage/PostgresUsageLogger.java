// PostgresUsageLogger — persists UsageEvent to the usage_events table.
// Async, fire-and-forget usage-event persistence. Aspect already wraps
// record() dispatch in try/catch; this class also swallows DB failures so
// observability never breaks a user request.

package PACKAGE_REPLACE_ME.observability.usage;

import PACKAGE_REPLACE_ME.domain.usage.entities.UsageEventEntity;
import PACKAGE_REPLACE_ME.domain.usage.repositories.UsageEventRepository;
import PACKAGE_REPLACE_ME.service.common.observability.UsageEvent;
import PACKAGE_REPLACE_ME.service.common.observability.UsageLogger;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;

/**
 * Persists usage events into the application database without blocking requests.
 */
@RequiredArgsConstructor
public class PostgresUsageLogger implements UsageLogger {

    private static final Logger LOG = LoggerFactory.getLogger(PostgresUsageLogger.class);

    private final UsageEventRepository repository;

    @Override
    @Async("usageLoggingExecutor")
    public void record(UsageEvent event) {
        try {
            repository.save(toEntity(event));
        } catch (Throwable t) {
            LOG.warn("Failed to persist usage event action={} eventId={}: {}",
                event.action(), event.eventId(), t.getMessage());
        }
    }

    /**
     * Converts the service-layer value object to a JPA entity.
     *
     * @param e usage event value object
     * @return populated JPA entity
     */
    private static UsageEventEntity toEntity(UsageEvent e) {
        UsageEventEntity entity = new UsageEventEntity();
        entity.setEventId(e.eventId());
        entity.setEventTimestamp(e.eventTimestamp());
        entity.setService(e.service());
        entity.setEnvironment(e.environment());
        entity.setEventType(e.eventType());
        entity.setAction(e.action());
        entity.setUserId(e.userId());
        entity.setUserEmail(e.userEmail());
        entity.setStatus(e.status());
        entity.setDurationMs(e.durationMs());
        entity.setAttributes(e.attributes());     // JSONB; null is fine, Hibernate native JSON handles it
        entity.setErrorMessage(e.errorMessage());
        entity.setCorrelationId(e.correlationId());
        entity.setClientIp(e.clientIp());
        entity.setUserAgent(e.userAgent());
        return entity;
    }
}
