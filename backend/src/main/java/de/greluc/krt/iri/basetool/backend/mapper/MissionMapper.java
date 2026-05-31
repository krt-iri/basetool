package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.FrequencyType;
import de.greluc.krt.iri.basetool.backend.model.JobType;
import de.greluc.krt.iri.basetool.backend.model.Mission;
import de.greluc.krt.iri.basetool.backend.model.MissionCrew;
import de.greluc.krt.iri.basetool.backend.model.MissionFinanceEntry;
import de.greluc.krt.iri.basetool.backend.model.MissionFrequency;
import de.greluc.krt.iri.basetool.backend.model.MissionParticipant;
import de.greluc.krt.iri.basetool.backend.model.MissionUnit;
import de.greluc.krt.iri.basetool.backend.model.dto.FrequencyTypeDto;
import de.greluc.krt.iri.basetool.backend.model.dto.JobTypeDto;
import de.greluc.krt.iri.basetool.backend.model.dto.MissionCrewDto;
import de.greluc.krt.iri.basetool.backend.model.dto.MissionDto;
import de.greluc.krt.iri.basetool.backend.model.dto.MissionFinanceEntryDto;
import de.greluc.krt.iri.basetool.backend.model.dto.MissionFrequencyDto;
import de.greluc.krt.iri.basetool.backend.model.dto.MissionListDto;
import de.greluc.krt.iri.basetool.backend.model.dto.MissionParticipantDto;
import de.greluc.krt.iri.basetool.backend.model.dto.MissionReferenceDto;
import de.greluc.krt.iri.basetool.backend.model.dto.MissionUnitDto;
import de.greluc.krt.iri.basetool.backend.service.AuthHelperService;
import de.greluc.krt.iri.basetool.backend.service.MissionSecurityService;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

/** MapStruct mapper between Mission entities and DTOs. */
@Mapper(
    componentModel = "spring",
    uses = {
      ShipMapper.class,
      UserMapper.class,
      InventoryItemMapper.class,
      RefineryOrderMapper.class,
      OperationMapper.class,
      SquadronMapper.class
    },
    unmappedTargetPolicy = org.mapstruct.ReportingPolicy.IGNORE)
public abstract class MissionMapper {

  // MapStruct generates a concrete subclass via the annotation processor; the
  // generated subclass cannot accept additional constructor parameters, so we
  // fall back to field-level @Autowired here. The architectural concern of
  // touching SecurityContextHolder directly is solved by routing through
  // AuthHelperService — the mapper itself no longer depends on Spring's
  // security-context classes.
  @Autowired protected MissionSecurityService missionSecurityService;

  @Autowired protected AuthHelperService authHelperService;

  /**
   * Full {@link Mission} -&gt; DTO mapping. The five {@code resolve*} expressions are applied on
   * top of the default field copy so the DTO carries the caller-aware projections ({@code canEdit},
   * {@code canManageManagers}, description redaction for guests, participant counts).
   *
   * <p>After R9 Step 2 the mission entity exposes {@code owningOrgUnit} (typed {@code OrgUnit});
   * the DTO still publishes {@code owningSquadron} as {@code SquadronReferenceDto} for API
   * stability. The explicit mapping routes the source through {@code
   * SquadronMapper.orgUnitToReferenceDto}, which projects either kind — a Staffel or a
   * Spezialkommando — into the slim owner reference (id/name/shorthand), so SK-owned missions now
   * surface their SK badge instead of a blank cell.
   *
   * @param mission the mission entity to project; {@code null} returns {@code null}.
   * @return the populated mission DTO.
   */
  @Mapping(target = "description", expression = "java(resolveDescription(mission))")
  @Mapping(target = "canEdit", expression = "java(resolveCanEdit(mission))")
  @Mapping(target = "canManageManagers", expression = "java(resolveCanManageManagers(mission))")
  @Mapping(
      target = "checkedInParticipants",
      expression = "java(resolveCheckedInParticipants(mission))")
  @Mapping(
      target = "registeredParticipants",
      expression = "java(resolveRegisteredParticipants(mission))")
  @Mapping(target = "owningSquadron", source = "owningOrgUnit")
  public abstract MissionDto toDto(Mission mission);

  /** Maps a {@link MissionParticipant} entity to its outbound DTO. */
  public abstract MissionParticipantDto toDto(MissionParticipant participant);

  /** Maps a {@link MissionUnit} to its DTO with a deterministic leader-first crew ordering. */
  @Mapping(target = "crew", expression = "java(resolveCrew(unit))")
  public abstract MissionUnitDto toDto(MissionUnit unit);

  /** Maps a {@link MissionCrew} entity to its DTO, flattening the participant id / display name. */
  @Mapping(target = "participantId", source = "participant.id")
  @Mapping(target = "participantName", expression = "java(resolveParticipantName(crew))")
  public abstract MissionCrewDto toDto(MissionCrew crew);

  /** Maps a {@link MissionFinanceEntry} to its DTO, flattening the parent mission id. */
  @Mapping(target = "missionId", source = "mission.id")
  public abstract MissionFinanceEntryDto toDto(MissionFinanceEntry entry);

  /** Maps a {@link FrequencyType} entity nested inside a mission to its outbound DTO. */
  public abstract FrequencyTypeDto toDto(FrequencyType frequencyType);

  /** Maps a {@link MissionFrequency} entity to its outbound DTO. */
  public abstract MissionFrequencyDto toDto(MissionFrequency missionFrequency);

  /** Maps a {@link JobType} entity nested inside a mission to its outbound DTO. */
  @Mapping(target = "parentId", source = "parent.id")
  @Mapping(target = "isLeadershipRole", source = "leadershipRole")
  public abstract JobTypeDto toDto(JobType jobType);

  /** Narrow reference DTO (id + name) used wherever the full mission payload is overkill. */
  public abstract MissionReferenceDto toReferenceDto(Mission mission);

  /**
   * Slim list-row DTO of a mission; same description redaction as the full DTO. Also routes the
   * mission's {@code owningOrgUnit} through {@code SquadronMapper.orgUnitToReferenceDto} for the
   * {@code owningSquadron} DTO slot so the column on the missions list renders without an extra
   * round-trip, projecting either a Staffel or a Spezialkommando owner into the slim reference.
   *
   * @param mission the mission entity to project; {@code null} returns {@code null}.
   * @return the slim list-row DTO.
   */
  @Mapping(target = "description", expression = "java(resolveDescription(mission))")
  @Mapping(target = "owningSquadron", source = "owningOrgUnit")
  public abstract MissionListDto toListDto(Mission mission);

  // toEntity(MissionDto) has been removed (audit finding C-3, 2026-05-20): the previous mapper
  // copied id / version / owningSquadron / parent / isInternal straight from the response DTO
  // into a fresh Mission entity, which made `missionRepository.save(entity)` invoke
  // EntityManager.merge() and overwrite an attacker-supplied existing row. Write paths now go
  // through dedicated CreateMissionRequest / UpdateMissionRequest records that physically lack
  // those fields. The ArchUnit rule {@code missionDtoMustNotBeAcceptedAsRequestBody} keeps this
  // direction one-way.

  /**
   * Returns the mission description only to authenticated callers; guests get {@code null} so the
   * description is never exposed via the public detail endpoint.
   */
  public String resolveDescription(Mission mission) {
    if (mission == null || mission.getDescription() == null) {
      return null;
    }
    if (authHelperService.isAuthenticated()) {
      return mission.getDescription();
    }
    return null;
  }

  /**
   * Returns {@code true} iff the current caller may edit this mission (see {@link
   * MissionSecurityService}).
   */
  public boolean resolveCanEdit(Mission mission) {
    if (mission == null) {
      return false;
    }
    return missionSecurityService.canManageMission(
        mission.getId(), authHelperService.rawAuthentication());
  }

  /** Returns {@code true} iff the current caller may add/remove mission managers. */
  public boolean resolveCanManageManagers(Mission mission) {
    if (mission == null) {
      return false;
    }
    return missionSecurityService.canManageManagers(
        mission.getId(), authHelperService.rawAuthentication());
  }

  /**
   * Counts participants that have been checked in. A participant is considered checked in as soon
   * as {@code startTime} is set (see {@link
   * de.greluc.krt.iri.basetool.backend.service.MissionService#checkIn}).
   */
  public int resolveCheckedInParticipants(Mission mission) {
    if (mission == null || mission.getParticipants() == null) {
      return 0;
    }
    return (int)
        mission.getParticipants().stream()
            .filter(p -> p != null && p.getStartTime() != null)
            .count();
  }

  /** Counts all registered/enrolled participants of the mission, regardless of check-in state. */
  public int resolveRegisteredParticipants(Mission mission) {
    if (mission == null || mission.getParticipants() == null) {
      return 0;
    }
    return mission.getParticipants().size();
  }

  /**
   * Sorts crew members of a mission unit so that leadership roles appear first. A crew member is
   * considered a leader if at least one of its assigned JobTypes is flagged as leadership role
   * (independent of archetype so CREW and MISSION leadership JobTypes both qualify; MISSION
   * semantics remain unchanged). Secondary sort is stable by participant display name to keep the
   * previous alphabetical ordering for non-leaders.
   */
  public java.util.List<MissionCrewDto> resolveCrew(MissionUnit unit) {
    if (unit == null || unit.getCrew() == null) {
      return java.util.List.of();
    }
    java.util.Comparator<MissionCrew> leaderFirst =
        java.util.Comparator.comparing((MissionCrew c) -> isLeaderCrew(c) ? 0 : 1)
            .thenComparing(
                c -> {
                  String n = resolveParticipantName(c);
                  return n == null ? "" : n.toLowerCase(java.util.Locale.ROOT);
                });
    return unit.getCrew().stream().sorted(leaderFirst).map(this::toDto).toList();
  }

  private boolean isLeaderCrew(MissionCrew crew) {
    if (crew == null || crew.getJobTypes() == null) {
      return false;
    }
    for (JobType jt : crew.getJobTypes()) {
      if (jt != null && jt.isLeadershipRole()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Resolves a participant's display name: the linked user's effective name if known, otherwise the
   * guest name captured at sign-up.
   */
  public String resolveParticipantName(MissionCrew crew) {
    if (crew.getParticipant() == null) {
      return null;
    }
    if (crew.getParticipant().getUser() != null) {
      return crew.getParticipant().getUser().getEffectiveName();
    }
    return crew.getParticipant().getGuestName();
  }
}
