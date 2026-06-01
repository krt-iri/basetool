package de.greluc.krt.iri.basetool.backend.model.dto;

/**
 * One fuzzy-match candidate offered for an unmatched SCMDB blueprint name (#327, Phase 4). The
 * frontend lists these (highest {@link #score} first) so the user can confirm the intended product
 * with one click instead of searching the full master list.
 *
 * @param productKey normalized product key of the candidate (echoed back on apply)
 * @param productName display spelling of the candidate product
 * @param score similarity to the external name in {@code [0.0, 1.0]} (1.0 = identical normalized)
 */
public record BlueprintImportSuggestionDto(String productKey, String productName, double score) {}
