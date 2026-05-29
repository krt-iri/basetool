package de.greluc.krt.iri.basetool.backend.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Update payload for {@code PUT /api/v1/material-external-aliases/{id}}.
 *
 * <p>Same shape as {@link MaterialExternalAliasCreateRequest} but additionally carries the
 * optimistic-lock {@code version} that the service compares against the row's current version
 * before accepting the change. A mismatched version surfaces as {@link
 * org.springframework.orm.ObjectOptimisticLockingFailureException} → HTTP 409.
 *
 * @param materialId UUID of the local material to link to (required)
 * @param sourceSystem catalogue identifier, must be {@code "UEX"} or {@code "SCWIKI"}
 * @param externalName the external commodity name (required, max 255 chars)
 * @param externalKey optional external internal key (max 255 chars)
 * @param externalUuid optional external UUID
 * @param externalCode optional external short code (max 64 chars)
 * @param note optional provenance note (free-form text)
 * @param version optimistic-lock token from the GET response
 */
public record MaterialExternalAliasUpdateRequest(
    @NotNull UUID materialId,
    @NotBlank @Pattern(regexp = "UEX|SCWIKI") String sourceSystem,
    @NotBlank @Size(max = 255) String externalName,
    @Size(max = 255) String externalKey,
    UUID externalUuid,
    @Size(max = 64) String externalCode,
    String note,
    @NotNull Long version) {}
