package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.model.Mission;
import de.greluc.krt.iri.basetool.backend.model.User;
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
