package de.greluc.krt.iri.basetool.backend.mapper;

import de.greluc.krt.iri.basetool.backend.model.*;
import de.greluc.krt.iri.basetool.backend.model.dto.*;
import de.greluc.krt.iri.basetool.backend.service.MissionSecurityService;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;

@Mapper(componentModel = "spring", uses = {ShipMapper.class, UserMapper.class, InventoryItemMapper.class, RefineryOrderMapper.class, OperationMapper.class}, unmappedTargetPolicy = org.mapstruct.ReportingPolicy.IGNORE)
public abstract class MissionMapper {

    @Autowired
    protected MissionSecurityService missionSecurityService;

    @Mapping(target = "description", expression = "java(resolveDescription(mission))")
    @Mapping(target = "canEdit", expression = "java(resolveCanEdit(mission))")
    @Mapping(target = "canManageManagers", expression = "java(resolveCanManageManagers(mission))")
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
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            return mission.getDescription();
        }
        return null;
    }

    public boolean resolveCanEdit(Mission mission) {
        if (mission == null) return false;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return missionSecurityService.canManageMission(mission.getId(), auth);
    }

    public boolean resolveCanManageManagers(Mission mission) {
        if (mission == null) return false;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return missionSecurityService.canManageManagers(mission.getId(), auth);
    }

    public abstract MissionParticipantDto toDto(MissionParticipant participant);

    public abstract MissionUnitDto toDto(MissionUnit unit);

    @Mapping(target = "participantId", source = "participant.id")
    @Mapping(target = "participantName", expression = "java(resolveParticipantName(crew))")
    public abstract MissionCrewDto toDto(MissionCrew crew);

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
