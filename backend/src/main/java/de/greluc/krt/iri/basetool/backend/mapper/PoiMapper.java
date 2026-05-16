package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.Poi;
import de.greluc.krt.iri.basetool.backend.model.dto.PoiDto;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper between {@link Poi} entities and their admin-facing {@link PoiDto} projection.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PoiMapper {
  /**
   * Projects a POI entity into the slim DTO used by the admin UEX-overrides page.
   *
   * @param entity managed POI entity
   * @return DTO carrying only the fields surfaced to the admin UI
   */
  PoiDto toDto(Poi entity);
}
