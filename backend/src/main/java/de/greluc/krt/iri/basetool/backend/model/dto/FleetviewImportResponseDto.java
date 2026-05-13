package de.greluc.krt.iri.basetool.backend.model.dto;

import java.util.List;

/**
 * Response DTO for the Fleetview JSON import endpoint.
 *
 * <p>Returns import statistics and lists of ships that could not be processed, so the user can take
 * corrective action (e.g. run a UEX sync or rename ships manually).
 */
public record FleetviewImportResponseDto(
    int importedCount,
    int skippedCount,
    int duplicateCount,
    List<String> skippedShips,
    List<String> duplicateShips) {}
