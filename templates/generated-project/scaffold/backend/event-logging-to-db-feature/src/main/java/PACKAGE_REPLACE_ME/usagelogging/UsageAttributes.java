// UsageAttributes — request-scoped key/value bag for the per-row `attributes`
// JSON column. Business code inside an @LogUsage method calls
// `UsageAttributes.put("geo", request.geo())`; the aspect drains the bag in
// its finally{} block, embeds the map into UsageEvent.attributes, and clears
// the ThreadLocal. Lives in the event-logging-to-db-feature module so
// service-impl classes can import it without depending on application/.
//
// Contract:
//  - Sync calls only. ThreadLocal does NOT propagate to @Async worker
//    threads — if the annotated method is @Async, set
//    SecurityContextHolder.MODE_INHERITABLETHREADLOCAL or use
//    DelegatingSecurityContextRunnable, and apply the same pattern here.
//  - Never PII: emails of third parties, raw doc bodies, API keys, JWTs.
//    See observability/usage-logging-rules.md → "Sensitive data".
//  - Values must be JSON-serialisable (String, Number, Boolean, Map, List).
//    Hibernate's @JdbcTypeCode(SqlTypes.JSON) maps via Jackson.

package PACKAGE_REPLACE_ME.usagelogging;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-request attribute bag drained by {@link UsageLoggingAspect} into the
 * `attributes` JSON column. Static-only — callers use {@link #put} from
 * inside an {@code @LogUsage}-annotated method body.
 */
public final class UsageAttributes {

    private static final ThreadLocal<Map<String, Object>> BAG = new ThreadLocal<>();

    private UsageAttributes() { }

    /**
     * Records one attribute on the in-flight event. Subsequent
     * {@code put(key, ...)} calls with the same key overwrite. Silently
     * ignores {@code null} keys and {@code null} values to keep call sites
     * defensive.
     *
     * @param key attribute key (alphanumeric / underscore preferred)
     * @param value JSON-serialisable value
     */
    public static void put(String key, Object value) {
        if (key == null || value == null) {
            return;
        }
        Map<String, Object> map = BAG.get();
        if (map == null) {
            map = new LinkedHashMap<>();
            BAG.set(map);
        }
        map.put(key, value);
    }

    /**
     * Returns the accumulated attributes for the current thread without
     * clearing them. Aspect-only usage path.
     *
     * @return immutable snapshot, or null when no attributes were recorded
     */
    static Map<String, Object> snapshot() {
        Map<String, Object> map = BAG.get();
        return (map == null || map.isEmpty()) ? null : Map.copyOf(map);
    }

    /**
     * Discards the attribute bag for the current thread. The aspect calls
     * this in finally{} after the event is dispatched so the next request
     * on the same worker thread starts clean.
     */
    static void clear() {
        BAG.remove();
    }
}
