// UsageLoggingAspect — intercepts every @LogUsage method call and records
// a UsageEvent row via UsageLogger. The whole usage-logging behaviour
// lives behind one annotation; remove `@LogUsage` from a method (or
// remove this aspect bean) and the application keeps working without any
// other code change.
//
// VALIDATION NOTES — keep these guarantees on every edit to this file:
//
//  1. PROXY MECHANISM. Spring AOP proxies bean methods. Self-invocation
//     (`this.annotatedMethod()` inside the same bean) bypasses the proxy
//     — aspect does NOT fire. Document this on @LogUsage; call across
//     bean boundaries only.
//
//  2. ORDER vs @Transactional. Spring's @Transactional aspect has
//     `@Order(Ordered.LOWEST_PRECEDENCE)` (innermost). Putting this
//     aspect at `LOWEST_PRECEDENCE - 100` makes it OUTER — the usage
//     event is logged AFTER commit/rollback, with the final tx outcome
//     reflected as `success` or `error`.
//
//  3. EXCEPTION PATH. `try { proceed() } catch (Throwable) { rethrow }
//     finally { log }` guarantees we log both success and failure, and
//     never swallow exceptions.
//
//  4. SECURITY CONTEXT. Read via SecurityContextHolder (ThreadLocal) —
//     works for synchronous HTTP requests on the same thread. For @Async
//     code, configure
//     `SecurityContextHolder.setStrategyName(MODE_INHERITABLETHREADLOCAL)`
//     or wrap with DelegatingSecurityContextRunnable.
//
//  5. LOGGER IS @Async. UsageLogger.record() does its own DB write off
//     the request thread. This aspect only ASSEMBLES the event and hands
//     it off; the assembly must be cheap (no DB calls, no I/O).
//
//  6. NO RAW ARGS LOGGED. The aspect deliberately ignores
//     joinPoint.getArgs(). Anything sensitive — request bodies, JWTs,
//     PII — can't leak through this path.

package PACKAGE_REPLACE_ME.observability.usage;

import PACKAGE_REPLACE_ME.domain.observability.UsageEvent;
import PACKAGE_REPLACE_ME.domain.observability.UsageLogger;
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

import java.time.OffsetDateTime;
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
            .eventTimestamp(OffsetDateTime.now())
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
