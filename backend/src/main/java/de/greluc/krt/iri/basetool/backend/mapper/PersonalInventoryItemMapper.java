package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.PersonalInventoryItem;
import de.greluc.krt.iri.basetool.backend.model.dto.PersonalInventoryItemCreateRequest;
import de.greluc.krt.iri.basetool.backend.model.dto.PersonalInventoryItemResponse;
import de.greluc.krt.iri.basetool.backend.model.dto.PersonalInventoryItemUpdateRequest;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * Entity ↔ DTO mapper for {@link PersonalInventoryItem}.
 *
 * <p>The {@code ownerSub} is intentionally never copied to/from the response DTO: it is
 * an internal isolation key (see AGENTS.md "MULTI-USER DATA ISOLATION") and must not leak
 * to clients.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PersonalInventoryItemMapper {

    @Mapping(target = "locationName", source = "locationNameSnapshot")
    PersonalInventoryItemResponse toResponse(PersonalInventoryItem entity);

    // Note: inherited fields from AbstractEntity (id, version, createdAt, updatedAt) are
    // not part of the Lombok @Builder generated for this class and are therefore covered
    // by the global unmappedTargetPolicy = IGNORE rather than by explicit @Mapping(ignore).
    @Mapping(target = "ownerSub", ignore = true)
    @Mapping(target = "locationNameSnapshot", ignore = true)
    PersonalInventoryItem toEntity(PersonalInventoryItemCreateRequest request);

    /**
     * Applies an update request to an already-managed entity. {@code id}, {@code ownerSub},
     * {@code version}, {@code createdAt} and {@code locationNameSnapshot} are excluded:
     * version is owned by JPA optimistic locking; the location name snapshot must be set
     * by the service after a successful UEX lookup.
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "ownerSub", ignore = true)
    @Mapping(target = "locationNameSnapshot", ignore = true)
    void updateEntity(@MappingTarget PersonalInventoryItem entity, PersonalInventoryItemUpdateRequest request);
}
