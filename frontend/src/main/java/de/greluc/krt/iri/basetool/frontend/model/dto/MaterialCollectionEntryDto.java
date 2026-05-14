package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.UUID;

/** DTO for a single entry in the material collection overview of a job order. */
public record MaterialCollectionEntryDto(
    UUID inventoryEntryId,
    long version,
    String ownerName,
    UUID ownerId,
    String location,
    UUID locationId,
    String materialName,
    double quality,
    double quantity,
    boolean delivered) {}
