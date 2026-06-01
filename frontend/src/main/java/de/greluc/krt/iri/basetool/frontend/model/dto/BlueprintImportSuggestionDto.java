package de.greluc.krt.iri.basetool.frontend.model.dto;

/**
 * One fuzzy-match candidate mirroring the backend {@code BlueprintImportSuggestionDto} (#327),
 * offered for an unmatched SCMDB blueprint name in the import preview.
 *
 * @param productKey normalized product key of the candidate (echoed back on apply)
 * @param productName display spelling of the candidate product
 * @param score similarity to the external name in {@code [0.0, 1.0]}
 */
public record BlueprintImportSuggestionDto(String productKey, String productName, double score) {}
