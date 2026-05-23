package de.greluc.krt.iri.basetool.backend.model.dto;

import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Response payload for the SPEZIALKOMMANDO_PLAN.md §7.4 single-POST membership-delta endpoint.
 * Carries the user's post-write membership state so the frontend can re-render the form without a
 * follow-up GET — saves one round-trip and avoids the "what's the new version?" question after each
 * save.
 *
 * @param memberships the user's complete current Staffel + SK membership list, sorted Staffel-
 *     first then SK alphabetical. Never {@code null}; possibly empty when the user has been
 *     stripped of every membership in the same transaction.
 */
public record MembershipDeltaResponse(@NotNull List<OrgUnitMembershipDto> memberships) {}
