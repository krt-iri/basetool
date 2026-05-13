package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.Role;
import de.greluc.krt.iri.basetool.backend.model.dto.RoleDto;
import org.mapstruct.Mapper;

/** MapStruct mapper between Role entities and DTOs. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = org.mapstruct.ReportingPolicy.IGNORE)
public interface RoleMapper {
  /** Maps a {@link Role} entity to its outbound DTO. */
  RoleDto toDto(Role role);

  /** Builds a new {@link Role} entity from the inbound DTO. */
  Role toEntity(RoleDto dto);
}
