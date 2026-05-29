package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Frontend mirror of the backend {@code MaterialExternalAliasDto}. Lives in the frontend module so
 * {@code AdminMaterialAliasesPageController} can deserialise the REST response without pulling in
 * the backend module (the two modules are isolated by Gradle).
 *
 * <p>Fields and types must stay in lockstep with {@code
 * de.greluc.krt.iri.basetool.backend.model.dto.MaterialExternalAliasDto} — any change on the
 * backend side requires a matching change here in the same commit (mirror-DTO rule).
 *
 * @param id alias UUID
 * @param version optimistic-lock token
 * @param materialId linked material UUID (FK)
 * @param materialName linked material name (denormalised for table rendering)
 * @param sourceSystem catalogue identifier ({@code "UEX"} or {@code "SCWIKI"})
 * @param externalName commodity name in the external catalogue
 * @param externalKey optional external internal key
 * @param externalUuid optional external UUID
 * @param externalCode optional external short code
 * @param note free-form provenance note
 * @param createdBy {@code "system"} for V108 seeds, JWT {@code sub} otherwise
 * @param createdAt row creation timestamp
 * @param updatedAt row last-update timestamp
 */
public record MaterialExternalAliasDto(
    UUID id,
    Long version,
    UUID materialId,
    String materialName,
    String sourceSystem,
    String externalName,
    String externalKey,
    UUID externalUuid,
    String externalCode,
    String note,
    String createdBy,
    Instant createdAt,
    Instant updatedAt) {}
