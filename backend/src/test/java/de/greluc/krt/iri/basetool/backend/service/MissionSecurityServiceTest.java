package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.model.Mission;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.repository.MissionParticipantRepository;
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

import de.greluc.krt.iri.basetool.backend.model.MissionParticipant;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
@ExtendWith(MockitoExtension.class)
class MissionSecurityServiceTest {

    @Mock
    private MissionRepository missionRepository;

    @Mock
    private UserService userService;

    @Mock
    private MissionParticipantRepository missionParticipantRepository;

    @Mock
    private de.greluc.krt.iri.basetool.backend.repository.MissionFinanceEntryRepository missionFinanceEntryRepository;

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
        missionSecurityService = new MissionSecurityService(missionRepository, userService, roleHierarchy, missionParticipantRepository, missionFinanceEntryRepository);
        
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

    // ---------------------------------------------------------------------
    // canAccessParticipant: Self-Edit support for logged-in mission participants.
    // A participant may be edited by its owner (participant.user.id == jwt.sub)
    // or by any user with elevated MISSION_MANAGER / OFFICER / ADMIN authority.
    // ---------------------------------------------------------------------

    @Test
    void canAccessParticipant_Owner_ShouldReturnTrue() {
        // Given: participant belongs to current user, no elevated role
        UUID participantId = UUID.randomUUID();
        MissionParticipant participant = new MissionParticipant();
        participant.setId(participantId);
        participant.setMission(mission);
        participant.setUser(user);

        when(missionParticipantRepository.findById(participantId)).thenReturn(Optional.of(participant));
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn("some-jwt-principal");
        when(authentication.getAuthorities()).thenAnswer(i -> Collections.singletonList(new SimpleGrantedAuthority("ROLE_SQUADRON_MEMBER")));
        when(userService.getCurrentUser()).thenReturn(Optional.of(user));

        // When / Then: self-edit allowed
        assertTrue(missionSecurityService.canAccessParticipant(missionId, participantId, authentication));
    }

    @Test
    void canAccessParticipant_ForeignUserWithoutPrivilege_ShouldReturnFalse() {
        // Given: participant belongs to a DIFFERENT user
        UUID participantId = UUID.randomUUID();
        User otherUser = new User();
        otherUser.setId(UUID.randomUUID());
        MissionParticipant participant = new MissionParticipant();
        participant.setId(participantId);
        participant.setMission(mission);
        participant.setUser(otherUser);

        when(missionParticipantRepository.findById(participantId)).thenReturn(Optional.of(participant));
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn("some-jwt-principal");
        when(authentication.getAuthorities()).thenAnswer(i -> Collections.singletonList(new SimpleGrantedAuthority("ROLE_SQUADRON_MEMBER")));
        when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
        when(userService.getCurrentUser()).thenReturn(Optional.of(user));

        // When / Then: foreign edit forbidden
        assertFalse(missionSecurityService.canAccessParticipant(missionId, participantId, authentication));
    }

    @Test
    void canAccessParticipant_MissionManager_ShouldReturnTrueForAnyParticipant() {
        // Given: participant belongs to a DIFFERENT user, but caller is MISSION_MANAGER
        UUID participantId = UUID.randomUUID();
        User otherUser = new User();
        otherUser.setId(UUID.randomUUID());
        MissionParticipant participant = new MissionParticipant();
        participant.setId(participantId);
        participant.setMission(mission);
        participant.setUser(otherUser);

        when(missionParticipantRepository.findById(participantId)).thenReturn(Optional.of(participant));
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn("some-jwt-principal");
        when(authentication.getAuthorities()).thenAnswer(i -> Collections.singletonList(new SimpleGrantedAuthority("ROLE_MISSION_MANAGER")));

        // When / Then: privileged access granted without owner match
        assertTrue(missionSecurityService.canAccessParticipant(missionId, participantId, authentication));
    }

    @Test
    void canAccessParticipant_AnonymousCaller_ShouldReturnFalse() {
        // Given: registered participant, anonymous caller
        UUID participantId = UUID.randomUUID();
        MissionParticipant participant = new MissionParticipant();
        participant.setId(participantId);
        participant.setMission(mission);
        participant.setUser(user);

        when(missionParticipantRepository.findById(participantId)).thenReturn(Optional.of(participant));
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn("anonymousUser");

        // When / Then
        assertFalse(missionSecurityService.canAccessParticipant(missionId, participantId, authentication));
    }

    @Test
    void canAccessParticipant_GuestParticipant_ShouldReturnTrueForAnyone() {
        // Given: guest (unlinked) participant
        UUID participantId = UUID.randomUUID();
        MissionParticipant participant = new MissionParticipant();
        participant.setId(participantId);
        participant.setMission(mission);
        participant.setUser(null);
        participant.setGuestName("Somebody");

        when(missionParticipantRepository.findById(participantId)).thenReturn(Optional.of(participant));

        // When / Then: guest entries are editable by anyone, matching add-participant behaviour
        assertTrue(missionSecurityService.canAccessParticipant(missionId, participantId, authentication));
    }

    @Test
    void canAccessParticipant_MissingParticipant_ShouldThrow404() {
        // Regression: a stale frontend row (participant concurrently deleted) must
        // surface as 404 Not Found, not as a generic 500 Internal Server Error.
        UUID participantId = UUID.randomUUID();
        when(missionParticipantRepository.findById(participantId)).thenReturn(Optional.empty());

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> missionSecurityService.canAccessParticipant(missionId, participantId, authentication));
    }

    @Test
    void canAccessParticipant_MissionMismatch_ShouldReturnFalse() {
        // Given: participant belongs to a different mission than the one addressed
        UUID participantId = UUID.randomUUID();
        Mission otherMission = new Mission();
        otherMission.setId(UUID.randomUUID());
        MissionParticipant participant = new MissionParticipant();
        participant.setId(participantId);
        participant.setMission(otherMission);
        participant.setUser(user);

        when(missionParticipantRepository.findById(participantId)).thenReturn(Optional.of(participant));

        assertFalse(missionSecurityService.canAccessParticipant(missionId, participantId, authentication));
    }

    // ---------------------------------------------------------------------
    // canChangeOwner — tighter than canManageManagers: only the current owner
    // or ROLE_ADMIN / ROLE_OFFICER may transfer mission ownership. Regular
    // co-managers and MISSION_MANAGER role holders MUST NOT be permitted to
    // displace the owner. Each branch below maps to a documented invariant
    // in the production Javadoc (MissionSecurityService.java:172-179).
    // ---------------------------------------------------------------------

    @Test
    void canChangeOwner_NullAuthentication_ShouldReturnFalse() {
        assertFalse(missionSecurityService.canChangeOwner(missionId, null));
    }

    @Test
    void canChangeOwner_NotAuthenticated_ShouldReturnFalse() {
        when(authentication.isAuthenticated()).thenReturn(false);

        assertFalse(missionSecurityService.canChangeOwner(missionId, authentication));
    }

    @Test
    void canChangeOwner_AnonymousPrincipal_ShouldReturnFalse() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn("anonymousUser");

        assertFalse(missionSecurityService.canChangeOwner(missionId, authentication));
    }

    @Test
    void canChangeOwner_AdminRole_ShouldReturnTrue() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn("real-jwt-sub");
        when(authentication.getAuthorities())
                .thenAnswer(i -> Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")));

        assertTrue(missionSecurityService.canChangeOwner(missionId, authentication));
    }

    @Test
    void canChangeOwner_OfficerRole_ShouldReturnTrue() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn("real-jwt-sub");
        when(authentication.getAuthorities())
                .thenAnswer(i -> Collections.singletonList(new SimpleGrantedAuthority("ROLE_OFFICER")));

        assertTrue(missionSecurityService.canChangeOwner(missionId, authentication));
    }

    @Test
    void canChangeOwner_MissionManagerRole_ShouldReturnFalse() {
        // CRITICAL invariant: MISSION_MANAGER must NOT be permitted to change
        // ownership — that would let a co-manager grab any mission they
        // already manage.
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn("real-jwt-sub");
        when(authentication.getAuthorities())
                .thenAnswer(i -> Collections.singletonList(new SimpleGrantedAuthority("ROLE_MISSION_MANAGER")));
        when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
        when(userService.getCurrentUser()).thenReturn(Optional.of(user));
        // user is NOT the owner.

        assertFalse(missionSecurityService.canChangeOwner(missionId, authentication));
    }

    @Test
    void canChangeOwner_CoManagerWithoutOfficerRole_ShouldReturnFalse() {
        // Same invariant from the other direction: a manager added to
        // mission.managers but with only ROLE_SQUADRON_MEMBER cannot
        // transfer ownership.
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn("real-jwt-sub");
        when(authentication.getAuthorities())
                .thenAnswer(i -> Collections.singletonList(new SimpleGrantedAuthority("ROLE_SQUADRON_MEMBER")));
        when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
        when(userService.getCurrentUser()).thenReturn(Optional.of(user));
        mission.getManagers().add(user); // user is a co-manager
        // user is NOT the owner

        assertFalse(missionSecurityService.canChangeOwner(missionId, authentication),
                "co-manager status must not grant the right to change ownership");
    }

    @Test
    void canChangeOwner_OwnerOnly_ShouldReturnTrue() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn("real-jwt-sub");
        when(authentication.getAuthorities())
                .thenAnswer(i -> Collections.singletonList(new SimpleGrantedAuthority("ROLE_SQUADRON_MEMBER")));
        when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
        when(userService.getCurrentUser()).thenReturn(Optional.of(user));
        mission.setOwner(user);

        assertTrue(missionSecurityService.canChangeOwner(missionId, authentication));
    }

    @Test
    void canChangeOwner_OwnerButGetCurrentUserReturnsEmpty_ShouldReturnFalse() {
        // Defensive: even with a valid Authentication, if the local user table
        // doesn't have a matching row (e.g. a freshly-issued JWT before
        // syncUser ran), refuse the operation rather than silently bypassing
        // the owner check.
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn("real-jwt-sub");
        when(authentication.getAuthorities())
                .thenAnswer(i -> Collections.singletonList(new SimpleGrantedAuthority("ROLE_SQUADRON_MEMBER")));
        when(userService.getCurrentUser()).thenReturn(Optional.empty());

        assertFalse(missionSecurityService.canChangeOwner(missionId, authentication));
    }

    @Test
    void canChangeOwner_MissionNotFound_ShouldReturnFalse() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn("real-jwt-sub");
        when(authentication.getAuthorities())
                .thenAnswer(i -> Collections.singletonList(new SimpleGrantedAuthority("ROLE_SQUADRON_MEMBER")));
        when(missionRepository.findById(missionId)).thenReturn(Optional.empty());
        when(userService.getCurrentUser()).thenReturn(Optional.of(user));

        assertFalse(missionSecurityService.canChangeOwner(missionId, authentication),
                "a missing mission must NOT default to true (orElse(false))");
    }

    @Test
    void canChangeOwner_NotOwner_ShouldReturnFalse() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn("real-jwt-sub");
        when(authentication.getAuthorities())
                .thenAnswer(i -> Collections.singletonList(new SimpleGrantedAuthority("ROLE_SQUADRON_MEMBER")));
        User differentOwner = new User();
        differentOwner.setId(UUID.randomUUID());
        mission.setOwner(differentOwner);

        when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
        when(userService.getCurrentUser()).thenReturn(Optional.of(user));

        assertFalse(missionSecurityService.canChangeOwner(missionId, authentication));
    }
}
