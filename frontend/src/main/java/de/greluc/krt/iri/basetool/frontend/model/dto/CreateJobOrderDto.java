package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Frontend mirror of the backend's {@code CreateJobOrderDto}. Mirrors the dual-squadron model
 * (MULTI_SQUADRON_PLAN.md section 4.5):
 *
 * <ul>
 *   <li>{@code creatingSquadronId} — optional admin-override (the backend rejects non-admin
 *       senders); {@code null} means "stamp from the caller's active squadron context".
 *   <li>{@code requestingSquadronId} — the squadron the order is executed for; any Logistician+ may
 *       set or change it.
 *   <li>{@code squadron} — legacy free-text fallback kept for clients that have not migrated yet.
 * </ul>
 */
public record CreateJobOrderDto(
    @Nullable String squadron,
    @Nullable UUID creatingSquadronId,
    @Nullable UUID requestingSquadronId,
    String handle,
    List<CreateJobOrderMaterialDto> materials,
    Long version) {}
