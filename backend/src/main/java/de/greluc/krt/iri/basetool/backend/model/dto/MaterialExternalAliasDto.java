package de.greluc.krt.iri.basetool.backend.model.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for {@link de.greluc.krt.iri.basetool.backend.model.MaterialExternalAlias}.
 *
 * <p>{@code materialName} is denormalised from the linked material so the admin table view does not
 * need a second round-trip per row. {@code version} is the optimistic-lock token: the admin UI
 * echoes it back on the update form so concurrent edits surface as a 409.
 *
 * @param id alias UUID
 * @param version optimistic-lock token
 * @param materialId linked material UUID (FK)
 * @param materialName linked material name, denormalised for table-view rendering
 * @param sourceSystem catalogue identifier ({@code "UEX"} or {@code "SCWIKI"})
 * @param externalName commodity name in the external catalogue
 * @param externalKey optional external internal key
 * @param externalUuid optional external UUID
 * @param externalCode optional external short code
 * @param note free-form provenance / verification note
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
