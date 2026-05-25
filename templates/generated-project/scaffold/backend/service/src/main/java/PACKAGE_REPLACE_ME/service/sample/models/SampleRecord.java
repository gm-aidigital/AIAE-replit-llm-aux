// SampleRecord — reference shape for an immutable service-layer DTO.
// Use Java `record`. Fields come from the entity via the mapper.

package PACKAGE_REPLACE_ME.service.sample.models;

import java.time.LocalDateTime;

public record SampleRecord(
    Long id,
    String name,
    LocalDateTime updatedAt                           // LocalDateTime in UTC — see SKILL "Time types"
) { }
