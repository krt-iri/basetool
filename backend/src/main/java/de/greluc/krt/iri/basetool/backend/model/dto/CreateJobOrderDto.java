package de.greluc.krt.iri.basetool.backend.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
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
 *   <li>{@code requestingSquadronId} (required) — the squadron the order is being executed for. Any
 *       Logistician+ may set it on create or change it later; not access-controlling.
 * </ul>
 *
 * <p>The legacy {@code squadron} free-text field is preserved on the wire for backwards
 * compatibility — clients that have not migrated to the UUID-typed fields can still post a
 * shorthand string and the service layer resolves it against {@code squadron.shorthand}. The field
 * will be removed when migration V88 drops the legacy column.
 */
public record CreateJobOrderDto(
    @Nullable String squadron,
    @Nullable UUID creatingSquadronId,
    @Nullable UUID requestingSquadronId,
    String handle,
    @NotEmpty @Valid List<CreateJobOrderMaterialDto> materials,
    Long version) {}
