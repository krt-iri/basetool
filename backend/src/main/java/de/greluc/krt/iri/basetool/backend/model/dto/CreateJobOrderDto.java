package de.greluc.krt.iri.basetool.backend.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Data transfer record carrying Create / Update Job Order payload.
 *
 * <p>Two org-unit references:
 *
 * <ul>
 *   <li>{@code responsibleOrgUnitId} — the org unit that <em>processes</em> the order. Must be a
 *       profit-eligible squadron or Spezialkommando (the service returns 400 otherwise). Required
 *       for authenticated callers; <b>ignored</b> for anonymous/guest creations, which are routed
 *       onto the configured intake Spezialkommando ({@code job_order.intake_special_command_id}).
 *       Ignored on update — the responsible org unit is only changed through the dedicated
 *       reassignment endpoint ({@code PATCH /api/v1/orders/{id}/responsible-org-unit}).
 *   <li>{@code requestingOrgUnitId} — the org unit the order is placed on behalf of (the customer).
 *       Any squadron or Spezialkommando, no profit-eligibility restriction. Mandatory; the service
 *       returns 400 when it does not resolve.
 *   <li>{@code comment} — optional free-text note (≤1000 chars), HTML-escaped on display.
 * </ul>
 */
public record CreateJobOrderDto(
    @Nullable UUID responsibleOrgUnitId,
    @Nullable UUID requestingOrgUnitId,
    @Size(max = 200) String handle,
    @Size(max = 1000) String comment,
    @NotEmpty @Size(max = 50) @Valid List<CreateJobOrderMaterialDto> materials,
    Long version) {}
