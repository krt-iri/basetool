package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.UUID;

/**
 * Frontend mirror of the backend {@code BlueprintReferenceDto}: a slim reference to a blueprint
 * that produces an orderable item.
 *
 * @param id the blueprint id
 * @param outputName the produced item's name as recorded on the blueprint
 * @param scwikiKey the SC-Wiki key (stable disambiguator)
 */
public record BlueprintReferenceDto(UUID id, String outputName, String scwikiKey) {}
