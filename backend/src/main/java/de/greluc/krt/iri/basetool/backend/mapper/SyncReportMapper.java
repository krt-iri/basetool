package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.ExternalSyncReport;
import de.greluc.krt.iri.basetool.backend.model.dto.SyncReportDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper from {@link ExternalSyncReport} to its read-only {@link SyncReportDto}. The two
 * enum fields are flattened to their {@code name()} so the wire shape carries plain strings.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = org.mapstruct.ReportingPolicy.IGNORE)
public interface SyncReportMapper {

  /**
   * Maps an audit-log entity to its DTO, flattening {@code sourceSystem} / {@code eventType} to
   * their enum names.
   *
   * @param entity the persistent audit row
   * @return the wire DTO
   */
  @Mapping(target = "sourceSystem", expression = "java(entity.getSourceSystem().name())")
  @Mapping(target = "eventType", expression = "java(entity.getEventType().name())")
  SyncReportDto toDto(ExternalSyncReport entity);
}
