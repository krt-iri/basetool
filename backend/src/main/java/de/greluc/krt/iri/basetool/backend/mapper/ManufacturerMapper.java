package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.Manufacturer;
import de.greluc.krt.iri.basetool.backend.model.dto.ManufacturerDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", unmappedTargetPolicy = org.mapstruct.ReportingPolicy.IGNORE)
public interface ManufacturerMapper {
    ManufacturerDto toDto(Manufacturer entity);
    Manufacturer toEntity(ManufacturerDto dto);
}