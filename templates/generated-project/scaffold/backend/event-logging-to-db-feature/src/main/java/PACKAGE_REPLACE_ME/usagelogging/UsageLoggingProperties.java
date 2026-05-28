// UsageLoggingProperties — @ConfigurationProperties("app.usage-logging").
// Binding spec: observability/usage-logging-rules.md → "Required env placeholders".

package PACKAGE_REPLACE_ME.usagelogging;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed configuration for usage event persistence.
 */
@ConfigurationProperties(prefix = "app.usage-logging")
public class UsageLoggingProperties {

    /** Master switch. Set to false to silence the aspect via NoOpUsageLogger. */
    private boolean enabled = true;

    /** Stable lowercase-hyphen identifier (e.g. `employee-directory`). Must
     *  NOT remain the template placeholder `replit-mvp-template` in deployment. */
    private String serviceName;

    /** `prod` | `staging` | `dev`. */
    private String environment = "dev";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String s) {
        this.serviceName = s;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String e) {
        this.environment = e;
    }
}
