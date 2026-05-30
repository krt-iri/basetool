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
 *   <li>{@code creatingSquadronId} (optional, immutable after first persist) — the squadron that
 *       authored the order in the system. {@code null} on create means "use the caller's active
 *       squadron context"; admins in "all squadrons" mode must set it explicitly (the service layer
 *       returns 400 otherwise). Ignored on update. Plan §7.3 reserves a {@code creatingOrgUnitId}
 *       admin-override field that R5.d.c left intentionally out of scope — the rename + widening to
 *       SKs ship in a follow-up release once the destructive cleanup release lowers NOT NULL on the
 *       legacy column.
 *   <li>{@code requestingOrgUnitId} — the org unit the order is being executed for. Renamed from
 *       the historical {@code requestingSquadronId} in R5.d.c so the picker on the create form can
 *       offer Staffel + Spezialkommando alike. Today the picker is filtered to Staffel-only by the
 *       schema (the legacy {@code requesting_squadron_id} column is still NOT NULL); SK selections
 *       are refused by the service with a clean 400 until the cleanup release lifts the constraint.
 *       When the field is {@code null} the service falls back to {@code creatingSquadronId} (i.e.
 *       the order executes for its own author squadron), preserving the same minimal-payload
 *       contract the legacy field had.
 *   <li>{@code comment} — optional free-text note (≤1000 chars), HTML-escaped on display.
 * </ul>
 */
public record CreateJobOrderDto(
    @Nullable UUID creatingSquadronId,
    @Nullable UUID requestingOrgUnitId,
    @Size(max = 200) String handle,
    @Size(max = 1000) String comment,
    @NotEmpty @Size(max = 50) @Valid List<CreateJobOrderMaterialDto> materials,
    Long version) {}
