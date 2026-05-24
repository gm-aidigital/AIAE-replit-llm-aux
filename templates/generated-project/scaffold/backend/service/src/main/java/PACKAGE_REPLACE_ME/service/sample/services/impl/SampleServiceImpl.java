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
     * Copies the writable fields from {@link SampleUpdate} onto the entity.
     * Kept private + small so the public method body reads top-to-bottom
     * as a workflow; field-level conditionals live here.
     */
    private void applyUpdate(SampleEntity entity, SampleUpdate update) {
        entity.apply(update);
    }
}
