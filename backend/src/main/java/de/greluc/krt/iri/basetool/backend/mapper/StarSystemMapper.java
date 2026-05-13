package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.StarSystem;
import de.greluc.krt.iri.basetool.backend.model.dto.StarSystemDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", unmappedTargetPolicy = org.mapstruct.ReportingPolicy.IGNORE)
public interface StarSystemMapper {
  StarSystemDto toDto(StarSystem starSystem);

  StarSystem toEntity(StarSystemDto dto);
}
