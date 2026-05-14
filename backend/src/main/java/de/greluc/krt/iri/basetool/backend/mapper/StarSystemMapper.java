package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.StarSystem;
import de.greluc.krt.iri.basetool.backend.model.dto.StarSystemDto;
import org.mapstruct.Mapper;

/** MapStruct mapper between Star System entities and DTOs. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = org.mapstruct.ReportingPolicy.IGNORE)
public interface StarSystemMapper {
  /** Maps a {@link StarSystem} entity to its outbound DTO. */
  StarSystemDto toDto(StarSystem starSystem);

  /** Builds a new {@link StarSystem} entity from the inbound DTO. */
  StarSystem toEntity(StarSystemDto dto);
}
