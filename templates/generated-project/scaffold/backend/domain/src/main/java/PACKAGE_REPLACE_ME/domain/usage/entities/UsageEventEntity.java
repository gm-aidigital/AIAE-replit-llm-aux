// UsageEventEntity — JPA mapping for usage_events (Liquibase 0001-usage-events.xml).
// Lives in domain so JPA never escapes to service/application. Service
// uses the immutable UsageEvent record; PostgresUsageLogger maps record
// → entity at the persistence boundary.

package PACKAGE_REPLACE_ME.domain.usage.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Persistence model for a usage logging event.
 */
@Entity
@Table(name = "usage_events")
@Getter
@Setter
@NoArgsConstructor
public class UsageEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", unique = true, nullable = false)
    private String eventId;

    @Column(name = "event_timestamp", nullable = false)
    private LocalDateTime eventTimestamp;

    @Column(nullable = false)
    private String service;

    private String environment;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    private String action;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "user_email")
    private String userEmail;

    private String status;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "client_ip")
    private String clientIp;

    @Column(name = "user_agent")
    private String userAgent;
}
