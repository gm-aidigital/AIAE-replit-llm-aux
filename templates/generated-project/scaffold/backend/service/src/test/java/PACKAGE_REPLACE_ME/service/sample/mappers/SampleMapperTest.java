// SCAFFOLD EXAMPLE — REFERENCE ONLY. DELETE before the first real aggregate
// lands (stripped by scripts/strip-scaffold-samples.sh alongside the sample
// aggregate). Demonstrates testing a MapStruct mapper via its generated impl.
package PACKAGE_REPLACE_ME.service.sample.mappers;

import PACKAGE_REPLACE_ME.domain.sample.entities.SampleEntity;
import PACKAGE_REPLACE_ME.service.sample.models.SampleRecord;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SampleMapperTest {

    private final SampleMapper mapper = new SampleMapperImpl();

    @Test
    void shouldMapEntityToRecordTest() {
        // Given:
        SampleEntity entity = new SampleEntity();
        entity.setId(7L);
        entity.setName("widget");
        entity.setUpdatedAt(LocalDateTime.of(2026, 1, 2, 3, 4, 5));

        // When:
        SampleRecord record = mapper.toRecord(entity);

        // Then:
        assertThat(record.id()).isEqualTo(7L);
        assertThat(record.name()).isEqualTo("widget");
        assertThat(record.updatedAt()).isEqualTo(LocalDateTime.of(2026, 1, 2, 3, 4, 5));
    }

    @Test
    void shouldMapRecordToEntityTest() {
        // Given:
        SampleRecord record = new SampleRecord(9L, "gadget", LocalDateTime.of(2026, 5, 6, 7, 8, 9));

        // When:
        SampleEntity entity = mapper.toEntity(record);

        // Then:
        assertThat(entity.getId()).isEqualTo(9L);
        assertThat(entity.getName()).isEqualTo("gadget");
        assertThat(entity.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 5, 6, 7, 8, 9));
    }

    @Test
    void shouldMapEntityListToRecordsTest() {
        // Given:
        SampleEntity entity = new SampleEntity();
        entity.setId(1L);
        entity.setName("a");
        entity.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 0, 0, 0));

        // When:
        List<SampleRecord> records = mapper.toRecords(List.of(entity));

        // Then:
        assertThat(records).hasSize(1);
        assertThat(records.get(0).id()).isEqualTo(1L);
        assertThat(records.get(0).name()).isEqualTo("a");
    }
}
