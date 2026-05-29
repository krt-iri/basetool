package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.model.OrgUnitKind;
import java.time.Instant;
import java.util.UUID;

/**
 * Wire shape for {@link de.greluc.krt.iri.basetool.backend.model.OrgUnitMembership}. Carries the
 * embedded composite key unpacked as two separate UUID fields ({@link #userId}, {@link #orgUnitId})
 * plus a denormalised {@link #userDisplayName} (display name with username fallback) so the admin
 * roster page can render the member chip without a per-row join back to {@code app_user}. The
 * {@code kind} field mirrors {@link OrgUnitKind} so the client can branch on Squadron vs
 * Spezialkommando without a follow-up lookup against the parent {@code org_unit} row.
 *
 * <p>All flags are surfaced as {@code Boolean} (boxed) on the read side; that keeps the inbound
 * patch shape ({@link MembershipFlagsPatchRequest}) symmetric (an omitted field on a PATCH means
 * "no change", which only works with boxed values).
 *
 * @param userId Identifier of the user this membership belongs to. Always populated.
 * @param userDisplayName Convenience copy of {@code user.effectiveName} — the user's display name
 *     when set, otherwise the username — so the admin roster does not need a per-row join and never
 *     shows an empty cell for users without a configured display name.
 * @param orgUnitId Identifier of the org unit (Squadron or Spezialkommando) the user belongs to.
 *     Always populated.
 * @param kind Discriminator of the referenced org unit; {@link OrgUnitKind#SPECIAL_COMMAND} for SK
 *     memberships, {@link OrgUnitKind#SQUADRON} for the user's home Staffel. Denormalised on the
 *     wire so the client does not need to know about the JPA inheritance tree.
 * @param isLogistician Whether the membership grants the Logistician role within the referenced org
 *     unit. Once R5.c migrates the scoped-role authorisation onto the membership row, this is the
 *     authoritative source for "may this user perform a Logistician action in this org unit?".
 * @param isMissionManager Same as {@link #isLogistician} for the Mission Manager role.
 * @param isLead Whether the membership grants the Spezialkommando Lead capability. Permanently
 *     {@code false} on Squadron memberships by the V95 CHECK constraint {@code
 *     chk_org_unit_membership_lead_only_on_special_command}.
 * @param joinedAt Timestamp when the membership was granted. Surfaced as {@link Instant} (UTC) per
 *     the project's "all times in UTC" rule.
 * @param version Optimistic-lock counter; required on patch requests so concurrent admin edits do
 *     not silently lose a flag flip.
 */
public record OrgUnitMembershipDto(
    UUID userId,
    String userDisplayName,
    UUID orgUnitId,
    OrgUnitKind kind,
    Boolean isLogistician,
    Boolean isMissionManager,
    Boolean isLead,
    Instant joinedAt,
    Long version) {}
