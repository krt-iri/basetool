package de.greluc.krt.iri.basetool.backend.model.dto;

/**
 * Internal DTO for parsing a single entry from a CCU Game Fleetview JSON export.
 *
 * <p>The fleetview.json format is:
 * <pre>
 * [
 *   { "name": "135c", "shipname": "", "type": "ship" },
 *   ...
 * ]
 * </pre>
 */
public record FleetviewEntryDto(
        String name,
        String shipname,
        String type
) {
}
