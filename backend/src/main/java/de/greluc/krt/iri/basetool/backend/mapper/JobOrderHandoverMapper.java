package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.JobOrderHandover;
import de.greluc.krt.iri.basetool.backend.model.JobOrderHandoverItem;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderHandoverDto;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderHandoverItemDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** MapStruct mapper between Job Order Handover entities and DTOs. */
@Mapper(
    componentModel = "spring",
    uses = {MaterialMapper.class})
public interface JobOrderHandoverMapper {

  /** Maps a {@link JobOrderHandover} entity to its DTO, flattening the parent job-order id. */
  @Mapping(source = "jobOrder.id", target = "jobOrderId")
  JobOrderHandoverDto toDto(JobOrderHandover jobOrderHandover);

  /** Maps a {@link JobOrderHandoverItem} entity to its DTO, flattening the parent handover id. */
  @Mapping(source = "jobOrderHandover.id", target = "jobOrderHandoverId")
  JobOrderHandoverItemDto toDto(JobOrderHandoverItem jobOrderHandoverItem);
}
