package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Frontend mirror of the backend {@code JobOrderDto}. Serves both order kinds: {@code MATERIAL}
 * orders populate {@code materials} + {@code handovers}; {@code ITEM} orders populate {@code
 * items}, {@code aggregatedMaterials} and {@code itemHandovers}. The unused lists are empty for the
 * respective kind, so the detail UI renders both through one shape.
 */
public record JobOrderDto(
    UUID id,
    Integer displayId,
    SquadronReferenceDto responsibleOrgUnit,
    SquadronReferenceDto requestingOrgUnit,
    String handle,
    String comment,
    Integer priority,
    String status,
    String type,
    List<JobOrderMaterialDto> materials,
    List<JobOrderItemDto> items,
    List<AggregatedMaterialDto> aggregatedMaterials,
    List<UserDto> assignees,
    List<JobOrderHandoverDto> handovers,
    List<JobOrderItemHandoverDto> itemHandovers,
    Instant createdAt,
    Long version) {}
