package de.greluc.krt.iri.basetool.frontend.model.dto;

/** Frontend mirror of the backend blueprint ingredient-line DTO. */
public record BlueprintRequirementIngredientDto(
    String kind, String name, Double quantityScu, Integer quantityUnits, Integer minQuality) {}
