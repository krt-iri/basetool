package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.City;
import de.greluc.krt.iri.basetool.backend.model.dto.CityDto;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper between {@link City} entities and their admin-facing {@link CityDto} projection.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CityMapper {
  /**
   * Projects a city entity into the slim DTO used by the admin UEX-overrides page.
   *
   * @param entity managed city entity
   * @return DTO carrying only the fields surfaced to the admin UI
   */
  CityDto toDto(City entity);
}
