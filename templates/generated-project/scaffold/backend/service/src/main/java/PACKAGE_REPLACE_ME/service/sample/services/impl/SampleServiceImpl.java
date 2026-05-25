// SampleServiceImpl — reference impl shape. See SampleService for the
// canonical layout rules.
//
// This file references types (SampleRepository, SampleEntity) that the
// real project must provide in the domain module. The SHAPE — class
// structure, annotations, dependency wiring, error-throwing pattern — is
// what Agent copies.

package PACKAGE_REPLACE_ME.service.sample.services.impl;

import PACKAGE_REPLACE_ME.domain.sample.entities.SampleEntity;
import PACKAGE_REPLACE_ME.domain.sample.repositories.SampleRepository;
import PACKAGE_REPLACE_ME.service.common.error.AppException;
import PACKAGE_REPLACE_ME.service.common.error.ErrorReason;
import PACKAGE_REPLACE_ME.service.common.observability.LogUsage;
import PACKAGE_REPLACE_ME.service.sample.mappers.SampleMapper;
import PACKAGE_REPLACE_ME.service.sample.models.SampleRecord;
import PACKAGE_REPLACE_ME.service.sample.models.SampleUpdate;
import PACKAGE_REPLACE_ME.service.sample.services.SampleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Default implementation of the reference sample service.
 */
@Service                                              // Only the impl carries @Service.
@RequiredArgsConstructor
public class SampleServiceImpl implements SampleService {

    private final SampleRepository repo;
    private final SampleMapper mapper;

    @Override
    @LogUsage(action = "sample.find")                 // ← aspect logs this call to usage_events
    public SampleRecord findById(Long id) {
        return repo.findById(id)
            .map(mapper::toRecord)                    // map BEFORE returning (no entity escape)
            .orElseThrow(() -> new AppException(ErrorReason.C001, id));
    }

    @Override
    @LogUsage(action = "sample.update")               // ← aspect logs success or error automatically
    public SampleRecord update(Long id, SampleUpdate update) {
        SampleEntity entity = repo.findById(id)
            .orElseThrow(() -> new AppException(ErrorReason.C001, id));
        applyUpdate(entity, update);
        return mapper.toRecord(repo.save(entity));
    }

    /**
     * Copies writable fields from {@link SampleUpdate} onto the entity via setters.
     * Service-side, NOT on the entity itself — keeps domain a leaf module
     * (entity must not import SampleUpdate or anything from the service module).
     *
     * @param entity managed JPA entity loaded by the caller
     * @param update writable-fields request payload
     */
    private void applyUpdate(SampleEntity entity, SampleUpdate update) {
        if (update.name() != null) {
            entity.setName(update.name());
        }
        entity.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
    }
}
