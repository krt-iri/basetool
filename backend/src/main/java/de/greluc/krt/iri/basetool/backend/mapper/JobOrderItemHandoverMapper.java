package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.GameItem;
import de.greluc.krt.iri.basetool.backend.model.JobOrderItemHandover;
import de.greluc.krt.iri.basetool.backend.model.JobOrderItemHandoverEntry;
import de.greluc.krt.iri.basetool.backend.model.dto.GameItemReferenceDto;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderItemHandoverDto;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderItemHandoverEntryDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** MapStruct mapper between item-handover entities and DTOs. */
@Mapper(
    componentModel = "spring",
    uses = {UserMapper.class, SquadronMapper.class})
public interface JobOrderItemHandoverMapper {

  /**
   * Maps a {@link JobOrderItemHandover} to its DTO, flattening the parent job-order id and
   * projecting the executing-user + squadron audit snapshot through their reference mappers.
   *
   * @param handover the entity to project
   * @return the populated DTO
   */
  @Mapping(source = "jobOrder.id", target = "jobOrderId")
  @Mapping(source = "executingUser", target = "executingUser")
  @Mapping(source = "executingSquadron", target = "executingSquadron")
  JobOrderItemHandoverDto toDto(JobOrderItemHandover handover);

  /**
   * Maps a delivered {@link JobOrderItemHandoverEntry} to its DTO, flattening the ordered-line id
   * and projecting the produced item to a slim reference.
   *
   * @param entry the entity to project
   * @return the populated DTO
   */
  @Mapping(source = "jobOrderItem.id", target = "jobOrderItemId")
  @Mapping(source = "jobOrderItem.gameItem", target = "gameItem")
  JobOrderItemHandoverEntryDto toDto(JobOrderItemHandoverEntry entry);

  /**
   * Projects a {@link GameItem} to the slim reference used in handover entries.
   *
   * @param gameItem the catalogue entity, or {@code null}
   * @return the slim reference, or {@code null} when the input is {@code null}
   */
  default GameItemReferenceDto toGameItemReference(GameItem gameItem) {
    if (gameItem == null) {
      return null;
    }
    return new GameItemReferenceDto(
        gameItem.getId(),
        gameItem.getName(),
        gameItem.getKind() == null ? null : gameItem.getKind().name());
  }
}
