package de.greluc.krt.iri.basetool.backend.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Write payload for adding a single blueprint to the caller's owned set (#327). The product is
 * referenced by its normalized {@code productKey}; the server resolves the display name and output
 * item, so neither is accepted from the client.
 *
 * @param productKey normalized product key of the blueprint to add
 * @param acquiredAt optional in-game acquisition time
 * @param note optional free-form note (max 2000 chars)
 */
public record PersonalBlueprintCreateRequest(
    @NotBlank String productKey, Instant acquiredAt, @Size(max = 2000) String note) {}
