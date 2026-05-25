// UsageLogger — sink interface. Implementations live in application/
// (PostgresUsageLogger, NoOpUsageLogger). Business code NEVER calls
// this directly — the @LogUsage annotation + UsageLoggingAspect own
// dispatch. See observability/usage-logging-rules.md.

package PACKAGE_REPLACE_ME.service.common.observability;

/**
 * Sink for usage logging events emitted by service methods.
 */
public interface UsageLogger {

    /**
     * Persists a single usage event. Must NOT block / throw into the caller
     * — implementations swallow infra errors and surface them via local
     * warning logs.
     *
     * @param event fully populated event from UsageLoggingAspect
     */
    void record(UsageEvent event);
}
