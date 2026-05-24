// SampleUpdate — reference shape for a service-layer input record. Carries
// only the fields a caller may change. Validation lives on the request
// DTO (in application/) AND on the entity via bean-validation; this
// record is the plain Java carrier between them.

package PACKAGE_REPLACE_ME.service.sample.models;

public record SampleUpdate(
    String name
) {}
