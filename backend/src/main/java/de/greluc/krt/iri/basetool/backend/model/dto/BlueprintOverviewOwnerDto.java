package de.greluc.krt.iri.basetool.backend.model.dto;

/**
 * One owner row of the blueprint availability drill-down (#364): the display name of an in-scope
 * member who owns the selected blueprint. Deliberately carries the display name only — never the
 * Keycloak {@code sub} or e-mail — so the overview cannot leak account identifiers.
 *
 * @param ownerName the member's effective display name (display name, or username fallback)
 */
public record BlueprintOverviewOwnerDto(String ownerName) {}
