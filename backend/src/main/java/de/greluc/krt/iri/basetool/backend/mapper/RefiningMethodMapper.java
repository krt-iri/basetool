package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.RefiningMethod;
import de.greluc.krt.iri.basetool.backend.model.dto.RefiningMethodDto;
import org.mapstruct.Mapper;

/** MapStruct mapper between Refining Method entities and DTOs. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = org.mapstruct.ReportingPolicy.IGNORE)
public interface RefiningMethodMapper {
  /** Maps a {@link RefiningMethod} entity to its outbound DTO. */
  RefiningMethodDto toDto(RefiningMethod entity);

  /** Builds a new {@link RefiningMethod} entity from the inbound DTO. */
  RefiningMethod toEntity(RefiningMethodDto dto);
}
