// UsageEvent — immutable value type assembled by UsageLoggingAspect and
// handed to UsageLogger.record(). Lives in service so both the aspect
// (application module) and any future logger impl can see it without
// pulling JPA/Spring deps into service.

package PACKAGE_REPLACE_ME.service.common.observability;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.Map;

@Builder
public record UsageEvent(
    String eventId,
    LocalDateTime eventTimestamp,
    String service,
    String environment,
    String eventType,
    String action,
    String userId,
    String userEmail,
    String status,
    long durationMs,
    Map<String, Object> attributes,
    String errorMessage,
    String correlationId,
    String clientIp,
    String userAgent
) { }
