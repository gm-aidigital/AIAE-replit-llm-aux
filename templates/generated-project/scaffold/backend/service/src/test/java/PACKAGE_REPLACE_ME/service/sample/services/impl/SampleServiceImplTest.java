// ─────────────────────────────────────────────────────────────────────────
// SCAFFOLD EXAMPLE — REFERENCE ONLY. DELETE before the first real aggregate
// lands. Run `scripts/strip-scaffold-samples.sh` for one-shot removal.
// See structure/near-production-project-structure.md → "Scaffold sample
// aggregate (reference fixture — MUST be stripped)" for the contract.
// ─────────────────────────────────────────────────────────────────────────
// SampleServiceImplTest — SHAPE template, mirrors SampleServiceImpl.
// References domain types (SampleEntity, SampleRepository) the real project
// must provide. Compiles once the agent has filled in the domain module
// per the structure doc.

package PACKAGE_REPLACE_ME.service.sample.services.impl;

import PACKAGE_REPLACE_ME.domain.sample.entities.SampleEntity;
import PACKAGE_REPLACE_ME.domain.sample.repositories.SampleRepository;
import PACKAGE_REPLACE_ME.service.common.error.AppException;
import PACKAGE_REPLACE_ME.service.common.error.ErrorReason;
import PACKAGE_REPLACE_ME.service.common.time.CurrentTime;
import PACKAGE_REPLACE_ME.service.mappers.sample.SampleMapper;
import PACKAGE_REPLACE_ME.service.sample.models.SampleRecord;
import PACKAGE_REPLACE_ME.service.sample.models.SampleUpdate;
import PACKAGE_REPLACE_ME.usagelogging.UsageAttributes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SampleServiceImplTest {

    @Mock SampleRepository repo;
    @Mock SampleMapper mapper;
    @Mock CurrentTime currentTime;
    @Mock UsageAttributes usageAttributes;
    @InjectMocks SampleServiceImpl service;

    @Test
    void shouldReturnRecordWhenEntityExistsTest() {
        // Given:
        SampleEntity entity = new SampleEntity();
        SampleRecord record = new SampleRecord(1L, "n", LocalDateTime.of(2026, 1, 1, 0, 0));
        when(repo.findById(1L)).thenReturn(Optional.of(entity));
        when(mapper.toRecord(entity)).thenReturn(record);

        // When:
        SampleRecord result = service.findById(1L);

        // Then:
        assertThat(result).isEqualTo(record);
    }

    @Test
    void shouldThrowAppExceptionWhenEntityMissingTest() {
        // Given:
        when(repo.findById(42L)).thenReturn(Optional.empty());

        // When:
        Throwable thrown = catchThrowable(() -> service.findById(42L));

        // Then:
        assertThat(thrown)
            .isInstanceOf(AppException.class)
            .hasFieldOrPropertyWithValue("code", ErrorReason.C001.getCode());
    }

    @Test
    void shouldUpdateEntityWithMapperAndCurrentTimeTest() {
        // Given:
        SampleEntity entity = new SampleEntity();
        SampleUpdate update = new SampleUpdate("next");
        LocalDateTime now = LocalDateTime.of(2026, 2, 3, 4, 5, 6);
        SampleRecord record = new SampleRecord(1L, "next", now);
        when(repo.findById(1L)).thenReturn(Optional.of(entity));
        when(currentTime.nowLocalDateTime()).thenReturn(now);
        when(repo.save(entity)).thenReturn(entity);
        when(mapper.toRecord(entity)).thenReturn(record);

        // When:
        SampleRecord result = service.update(1L, update);

        // Then:
        verify(mapper).updateEntity(update, entity);
        assertThat(entity.getUpdatedAt()).isEqualTo(now);
        assertThat(result).isEqualTo(record);
    }
}
