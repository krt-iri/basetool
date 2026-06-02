package de.greluc.krt.iri.basetool.frontend.model.dto;

/**
 * Frontend mirror of the backend {@code BlueprintOverviewOwnerDto} (#364): one owner of a blueprint
 * in the availability drill-down, carrying the display name only (never the sub or e-mail).
 *
 * @param ownerName the member's effective display name
 */
public record BlueprintOverviewOwnerDto(String ownerName) {}
