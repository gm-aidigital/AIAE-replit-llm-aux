// UsageLoggingAspect — intercepts @LogUsage methods, records UsageEvent via
// UsageLogger. Behaviour invariants (preserve on every edit):
//  1. Self-invocation bypasses Spring AOP proxy → aspect won't fire.
//  2. @Order(LOWEST_PRECEDENCE - 100) — OUTER than @Transactional, so the
//     usage event reflects final commit/rollback outcome.
//  3. try { proceed() } catch (Throwable) { rethrow } finally { log }
//     — logs success + failure, never swallows.
//  4. SecurityContextHolder + UsageAttributes are ThreadLocal; for @Async
//     paths use MODE_INHERITABLETHREADLOCAL or DelegatingSecurityContextRunnable.
//  5. Logger is @Async (off-thread DB write). Aspect only assembles + hands off
//     — assembly must be cheap (no I/O).
//  6. joinPoint.getArgs() deliberately ignored — no payload/JWT/PII leakage.
//     Callers populate per-row attributes via UsageAttributes.put(...).
//  7. user_email + user display name extraction covers EVERY supported
//     AUTH_MODE via a shared principal-claim reader:
//        Bearer-JWT (sso/mock/auto) → principal is Jwt, scan known claim
//          names. email aliases: email, email_address, primary_email_address,
//          mail. name aliases: full_name, name, preferred_username. First
//          non-blank wins. IdP-side claim configuration is out of scope for
//          this template — but SecurityConfig wires
//          JwtAuthenticationConverter#setPrincipalClaimName("email"), so
//          when the JWT carries email, auth.getName() (→ user_id column)
//          also returns the email rather than the provider sub.
//        AUTH_MODE=replit → principal is OidcUser; prefer OidcUser#getEmail()
//          / OidcUser#getFullName() then fall back to the alias lists.
//        Any other oauth2Login → principal is OAuth2User; alias lists only.
//     Adding a new auth backend? Extend the alias lists or pass a new OIDC
//     standard-accessor into readPrincipalClaim — do not introduce a
//     parallel pipeline.
//  8. The BQ-aligned schema has no top-level correlation_id or user_name
//     column. The aspect embeds both inside `attributes` JSON
//     ({"correlation_id": "...", "user_name": "..."}) alongside any
//     caller-supplied UsageAttributes entries.
// Full contract: `templates/generated-project/observability/usage-logging-rules.md`.

package PACKAGE_REPLACE_ME.usagelogging;

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
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

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
    private static final String ATTR_CORRELATION_ID = "correlation_id";
    private static final String ATTR_USER_NAME = "user_name";

    /**
     * Claim names checked, in order, when reading the user email from a JWT
     * or OIDC/OAuth2 user. The list covers the names different identity
     * providers commonly use; the first non-blank value wins. IdP-side
     * configuration to surface email under one of these names is out of
     * scope for the template.
     */
    private static final String[] EMAIL_CLAIM_NAMES = {
        "email", "email_address", "primary_email_address", "mail"
    };

    /**
     * Full-name claim aliases. Clerk's templated JWT typically uses
     * {@code full_name} ({@code {{user.full_name}}}); OIDC standard uses
     * {@code name}; {@code preferred_username} is the OIDC fallback when
     * the IdP doesn't ship a display name. When the full-name lookup comes
     * up empty, the aspect falls back to composing
     * {@link #FIRST_NAME_CLAIM_NAMES} + {@link #LAST_NAME_CLAIM_NAMES}
     * (Clerk also exposes these as separate template variables, and OIDC
     * has {@code given_name} / {@code family_name}). The resulting value
     * lands inside the {@code attributes} JSON under {@code user_name}.
     */
    private static final String[] NAME_CLAIM_NAMES = {
        "full_name", "name", "preferred_username"
    };

    private static final String[] FIRST_NAME_CLAIM_NAMES = {
        "first_name", "given_name"
    };

    private static final String[] LAST_NAME_CLAIM_NAMES = {
        "last_name", "family_name"
    };

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
            } finally {
                // Always clear the per-request attribute bag; otherwise the
                // next request on the same worker thread inherits stale keys.
                UsageAttributes.clear();
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
            .attributes(buildAttributes(auth))
            .errorMessage(failed ? truncate(thrown.getMessage(), 500) : null)
            .clientIp(clientIp(request))
            .userAgent(userAgent(request))
            .build();
    }

    /**
     * Merges caller-supplied attributes from {@link UsageAttributes} with the
     * aspect-supplied entries. Today that's the MDC correlation id (the BQ-
     * aligned schema doesn't carry it as a top-level column) and the user's
     * display name lifted out of the JWT/OIDC principal so dashboards can
     * read {@code attributes->>'user_name'} without joining role tables.
     *
     * @param auth current Spring Security authentication (may be null)
     * @return merged map, or null when nothing was contributed
     */
    private static Map<String, Object> buildAttributes(Authentication auth) {
        Map<String, Object> caller = UsageAttributes.snapshot();
        String correlation = MDC.get(MDC_CORRELATION);
        String userName = extractName(auth);
        boolean nothing = caller == null
            && (correlation == null || correlation.isBlank())
            && (userName == null || userName.isBlank());
        if (nothing) {
            return null;
        }
        Map<String, Object> merged = new LinkedHashMap<>();
        if (correlation != null && !correlation.isBlank()) {
            merged.put(ATTR_CORRELATION_ID, correlation);
        }
        if (userName != null && !userName.isBlank()) {
            merged.put(ATTR_USER_NAME, userName);
        }
        if (caller != null) {
            merged.putAll(caller);
        }
        return merged;
    }

    /**
     * Extracts the user email from whatever principal Spring Security
     * produced. Three live principal shapes across the supported AUTH_MODEs;
     * see the file header for the per-mode contract.
     *
     * @param auth current Spring Security authentication
     * @return email value, or null when unavailable
     */
    private static String extractEmail(Authentication auth) {
        return readPrincipalClaim(auth, EMAIL_CLAIM_NAMES, OidcUser::getEmail);
    }

    /**
     * Extracts the user's display name from the principal. Resolution order:
     * <ol>
     *   <li>{@link OidcUser#getFullName()} (Replit OIDC) /
     *       {@code full_name} / {@code name} / {@code preferred_username} —
     *       first non-blank wins.</li>
     *   <li>Compose {@code first_name + " " + last_name} (Clerk template
     *       variables) or OIDC {@code given_name + " " + family_name}.</li>
     *   <li>If only one half of the pair is present, return that half.</li>
     * </ol>
     * The composed fallback covers Clerk users whose
     * {@code {{user.full_name}}} template variable resolved to blank but
     * whose first/last name claims are still in the JWT.
     *
     * @param auth current Spring Security authentication
     * @return display name, or null when no naming claim is present
     */
    private static String extractName(Authentication auth) {
        String fullName = readPrincipalClaim(auth, NAME_CLAIM_NAMES, OidcUser::getFullName);
        if (fullName != null && !fullName.isBlank()) {
            return fullName;
        }
        String first = readPrincipalClaim(auth, FIRST_NAME_CLAIM_NAMES, OidcUser::getGivenName);
        String last = readPrincipalClaim(auth, LAST_NAME_CLAIM_NAMES, OidcUser::getFamilyName);
        boolean hasFirst = first != null && !first.isBlank();
        boolean hasLast = last != null && !last.isBlank();
        if (hasFirst && hasLast) {
            return first + " " + last;
        }
        if (hasFirst) {
            return first;
        }
        if (hasLast) {
            return last;
        }
        return null;
    }

    /**
     * Shared backbone for principal-claim reads. Walks the alias list across
     * Jwt / OidcUser / OAuth2User principal shapes and returns the first
     * non-blank match. For OidcUser an OIDC-standard accessor (e.g.
     * {@link OidcUser#getEmail()}) is checked first so a canonical value
     * wins over a non-standard claim alias.
     *
     * @param auth current Spring Security authentication
     * @param aliases claim names to try, in order
     * @param oidcStandard OIDC standard accessor when the principal is an
     *                     {@link OidcUser}; consulted before the alias list
     * @return first non-blank value, or null
     */
    private static String readPrincipalClaim(Authentication auth,
                                             String[] aliases,
                                             Function<OidcUser, String> oidcStandard) {
        if (auth == null) {
            return null;
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof Jwt jwt) {
            return firstClaim(jwt.getClaims()::get, aliases);
        }
        if (principal instanceof OidcUser oidc) {
            String standard = oidcStandard.apply(oidc);
            if (standard != null && !standard.isBlank()) {
                return standard;
            }
            return firstClaim(oidc.getClaims()::get, aliases);
        }
        if (principal instanceof OAuth2User oauth) {
            return firstClaim(oauth.getAttributes()::get, aliases);
        }
        return null;
    }

    /**
     * Walks the supplied alias list and returns the first non-blank value the
     * lookup function produces. Tolerant of {@code null} (missing) and
     * blank-string ({@code ""}) claim values.
     *
     * @param lookup function that resolves a claim/attribute name to its value
     * @param aliases claim names to try, in order
     * @return first matching value as a string, or null
     */
    private static String firstClaim(Function<String, Object> lookup, String[] aliases) {
        for (String name : aliases) {
            Object value = lookup.apply(name);
            if (value == null) {
                continue;
            }
            String s = String.valueOf(value);
            if (!s.isBlank()) {
                return s;
            }
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
