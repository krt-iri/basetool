package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.Operation;
import de.greluc.krt.iri.basetool.backend.model.dto.OperationCreateDto;
import de.greluc.krt.iri.basetool.backend.model.dto.OperationDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/** MapStruct mapper between Operation entities and DTOs. */
@Mapper(
    componentModel = "spring",
    uses = {SquadronMapper.class},
    unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OperationMapper {

  /**
   * Maps an {@link Operation} entity to its outbound DTO.
   *
   * <p>After R9 Step 2 the operation entity exposes {@code owningOrgUnit} (typed {@code OrgUnit});
   * the DTO still publishes {@code owningSquadron} as {@code SquadronReferenceDto} for API
   * stability. The explicit mapping routes the source through {@code
   * SquadronMapper.orgUnitToReferenceDto} so SK-owned operations surface as {@code null} on the
   * wire while Staffel-owned ones continue to project as before.
   *
   * @param entity the operation entity to project; {@code null} returns {@code null}.
   * @return the populated operation DTO.
   */
  @Mapping(target = "owningSquadron", source = "owningOrgUnit")
  OperationDto toDto(Operation entity);

  /**
   * Builds a new {@link Operation} entity from a create-DTO. Server-managed fields ({@code id},
   * timestamps) and the {@code missions} association are owned by the service layer and stripped
   * here.
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  @Mapping(target = "missions", ignore = true)
  Operation toEntity(OperationCreateDto dto);
}
