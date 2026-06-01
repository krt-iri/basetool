package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.List;

/**
 * Outbound write DTO mirroring the backend {@code PersonalBlueprintBatchCreateRequest} (#327): the
 * normalized product keys staged by the multi-select add. Validation is authoritative on the
 * backend; this record is just the wire shape.
 *
 * @param productKeys the normalized product keys to add in one call
 */
public record PersonalBlueprintBatchCreateRequest(List<String> productKeys) {}
