// SampleService — reference shape Agent copies/renames for every new
// service. Demonstrates the canonical service-layer layout:
//
//   service/<base>/service/<aggregate>/
//     services/<X>Service.java         ← interface (this file)
//     services/impl/<X>ServiceImpl.java ← @Service impl
//     mappers/<X>Mapper.java            ← Entity ↔ Record, one per entity
//     models/<X>Record.java             ← immutable record (output)
//     models/<X>Update.java             ← request payload (input)
//
// Hard rules (see backend-java-feature SKILL):
//   - Interface and impl in DIFFERENT packages (services/ vs services/impl/).
//   - Only the impl carries @Service. Callers inject the interface.
//   - Each public method gets @LogUsage on the impl side (interfaces don't
//     trigger Spring AOP proxying reliably for annotation-based pointcuts).
//   - Methods return Record / page-of-Record, never Entity.
//   - Throw AppException with ErrorReason — never a per-domain enum.
//
// Delete this whole sample/ tree once the project has a real aggregate.

package PACKAGE_REPLACE_ME.service.sample.services;

import PACKAGE_REPLACE_ME.service.sample.models.SampleRecord;
import PACKAGE_REPLACE_ME.service.sample.models.SampleUpdate;

public interface SampleService {

    /** Read — controller-level @Transactional(readOnly=true) handles the tx. */
    SampleRecord findById(Long id);

    /** Write — controller-level @Transactional handles the tx. */
    SampleRecord update(Long id, SampleUpdate update);
}
