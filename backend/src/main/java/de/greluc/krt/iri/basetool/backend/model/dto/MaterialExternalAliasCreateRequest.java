package de.greluc.krt.iri.basetool.backend.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Create payload for {@code POST /api/v1/material-external-aliases}.
 *
 * <p>Server-managed fields ({@code id}, {@code version}, {@code createdBy}, timestamps) are
 * intentionally absent from this record so a client cannot pre-set them via the JSON body
 * (mass-assignment defence). The service stamps {@code createdBy} from the authenticated JWT.
 *
 * @param materialId UUID of the local material to link to (required)
 * @param sourceSystem catalogue identifier, must be {@code "UEX"} or {@code "SCWIKI"}
 * @param externalName the external commodity name (required, max 255 chars)
 * @param externalKey optional external internal key (max 255 chars)
 * @param externalUuid optional external UUID
 * @param externalCode optional external short code (max 64 chars)
 * @param note optional provenance note (free-form text)
 */
public record MaterialExternalAliasCreateRequest(
    @NotNull UUID materialId,
    @NotBlank @Pattern(regexp = "UEX|SCWIKI") String sourceSystem,
    @NotBlank @Size(max = 255) String externalName,
    @Size(max = 255) String externalKey,
    UUID externalUuid,
    @Size(max = 64) String externalCode,
    String note) {}
