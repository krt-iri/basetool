package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.model.Mission;
import de.greluc.krt.iri.basetool.backend.model.MissionFinanceEntry;
import de.greluc.krt.iri.basetool.backend.model.MissionParticipant;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.repository.MissionFinanceEntryRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionParticipantRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionRepository;
import de.greluc.krt.iri.basetool.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MissionSecurityService {

    private final MissionRepository missionRepository;
    private final UserService userService;
    private final RoleHierarchy roleHierarchy;
    private final MissionParticipantRepository missionParticipantRepository;
    private final MissionFinanceEntryRepository missionFinanceEntryRepository;

    @Transactional(readOnly = true)
    public boolean canAccessParticipant(UUID missionId, UUID participantId, Authentication authentication) {
        MissionParticipant p = missionParticipantRepository.findById(participantId)
                .orElseThrow(() -> new RuntimeException("Participant not found"));

        if (!p.getMission().getId().equals(missionId)) {
            log.warn("Mission ID mismatch: {} != {}", p.getMission().getId(), missionId);
            return false;
        }

        if (p.getUser() == null) {
            return true;
        }

        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        // Handle anonymous authentication correctly
        boolean isAnonymous = "anonymousUser".equals(authentication.getPrincipal());

        boolean canManage = !isAnonymous && (
                authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_MISSION_MANAGER")) ||
                canManageMission(missionId, authentication)
        );

        UUID currentUserId = userService.getCurrentUser().map(User::getId).orElse(null);
        if (isAnonymous) {
            return false;
        }
        if (!canManage && (currentUserId == null || !p.getUser().getId().equals(currentUserId))) {
            return false;
        }
        return true;
    }

    @Transactional(readOnly = true)
    public boolean canEditFinanceEntry(UUID entryId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        MissionFinanceEntry entry = missionFinanceEntryRepository.findById(entryId)
                .orElseThrow(() -> new RuntimeException("Finance entry not found"));

        // Check if user is ADMIN or OFFICER
        boolean isAdminOrOfficer = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_OFFICER"));
        
        if (isAdminOrOfficer) {
            return true;
        }

        // Must be the owner of the entry (if the participant has a linked user account)
        UUID currentUserId = userService.getCurrentUser().map(User::getId).orElse(null);
        if (currentUserId == null || entry.getParticipant().getUser() == null || !entry.getParticipant().getUser().getId().equals(currentUserId)) {
            return false;
        }

        // Must be a registered participant of this mission
        return missionParticipantRepository.findByMissionIdAndUserId(
                entry.getMission().getId(), currentUserId).isPresent();
    }

    @Transactional(readOnly = true)
    public boolean canManageMission(UUID missionId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        Collection<? extends GrantedAuthority> reachable = roleHierarchy.getReachableGrantedAuthorities(authentication.getAuthorities());
        if (reachable.stream().anyMatch(a -> 
                a.getAuthority().equals("ROLE_MISSION_MANAGER") ||
                a.getAuthority().equals("MISSION_MANAGER") ||
                a.getAuthority().equals("MISSION_MANAGE") ||
                a.getAuthority().equals("ROLE_OFFICER") ||
                a.getAuthority().equals("ROLE_ADMIN"))) {
            return true;
        }

        Optional<Mission> missionOpt = missionRepository.findById(missionId);
        if (missionOpt.isEmpty()) {
            return false;
        }

        Mission mission = missionOpt.get();
        return isOwnerOrManager(mission, authentication);
    }

    @Transactional(readOnly = true)
    public boolean canManageManagers(UUID missionId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("[DEBUG_LOG] Authentication failed or missing for canManageManagers on mission {}", missionId);
            return false;
        }

        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        Collection<? extends GrantedAuthority> reachable = roleHierarchy.getReachableGrantedAuthorities(authorities);
        log.info("[DEBUG_LOG] User {} authorities: {}, Reachable: {}", authentication.getName(), authorities, reachable);
        
        boolean hasAuthority = reachable.stream().anyMatch(a -> 
                a.getAuthority().equals("ROLE_MISSION_MANAGER") ||
                a.getAuthority().equals("MISSION_MANAGER") ||
                a.getAuthority().equals("MISSION_MANAGE") ||
                a.getAuthority().equals("ROLE_OFFICER") ||
                a.getAuthority().equals("ROLE_ADMIN"));
        
        if (hasAuthority) {
            log.info("[DEBUG_LOG] Access granted for user {} via authority for mission {}", authentication.getName(), missionId);
            return true;
        }

        Optional<Mission> missionOpt = missionRepository.findById(missionId);
        if (missionOpt.isEmpty()) {
            log.warn("[DEBUG_LOG] Mission {} not found for canManageManagers check", missionId);
            return false;
        }

        boolean result = isOwnerOrManager(missionOpt.get(), authentication);
        log.info("[DEBUG_LOG] Access check for user {} on mission {} (owner/manager): {}", authentication.getName(), missionId, result);
        return result;
    }

    public boolean isOwnerOrManager(Mission mission, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        UUID userId = userService.getCurrentUser().map(User::getId).orElse(null);
        if (userId == null) {
            return false;
        }

        // Check if user is owner
        if (mission.getOwner() != null && mission.getOwner().getId().equals(userId)) {
            return true;
        }

        // Check if user is in managers list
        return mission.getManagers().stream()
                .anyMatch(user -> user.getId().equals(userId));
    }
}
