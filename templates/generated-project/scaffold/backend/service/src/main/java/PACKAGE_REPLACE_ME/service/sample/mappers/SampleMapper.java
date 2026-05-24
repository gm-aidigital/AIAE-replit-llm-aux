// SampleMapper — reference Entity ↔ Record mapper. One mapper per entity
// per layer (this is the service-layer mapper).
//
// Composition rule: when this entity contains another that has its own
// mapper (e.g. SampleItemEntity → SampleItemMapper), declare
// `@Mapper(uses = SampleItemMapper.class)` here and let MapStruct wire
// the nested call automatically. Never duplicate nested conversion code.

package PACKAGE_REPLACE_ME.service.sample.mappers;

import PACKAGE_REPLACE_ME.domain.sample.entities.SampleEntity;
import PACKAGE_REPLACE_ME.service.sample.models.SampleRecord;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface SampleMapper {
    SampleRecord toRecord(SampleEntity entity);
    SampleEntity toEntity(SampleRecord record);
    List<SampleRecord> toRecords(List<SampleEntity> entities);
}
