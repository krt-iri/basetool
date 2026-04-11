package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.JobOrder;
import de.greluc.krt.iri.basetool.backend.model.JobOrderMaterial;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderDto;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderMaterialDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {InventoryItemMapper.class, UserMapper.class, MaterialMapper.class, JobOrderHandoverMapper.class})
public interface JobOrderMapper {

    JobOrderDto toDto(JobOrder jobOrder);

    @Mapping(target = "currentStock", ignore = true)
    JobOrderMaterialDto toDto(JobOrderMaterial material);
}
