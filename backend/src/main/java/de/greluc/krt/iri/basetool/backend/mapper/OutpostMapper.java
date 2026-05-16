package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.Outpost;
import de.greluc.krt.iri.basetool.backend.model.dto.OutpostDto;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper between {@link Outpost} entities and their admin-facing {@link OutpostDto}
 * projection.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OutpostMapper {
  /**
   * Projects an outpost entity into the slim DTO used by the admin UEX-overrides page.
   *
   * @param entity managed outpost entity
   * @return DTO carrying only the fields surfaced to the admin UI
   */
  OutpostDto toDto(Outpost entity);
}
