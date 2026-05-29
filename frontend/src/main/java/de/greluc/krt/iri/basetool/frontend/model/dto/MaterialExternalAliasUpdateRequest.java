package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.UUID;

/**
 * Frontend mirror of the backend {@code MaterialExternalAliasUpdateRequest}. Identical to {@link
 * MaterialExternalAliasCreateRequest} plus the {@code version} optimistic-lock token that the form
 * re-submits on every edit.
 *
 * @param materialId UUID of the local material to link to
 * @param sourceSystem catalogue identifier ({@code "UEX"} or {@code "SCWIKI"})
 * @param externalName commodity name in the external catalogue
 * @param externalKey optional external internal key
 * @param externalUuid optional external UUID
 * @param externalCode optional external short code
 * @param note free-form provenance note
 * @param version optimistic-lock token from the GET response
 */
public record MaterialExternalAliasUpdateRequest(
    UUID materialId,
    String sourceSystem,
    String externalName,
    String externalKey,
    UUID externalUuid,
    String externalCode,
    String note,
    Long version) {}
