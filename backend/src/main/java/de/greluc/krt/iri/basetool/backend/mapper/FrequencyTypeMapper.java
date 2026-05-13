package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.FrequencyType;
import de.greluc.krt.iri.basetool.backend.model.dto.FrequencyTypeDto;
import org.mapstruct.Mapper;

/** MapStruct mapper between Frequency Type entities and DTOs. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = org.mapstruct.ReportingPolicy.IGNORE)
public interface FrequencyTypeMapper {
  /** Maps a {@link FrequencyType} entity to its outbound DTO. */
  FrequencyTypeDto toDto(FrequencyType entity);

  /** Builds a new {@link FrequencyType} entity from the inbound DTO. */
  FrequencyType toEntity(FrequencyTypeDto dto);
}
