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
 * <p>Two squadron references following MULTI_SQUADRON_PLAN.md section 4.5:
 *
 * <ul>
 *   <li>{@code creatingSquadronId} (optional, immutable after first persist) — the squadron that
 *       authored the order in the system. {@code null} on create means "use the caller's active
 *       squadron context"; admins in "all squadrons" mode must set it explicitly (the service layer
 *       returns 400 otherwise). Ignored on update.
 *   <li>{@code requestingSquadronId} — the squadron the order is being executed for. Any
 *       Logistician+ may set it on create or change it later; not access-controlling. When {@code
 *       null} the service falls back to {@code creatingSquadronId} (i.e. the order executes for its
 *       own author squadron), keeping the same minimal-payload contract that the legacy
 *       VARCHAR-fallback path supplied before Part 3.
 * </ul>
 *
 * <p>The legacy free-text {@code squadron} field (kept on the wire for backwards compatibility
 * since Phase 7 part 1 / V88 stopped writing the corresponding DB column) has been removed on this
 * release together with the V90 DROP COLUMN migration. Clients must use the typed {@code
 * requestingSquadronId} UUID; a string shorthand is no longer accepted.
 */
public record CreateJobOrderDto(
    @Nullable UUID creatingSquadronId,
    @Nullable UUID requestingSquadronId,
    @Size(max = 200) String handle,
    @NotEmpty @Size(max = 50) @Valid List<CreateJobOrderMaterialDto> materials,
    Long version) {}
