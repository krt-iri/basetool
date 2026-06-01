package de.greluc.krt.iri.basetool.backend.model.dto;

import de.greluc.krt.iri.basetool.backend.model.OrgUnitKind;
import java.util.UUID;

/**
 * Narrow reference projection of an {@code OrgUnit} (a {@code Squadron} or a {@code
 * SpecialCommand}) carrying only what the UI needs to render an org-unit badge: the surrogate id,
 * the long-form name for tooltips, the short handle for the chip text, and the {@link #kind}
 * discriminator so the client can style Staffel and Spezialkommando chips differently.
 *
 * <p>Embedded as a list into {@link MissionParticipantDto}: a single participant may be affiliated
 * with zero, one, or several org units (a member of a Staffel plus one or more Spezialkommandos),
 * so the participant snapshot exposes a {@code List<OrgUnitReferenceDto>} rather than the single
 * {@code SquadronDto} the pre-org-unit model used. Deliberately omits {@code active} / {@code
 * description} / {@code version}, which belong on the full admin DTOs.
 *
 * @param id surrogate id of the org unit.
 * @param name long-form display name (e.g. for tooltips / full-width cells).
 * @param shorthand abbreviated three- to five-letter handle rendered on the chip; may be {@code
 *     null} for legacy rows without a shorthand.
 * @param kind discriminator distinguishing a Staffel ({@link OrgUnitKind#SQUADRON}) from a
 *     Spezialkommando ({@link OrgUnitKind#SPECIAL_COMMAND}).
 */
public record OrgUnitReferenceDto(UUID id, String name, String shorthand, OrgUnitKind kind) {}
