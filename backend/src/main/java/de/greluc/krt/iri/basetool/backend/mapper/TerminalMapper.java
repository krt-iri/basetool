package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.Terminal;
import de.greluc.krt.iri.basetool.backend.model.dto.TerminalDto;
import org.mapstruct.Mapper;

/** MapStruct mapper between Terminal entities and DTOs. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = org.mapstruct.ReportingPolicy.IGNORE)
public interface TerminalMapper {
  /** Maps a {@link Terminal} entity to its outbound DTO. */
  TerminalDto toDto(Terminal entity);

  /** Builds a new {@link Terminal} entity from the inbound DTO. */
  Terminal toEntity(TerminalDto dto);
}
