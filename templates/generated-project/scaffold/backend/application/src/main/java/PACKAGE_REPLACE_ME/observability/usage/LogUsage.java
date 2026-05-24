// @LogUsage — annotation that triggers UsageLoggingAspect.
//
// Apply to PUBLIC methods on Spring-managed beans (typically @Service
// implementations) that you want to record as user actions. The aspect
// captures action, user, status, duration, and writes a UsageEvent row
// via UsageLogger.
//
// !!! Self-invocation does NOT trigger the aspect !!!
// Spring AOP works via proxies. `this.annotatedMethod()` inside the same
// class bypasses the proxy. Always call the annotated method from another
// Spring bean (controller → service.update(...) — works; service.foo()
// calls this.bar() in the same class — bar's aspect does NOT fire).
//
// Don't log secrets: the aspect logs `action` + `eventType` + user +
// duration + status. It does NOT serialise method arguments. If you need
// to record a domain-specific attribute, call UsageLogger directly inside
// the method body for that one detail.

package PACKAGE_REPLACE_ME.observability.usage;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface LogUsage {

    /**
     * Dotted lowercase action name, e.g. "employee.update", "report.export".
     * Required. Becomes UsageEvent.action.
     */
    String action();

    /**
     * Event category. Defaults to "api_request". Other values: "auth",
     * "custom". Errors are inferred automatically when the method throws.
     */
    String eventType() default "api_request";
}
