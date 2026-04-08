package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.model.Mission;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.repository.MissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MissionSecurityServiceTest {

    @Mock
    private MissionRepository missionRepository;

    @Mock
    private UserService userService;

    private RoleHierarchy roleHierarchy;

    @InjectMocks
    private MissionSecurityService missionSecurityService;

    private UUID missionId;
    private UUID userId;
    private User user;
    private Mission mission;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        roleHierarchy = de.greluc.krt.iri.basetool.backend.config.SecurityConfig.roleHierarchy();
        missionSecurityService = new MissionSecurityService(missionRepository, userService, roleHierarchy);
        
        missionId = UUID.randomUUID();
        userId = UUID.randomUUID();
        user = new User();
        user.setId(userId);
        mission = new Mission();
        mission.setId(missionId);
        authentication = mock(Authentication.class);
    }

    @Test
    void canManageManagers_GlobalMissionManager_ShouldReturnTrue() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getAuthorities()).thenAnswer(i -> Collections.singletonList(new SimpleGrantedAuthority("ROLE_MISSION_MANAGER")));

        assertTrue(missionSecurityService.canManageManagers(missionId, authentication));
    }

    @Test
    void canManageManagers_OfficerRole_ShouldReturnTrue() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getAuthorities()).thenAnswer(i -> Collections.singletonList(new SimpleGrantedAuthority("ROLE_OFFICER")));

        assertTrue(missionSecurityService.canManageManagers(missionId, authentication));
    }

    @Test
    void canManageManagers_MissionManagePermission_ShouldReturnTrue() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getAuthorities()).thenAnswer(i -> Collections.singletonList(new SimpleGrantedAuthority("MISSION_MANAGE")));

        assertTrue(missionSecurityService.canManageManagers(missionId, authentication));
    }

    @Test
    void canManageManagers_Owner_ShouldReturnTrue() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getAuthorities()).thenReturn(Collections.emptyList());
        when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
        when(userService.getCurrentUser()).thenReturn(Optional.of(user));
        mission.setOwner(user);

        assertTrue(missionSecurityService.canManageManagers(missionId, authentication));
    }

    @Test
    void canManageManagers_CoManager_ShouldReturnTrue() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getAuthorities()).thenReturn(Collections.emptyList());
        when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
        when(userService.getCurrentUser()).thenReturn(Optional.of(user));
        mission.getManagers().add(user);

        assertTrue(missionSecurityService.canManageManagers(missionId, authentication));
    }

    @Test
    void canManageManagers_RegularUser_ShouldReturnFalse() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getAuthorities()).thenReturn(Collections.emptyList());
        when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
        when(userService.getCurrentUser()).thenReturn(Optional.of(user));

        assertFalse(missionSecurityService.canManageManagers(missionId, authentication));
    }
}
