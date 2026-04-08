package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.Material;
import de.greluc.krt.iri.basetool.backend.model.dto.MaterialDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", unmappedTargetPolicy = org.mapstruct.ReportingPolicy.IGNORE)
public interface MaterialMapper {
    @Mapping(target = "isIllegal", expression = "java(entity.getIsIllegal() != null && entity.getIsIllegal() == 1)")
    @Mapping(target = "isVolatileQt", expression = "java(entity.getIsVolatileQt() != null && entity.getIsVolatileQt() == 1)")
    @Mapping(target = "isVolatileTime", expression = "java(entity.getIsVolatileTime() != null && entity.getIsVolatileTime() == 1)")
    MaterialDto toDto(Material entity);

    @Mapping(target = "isIllegal", expression = "java(dto.isIllegal() != null && dto.isIllegal() ? 1 : 0)")
    @Mapping(target = "isVolatileQt", expression = "java(dto.isVolatileQt() != null && dto.isVolatileQt() ? 1 : 0)")
    @Mapping(target = "isVolatileTime", expression = "java(dto.isVolatileTime() != null && dto.isVolatileTime() ? 1 : 0)")
    Material toEntity(MaterialDto dto);

    default Boolean mapIsIllegal(Integer value) {
        return value != null && value == 1;
    }

    default Integer mapIsIllegal(Boolean value) {
        return value != null && value ? 1 : 0;
    }
}