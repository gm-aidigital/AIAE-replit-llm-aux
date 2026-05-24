// UsageLoggingAspect — intercepts @LogUsage methods, records UsageEvent via
// UsageLogger. Behaviour invariants (preserve on every edit):
//  1. Self-invocation bypasses Spring AOP proxy → aspect won't fire.
//  2. @Order(LOWEST_PRECEDENCE - 100) — OUTER than @Transactional, so the
//     usage event reflects final commit/rollback outcome.
//  3. try { proceed() } catch (Throwable) { rethrow } finally { log }
//     — logs success + failure, never swallows.
//  4. SecurityContextHolder is ThreadLocal; for @Async paths use
//     MODE_INHERITABLETHREADLOCAL or DelegatingSecurityContextRunnable.
//  5. Logger is @Async (off-thread DB write). Aspect only assembles + hands off
//     — assembly must be cheap (no I/O).
//  6. joinPoint.getArgs() deliberately ignored — no payload/JWT/PII leakage.
// Full contract: `templates/generated-project/observability/usage-logging-rules.md`.

package PACKAGE_REPLACE_ME.observability.usage;

import PACKAGE_REPLACE_ME.service.common.observability.LogUsage;
import PACKAGE_REPLACE_ME.service.common.observability.UsageEvent;
import PACKAGE_REPLACE_ME.service.common.observability.UsageLogger;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)   // OUTER than @Transactional (see header)
@ConditionalOnProperty(name = "app.usage-logging.enabled",
                       havingValue = "true",
                       matchIfMissing = true)
public class UsageLoggingAspect {

    private static final String EVENT_TYPE_ERROR = "error";
    private static final String STATUS_SUCCESS   = "success";
    private static final String STATUS_ERROR     = "error";
    private static final String MDC_CORRELATION  = "correlationId";

    private final UsageLogger logger;
    private final UsageLoggingProperties props;

    public UsageLoggingAspect(UsageLogger logger, UsageLoggingProperties props) {
        this.logger = logger;
        this.props = props;
    }

    @Around("@annotation(logUsage)")
    public Object recordUsage(ProceedingJoinPoint joinPoint, LogUsage logUsage) throws Throwable {
        long startNanos = System.nanoTime();
        Throwable thrown = null;
        try {
            return joinPoint.proceed();
        } catch (Throwable t) {
            thrown = t;
            throw t;                                                 // rethrow — never swallow
        } finally {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
            try {
                logger.record(buildEvent(logUsage, thrown, durationMs));
            } catch (Throwable loggingFailure) {
                // Last resort: if even assembling/dispatching the event throws,
                // do NOT propagate into the caller's flow.
                org.slf4j.LoggerFactory.getLogger(UsageLoggingAspect.class)
                    .warn("Usage logging failed for action={}: {}",
                          logUsage.action(), loggingFailure.getMessage());
            }
        }
    }

    private UsageEvent buildEvent(LogUsage logUsage, Throwable thrown, long durationMs) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userId    = (auth != null && auth.isAuthenticated()) ? auth.getName() : null;
        String userEmail = extractEmail(auth);
        boolean failed   = thrown != null;

        return UsageEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .eventTimestamp(LocalDateTime.now(ZoneOffset.UTC))
            .service(props.getServiceName())
            .environment(props.getEnvironment())
            .eventType(failed ? EVENT_TYPE_ERROR : logUsage.eventType())
            .action(logUsage.action())
            .userId(userId)
            .userEmail(userEmail)
            .status(failed ? STATUS_ERROR : STATUS_SUCCESS)
            .durationMs(durationMs)
            .errorMessage(failed ? truncate(thrown.getMessage(), 500) : null)
            .correlationId(MDC.get(MDC_CORRELATION))
            .build();
    }

    private String extractEmail(Authentication auth) {
        if (auth == null) return null;
        Object principal = auth.getPrincipal();
        if (principal instanceof org.springframework.security.oauth2.jwt.Jwt jwt) {
            Object email = jwt.getClaims().get("email");
            return email != null ? String.valueOf(email) : null;
        }
        return null;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
