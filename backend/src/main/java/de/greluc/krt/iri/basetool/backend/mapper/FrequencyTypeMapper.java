package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.FrequencyType;
import de.greluc.krt.iri.basetool.backend.model.dto.FrequencyTypeDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", unmappedTargetPolicy = org.mapstruct.ReportingPolicy.IGNORE)
public interface FrequencyTypeMapper {
  FrequencyTypeDto toDto(FrequencyType entity);

  FrequencyType toEntity(FrequencyTypeDto dto);
}
