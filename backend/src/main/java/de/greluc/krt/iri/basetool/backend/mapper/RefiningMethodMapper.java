package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.RefiningMethod;
import de.greluc.krt.iri.basetool.backend.model.dto.RefiningMethodDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", unmappedTargetPolicy = org.mapstruct.ReportingPolicy.IGNORE)
public interface RefiningMethodMapper {
    RefiningMethodDto toDto(RefiningMethod entity);
    RefiningMethod toEntity(RefiningMethodDto dto);
}