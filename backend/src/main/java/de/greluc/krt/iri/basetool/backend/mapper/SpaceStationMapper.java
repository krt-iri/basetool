package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.SpaceStation;
import de.greluc.krt.iri.basetool.backend.model.dto.SpaceStationDto;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper between {@link SpaceStation} entities and their admin-facing {@link
 * SpaceStationDto} projection.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SpaceStationMapper {
  /**
   * Projects a space-station entity into the slim DTO used by the admin UEX-overrides page.
   *
   * @param entity managed space-station entity
   * @return DTO carrying only the fields surfaced to the admin UI
   */
  SpaceStationDto toDto(SpaceStation entity);
}
