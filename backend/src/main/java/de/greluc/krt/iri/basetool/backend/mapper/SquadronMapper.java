package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.Squadron;
import de.greluc.krt.iri.basetool.backend.model.dto.SquadronDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** MapStruct mapper between Squadron entities and DTOs. */
@Mapper(componentModel = "spring")
public interface SquadronMapper {
  /** Maps a {@link Squadron} entity to its outbound DTO. */
  SquadronDto toDto(Squadron entity);

  /**
   * Builds a new {@link Squadron} entity from the inbound DTO. Timestamps are owned by the
   * persistence provider and ignored.
   */
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  Squadron toEntity(SquadronDto dto);
}
