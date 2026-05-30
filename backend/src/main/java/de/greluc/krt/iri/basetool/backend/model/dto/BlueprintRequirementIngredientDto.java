package de.greluc.krt.iri.basetool.backend.model.dto;

/**
 * Boundary DTO for one ingredient line of a blueprint. The {@link #name} is the Wiki name snapshot
 * persisted on the ingredient (always present, so rendering it never triggers a material /
 * game-item load). {@link #kind} is the {@code RESOURCE} / {@code ITEM} discriminator; the matching
 * quantity field is populated per kind.
 *
 * @param kind {@code "RESOURCE"} or {@code "ITEM"}
 * @param name display name of the ingredient (Wiki snapshot)
 * @param quantityScu SCU amount for a RESOURCE line, else {@code null}
 * @param quantityUnits whole-unit count for an ITEM line, else {@code null}
 * @param minQuality minimum quality tier required, or {@code null}
 */
public record BlueprintRequirementIngredientDto(
    String kind, String name, Double quantityScu, Integer quantityUnits, Integer minQuality) {}
