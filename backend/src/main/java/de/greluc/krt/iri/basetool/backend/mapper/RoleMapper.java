package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.Role;
import de.greluc.krt.iri.basetool.backend.model.dto.RoleDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", unmappedTargetPolicy = org.mapstruct.ReportingPolicy.IGNORE)
public interface RoleMapper {
  RoleDto toDto(Role role);

  Role toEntity(RoleDto dto);
}
