package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.Manufacturer;
import de.greluc.krt.iri.basetool.backend.model.dto.ManufacturerDto;
import org.mapstruct.Mapper;

/** MapStruct mapper between Manufacturer entities and DTOs. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = org.mapstruct.ReportingPolicy.IGNORE)
public interface ManufacturerMapper {
  /** Maps a {@link Manufacturer} entity to its outbound DTO. */
  ManufacturerDto toDto(Manufacturer entity);

  /** Builds a new {@link Manufacturer} entity from the inbound DTO. */
  Manufacturer toEntity(ManufacturerDto dto);
}
