package de.greluc.krt.iri.basetool.backend.model.dto;

/**
 * Boundary DTO for one aggregated stat a blueprint affects (the {@code blueprint_summary_property}
 * roll-up). Used to badge a blueprint row with the stats it influences without expanding every
 * slot.
 *
 * @param propertyKey internal stat key (e.g. {@code "weapon_damage"})
 * @param label human-readable stat name (e.g. {@code "Impact Force"})
 * @param betterWhen whether a higher / lower / neutral value is desirable
 */
public record BlueprintSummaryPropertyDto(String propertyKey, String label, String betterWhen) {}
