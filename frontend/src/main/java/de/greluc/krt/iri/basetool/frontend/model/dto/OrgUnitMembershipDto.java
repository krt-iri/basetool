package de.greluc.krt.iri.basetool.frontend.model.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Frontend mirror of the backend {@code OrgUnitMembershipDto}. Carries the composite key
 * unpacked into flat {@code userId} / {@code orgUnitId} fields plus the denormalised
 * {@code userDisplayName} so the admin SK detail page can render the member chip without a
 * per-row join back to {@code app_user}.
 *
 * @param userId user identifier; never {@code null} on responses.
 * @param userDisplayName denormalised copy of the user's display name; never {@code null} on
 *     responses (defaults to the username when the user has no display name set).
 * @param orgUnitId org-unit identifier the user belongs to.
 * @param kind discriminator of the referenced org unit; {@link OrgUnitKind#SPECIAL_COMMAND} for
 *     SK memberships, {@link OrgUnitKind#SQUADRON} for Staffel memberships.
 * @param isLogistician whether the membership grants the Logistician role within the referenced
 *     org unit.
 * @param isMissionManager whether the membership grants the Mission Manager role within the
 *     referenced org unit.
 * @param isLead whether the membership grants the Spezialkommando Lead capability (always
 *     {@code false} on Staffel memberships by the V95 CHECK constraint).
 * @param joinedAt timestamp when the membership was granted.
 * @param version optimistic-lock counter; required on PATCH requests.
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
