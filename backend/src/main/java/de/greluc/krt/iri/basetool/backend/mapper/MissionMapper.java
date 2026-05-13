package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.*;
import de.greluc.krt.iri.basetool.backend.model.dto.*;
import de.greluc.krt.iri.basetool.backend.service.AuthHelperService;
import de.greluc.krt.iri.basetool.backend.service.MissionSecurityService;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(
    componentModel = "spring",
    uses = {
      ShipMapper.class,
      UserMapper.class,
      InventoryItemMapper.class,
      RefineryOrderMapper.class,
      OperationMapper.class
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

  @Mapping(target = "description", expression = "java(resolveDescription(mission))")
  @Mapping(target = "canEdit", expression = "java(resolveCanEdit(mission))")
  @Mapping(target = "canManageManagers", expression = "java(resolveCanManageManagers(mission))")
  @Mapping(
      target = "checkedInParticipants",
      expression = "java(resolveCheckedInParticipants(mission))")
  @Mapping(
      target = "registeredParticipants",
      expression = "java(resolveRegisteredParticipants(mission))")
  public abstract MissionDto toDto(Mission mission);

  public abstract MissionReferenceDto toReferenceDto(Mission mission);

  @Mapping(target = "description", expression = "java(resolveDescription(mission))")
  public abstract MissionListDto toListDto(Mission mission);

  @Mapping(target = "subMissions", ignore = true)
  @Mapping(target = "participants", ignore = true)
  @Mapping(target = "assignedUnits", ignore = true)
  @Mapping(target = "inventoryEntries", ignore = true)
  @Mapping(target = "refineryOrders", ignore = true)
  @Mapping(target = "frequencies", ignore = true)
  @Mapping(target = "owner", ignore = true)
  @Mapping(target = "managers", ignore = true)
  public abstract Mission toEntity(MissionDto dto);

  public String resolveDescription(Mission mission) {
    if (mission == null || mission.getDescription() == null) return null;
    if (authHelperService.isAuthenticated()) {
      return mission.getDescription();
    }
    return null;
  }

  public boolean resolveCanEdit(Mission mission) {
    if (mission == null) return false;
    return missionSecurityService.canManageMission(
        mission.getId(), authHelperService.rawAuthentication());
  }

  public boolean resolveCanManageManagers(Mission mission) {
    if (mission == null) return false;
    return missionSecurityService.canManageManagers(
        mission.getId(), authHelperService.rawAuthentication());
  }

  /**
   * Counts participants that have been checked in. A participant is considered checked in as soon
   * as {@code startTime} is set (see {@link
   * de.greluc.krt.iri.basetool.backend.service.MissionService#checkIn}).
   */
  public int resolveCheckedInParticipants(Mission mission) {
    if (mission == null || mission.getParticipants() == null) return 0;
    return (int)
        mission.getParticipants().stream()
            .filter(p -> p != null && p.getStartTime() != null)
            .count();
  }

  /** Counts all registered/enrolled participants of the mission, regardless of check-in state. */
  public int resolveRegisteredParticipants(Mission mission) {
    if (mission == null || mission.getParticipants() == null) return 0;
    return mission.getParticipants().size();
  }

  public abstract MissionParticipantDto toDto(MissionParticipant participant);

  @Mapping(target = "crew", expression = "java(resolveCrew(unit))")
  public abstract MissionUnitDto toDto(MissionUnit unit);

  @Mapping(target = "participantId", source = "participant.id")
  @Mapping(target = "participantName", expression = "java(resolveParticipantName(crew))")
  public abstract MissionCrewDto toDto(MissionCrew crew);

  /**
   * Sorts crew members of a mission unit so that leadership roles appear first. A crew member is
   * considered a leader if at least one of its assigned JobTypes is flagged as leadership role
   * (independent of archetype so CREW and MISSION leadership JobTypes both qualify; MISSION
   * semantics remain unchanged). Secondary sort is stable by participant display name to keep the
   * previous alphabetical ordering for non-leaders.
   */
  public java.util.List<MissionCrewDto> resolveCrew(MissionUnit unit) {
    if (unit == null || unit.getCrew() == null) return java.util.List.of();
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
    if (crew == null || crew.getJobTypes() == null) return false;
    for (JobType jt : crew.getJobTypes()) {
      if (jt != null && jt.isLeadershipRole()) return true;
    }
    return false;
  }

  @Mapping(target = "missionId", source = "mission.id")
  public abstract MissionFinanceEntryDto toDto(MissionFinanceEntry entry);

  public abstract FrequencyTypeDto toDto(FrequencyType frequencyType);

  public abstract MissionFrequencyDto toDto(MissionFrequency missionFrequency);

  @Mapping(target = "parentId", source = "parent.id")
  @Mapping(target = "isLeadershipRole", source = "leadershipRole")
  public abstract JobTypeDto toDto(JobType jobType);

  public abstract SquadronDto toDto(Squadron squadron);

  public String resolveParticipantName(MissionCrew crew) {
    if (crew.getParticipant() == null) return null;
    if (crew.getParticipant().getUser() != null) {
      return crew.getParticipant().getUser().getEffectiveName();
    }
    return crew.getParticipant().getGuestName();
  }
}
