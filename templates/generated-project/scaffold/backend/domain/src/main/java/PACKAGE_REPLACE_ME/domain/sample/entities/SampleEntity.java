// SampleEntity — reference JPA entity for the sample aggregate. Plain
// POJO + getters/setters via Lombok; no business logic, no service
// imports (domain is a leaf module). Service-layer code copies field
// values from SampleUpdate via setters — entity does NOT accept
// SampleUpdate (would force domain → service edge).
//
// Delete this whole sample tree when the project has a real aggregate.

package PACKAGE_REPLACE_ME.domain.sample.entities;

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
 * Persistence model for the reference sample aggregate.
 */
@Entity
@Table(name = "samples")
@Getter
@Setter
@NoArgsConstructor
public class SampleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
