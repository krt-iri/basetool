package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.util.List;

/**
 * Frontend mirror of the backend SPEZIALKOMMANDO_PLAN.md §7.4 delta-endpoint response payload.
 * Carries the user's complete post-write membership list so the page-controller can re-render the
 * form without a follow-up GET.
 *
 * @param memberships post-write membership rows, sorted Staffel-first.
 */
public record MembershipDeltaResponse(List<OrgUnitMembershipDto> memberships) {}
