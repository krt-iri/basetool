package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.UUID;

/**
 * Frontend mirror of the backend Squadron DTO. {@code isPromotionEnabled} is the per-squadron
 * feature flag for the promotion subsystem; the admin toggle lives at {@code
 * /api/proxy/squadrons/{id}/promotion-enabled} and the page-render flow reads the flag through
 * {@code SquadronContextAdvice} so the sidebar and {@link
 * de.greluc.krt.iri.basetool.frontend.controller.PromotionPageController} know whether to expose
 * the promotion menu for a non-admin caller.
 *
 * @param id squadron identifier
 * @param name display name
 * @param shorthand short tag
 * @param description free-form text
 * @param active soft-delete flag
 * @param isPromotionEnabled per-squadron promotion-feature flag
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
