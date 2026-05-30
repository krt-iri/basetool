package de.greluc.krt.iri.basetool.frontend.model.dto;

/**
 * Frontend mirror of the backend blueprint modifier-segment DTO (one linear step of a stepped /
 * piecewise-linear stat curve, used by the admin blueprint slider to compute non-linear values).
 */
public record BlueprintRequirementModifierSegmentDto(
    Double qualityMin, Double qualityMax, Double modifierAtStart, Double modifierAtEnd) {}
