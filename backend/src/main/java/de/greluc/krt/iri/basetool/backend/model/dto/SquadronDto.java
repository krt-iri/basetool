package de.greluc.krt.iri.basetool.backend.model.dto;

import java.util.UUID;

/**
 * Data transfer record carrying Squadron payload.
 *
 * <p>{@code isPromotionEnabled} is the per-squadron feature flag for the entire promotion subsystem
 * (topics, categories, level-contents, rank-requirements, member-evaluations). Flag is read-only on
 * the regular update path; admins toggle it through a dedicated {@code
 * /api/v1/squadrons/{id}/promotion-enabled} endpoint so the change is auditable and cannot be made
 * by accident as a side-effect of editing the squadron name/shorthand. {@code null} on the request
 * side is therefore meaningless — the value is always supplied on responses, and only the dedicated
 * toggle endpoint actually mutates it.
 *
 * @param id squadron identifier; nullable on create-request payloads
 * @param name display name (case-insensitive unique)
 * @param shorthand short tag used on badges / column headers
 * @param description free-form text
 * @param active soft-delete flag; {@code true} for the active reference data
 * @param isPromotionEnabled per-squadron promotion-system feature flag (default {@code true})
 * @param version optimistic-lock counter
 */
public record SquadronDto(
    UUID id,
    String name,
    String shorthand,
    String description,
    Boolean active,
    Boolean isPromotionEnabled,
    Long version) {}
