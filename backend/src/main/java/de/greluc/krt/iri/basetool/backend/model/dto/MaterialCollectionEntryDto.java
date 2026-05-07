package de.greluc.krt.iri.basetool.backend.model.dto;

import java.util.UUID;

/**
 * DTO representing a single inventory entry in the material collection overview
 * for a specific job order. Used by the material collection endpoint.
 */
public record MaterialCollectionEntryDto(
        UUID inventoryEntryId,
        long version,
        String ownerName,
        UUID ownerId,
        String location,
        UUID locationId,
        String materialName,
        Double quality,
        Double quantity,
        boolean delivered
) {}
