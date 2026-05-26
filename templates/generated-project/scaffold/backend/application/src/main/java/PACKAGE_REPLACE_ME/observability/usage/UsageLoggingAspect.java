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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Captures {@link LogUsage} service calls and emits structured usage events.
 */
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)   // OUTER than @Transactional (see header)
@ConditionalOnProperty(name = "app.usage-logging.enabled",
                       havingValue = "true",
                       matchIfMissing = true)
public class UsageLoggingAspect {

    private static final Logger LOG = LoggerFactory.getLogger(UsageLoggingAspect.class);

    private static final String EVENT_TYPE_ERROR = "error";
    private static final String STATUS_SUCCESS = "success";
    private static final String STATUS_ERROR = "error";
    private static final String MDC_CORRELATION = "correlationId";

    private final UsageLogger usageLogger;
    private final UsageLoggingProperties props;

    public UsageLoggingAspect(UsageLogger usageLogger, UsageLoggingProperties props) {
        this.usageLogger = usageLogger;
        this.props = props;
    }

    /**
     * Records success or failure metadata around an annotated service method.
     *
     * @param joinPoint intercepted service method
     * @param logUsage annotation values from the method
     * @return original method result
     * @throws Throwable original method failure, always rethrown unchanged
     */
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
                usageLogger.record(buildEvent(logUsage, thrown, durationMs));
            } catch (Throwable loggingFailure) {
                // Last resort: if even assembling/dispatching the event throws,
                // do NOT propagate into the caller's flow.
                LOG.warn("Usage logging failed for action={}: {}",
                         logUsage.action(), loggingFailure.getMessage());
            }
        }
    }

    /**
     * Builds the immutable event payload from the invocation context.
     *
     * @param logUsage annotation values from the method
     * @param thrown method failure, or null on success
     * @param durationMs elapsed method duration in milliseconds
     * @return assembled usage event
     */
    private UsageEvent buildEvent(LogUsage logUsage, Throwable thrown, long durationMs) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userId = (auth != null && auth.isAuthenticated()) ? auth.getName() : null;
        String userEmail = extractEmail(auth);
        boolean failed = thrown != null;
        HttpServletRequest request = currentRequest();

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
            .clientIp(clientIp(request))
            .userAgent(userAgent(request))
            .build();
    }

    /**
     * Extracts the user email claim from a JWT authentication.
     *
     * @param auth current Spring Security authentication
     * @return email claim value, or null when unavailable
     */
    private String extractEmail(Authentication auth) {
        if (auth == null) {
            return null;
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof org.springframework.security.oauth2.jwt.Jwt jwt) {
            Object email = jwt.getClaims().get("email");
            return email != null ? String.valueOf(email) : null;
        }
        return null;
    }

    /**
     * Reads the current servlet request from Spring's request context.
     *
     * @return current request, or null outside an HTTP request
     */
    private HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return attrs instanceof ServletRequestAttributes servletAttrs
            ? servletAttrs.getRequest()
            : null;
    }

    /**
     * Resolves the client IP address with proxy support.
     *
     * @param request current HTTP request
     * @return first forwarded IP or remote address, or null without a request
     */
    private String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",", 2)[0].strip();
        }
        return request.getRemoteAddr();
    }

    /**
     * Reads the user-agent header for audit context.
     *
     * @param request current HTTP request
     * @return truncated user-agent value, or null without a request
     */
    private String userAgent(HttpServletRequest request) {
        return request == null ? null : truncate(request.getHeader("User-Agent"), 500);
    }

    /**
     * Truncates long strings to a safe storage length.
     *
     * @param s input value
     * @param max maximum returned length
     * @return null, unchanged value, or truncated value
     */
    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
