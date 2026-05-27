package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.UUID;

/**
 * Frontend mirror of the backend {@code MaterialExternalAliasCreateRequest}. Used by {@code
 * AdminMaterialAliasesPageController} to forward the admin form submission to the backend REST
 * controller. Validation lives on the backend record; the frontend only relays.
 *
 * @param materialId UUID of the local material to link to
 * @param sourceSystem catalogue identifier ({@code "UEX"} or {@code "SCWIKI"})
 * @param externalName commodity name in the external catalogue
 * @param externalKey optional external internal key
 * @param externalUuid optional external UUID
 * @param externalCode optional external short code
 * @param note free-form provenance note
 */
public record MaterialExternalAliasCreateRequest(
    UUID materialId,
    String sourceSystem,
    String externalName,
    String externalKey,
    UUID externalUuid,
    String externalCode,
    String note) {}
