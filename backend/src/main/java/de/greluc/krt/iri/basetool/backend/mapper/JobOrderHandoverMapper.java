package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.JobOrderHandover;
import de.greluc.krt.iri.basetool.backend.model.JobOrderHandoverItem;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderHandoverDto;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderHandoverItemDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(
    componentModel = "spring",
    uses = {MaterialMapper.class})
public interface JobOrderHandoverMapper {

  @Mapping(source = "jobOrder.id", target = "jobOrderId")
  JobOrderHandoverDto toDto(JobOrderHandover jobOrderHandover);

  @Mapping(source = "jobOrderHandover.id", target = "jobOrderHandoverId")
  JobOrderHandoverItemDto toDto(JobOrderHandoverItem jobOrderHandoverItem);
}
