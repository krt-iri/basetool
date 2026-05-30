package de.greluc.krt.iri.basetool.frontend.model.dto;

/** Frontend mirror of the backend blueprint summary-property DTO (an aggregated affected stat). */
public record BlueprintSummaryPropertyDto(String propertyKey, String label, String betterWhen) {}
