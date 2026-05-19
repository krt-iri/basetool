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
    uses = {MaterialMapper.class, UserMapper.class, SquadronMapper.class})
public interface JobOrderHandoverMapper {

  /**
   * Maps a {@link JobOrderHandover} entity to its DTO, flattening the parent job-order id and
   * projecting the executing-user + squadron audit snapshot through their reference mappers (slim
   * payload — the handover detail view never needs the full {@link
   * de.greluc.krt.iri.basetool.backend.model.User} aggregate).
   */
  @Mapping(source = "jobOrder.id", target = "jobOrderId")
  @Mapping(source = "executingUser", target = "executingUser")
  @Mapping(source = "executingSquadron", target = "executingSquadron")
  JobOrderHandoverDto toDto(JobOrderHandover jobOrderHandover);

  /** Maps a {@link JobOrderHandoverItem} entity to its DTO, flattening the parent handover id. */
  @Mapping(source = "jobOrderHandover.id", target = "jobOrderHandoverId")
  JobOrderHandoverItemDto toDto(JobOrderHandoverItem jobOrderHandoverItem);
}
