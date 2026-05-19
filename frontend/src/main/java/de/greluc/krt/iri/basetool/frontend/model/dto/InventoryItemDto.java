package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.UUID;

/** Data transfer record carrying Inventory Item payload. */
public record InventoryItemDto(
    UUID id,
    UserReferenceDto user,
    MaterialReferenceDto material,
    LocationReferenceDto location,
    Integer quality,
    Double amount,
    Boolean personal,
    UUID jobOrderId,
    Integer jobOrderDisplayId,
    UUID missionId,
    String missionName,
    String note,
    SquadronReferenceDto owningSquadron,
    Long version) {}
