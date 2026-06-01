package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.PersonalBlueprint;
import de.greluc.krt.iri.basetool.backend.model.dto.PersonalBlueprintResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Entity → DTO mapper for {@link PersonalBlueprint}.
 *
 * <p>The {@code ownerSub} is never copied into the response — it is the internal isolation key and
 * must not leak to clients (see the multi-user data isolation rule). The optional output-item
 * association is flattened to its id; writes are applied field-by-field in the service (only {@code
 * acquiredAt} / {@code note} are mutable), so no entity-write mapping is needed here.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PersonalBlueprintMapper {

  /**
   * Maps an owned blueprint to its response DTO, flattening the optional {@code outputItem}
   * association to {@code outputItemId} ({@code null} when unresolved).
   *
   * @param entity the owned blueprint
   * @return the response DTO
   */
  @Mapping(target = "outputItemId", source = "outputItem.id")
  PersonalBlueprintResponse toResponse(PersonalBlueprint entity);
}
