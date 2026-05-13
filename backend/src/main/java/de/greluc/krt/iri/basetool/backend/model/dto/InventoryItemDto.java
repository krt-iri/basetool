package de.greluc.krt.iri.basetool.backend.model.dto;

import java.util.UUID;

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
    Long version) {}
