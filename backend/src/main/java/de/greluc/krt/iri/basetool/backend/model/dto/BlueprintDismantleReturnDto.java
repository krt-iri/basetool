package de.greluc.krt.iri.basetool.backend.model.dto;

/**
 * Boundary DTO for one commodity recovered when the crafted item is dismantled (the {@code
 * blueprint_dismantle_return} row). {@link #name} is the Wiki name snapshot.
 *
 * @param name display name of the returned commodity (Wiki snapshot)
 * @param quantityScu SCU amount recovered
 */
public record BlueprintDismantleReturnDto(String name, Double quantityScu) {}
