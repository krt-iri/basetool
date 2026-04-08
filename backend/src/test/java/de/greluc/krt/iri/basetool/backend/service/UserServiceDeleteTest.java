package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.model.Role;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceDeleteTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private InventoryItemRepository inventoryItemRepository;
    @Mock
    private ShipRepository shipRepository;
    @Mock
    private RefineryOrderRepository refineryOrderRepository;
    @Mock
    private MissionRepository missionRepository;
    @Mock
    private JobOrderRepository jobOrderRepository;
    @Mock
    private MissionParticipantRepository missionParticipantRepository;
    @Mock
    private RoleRepository roleRepository;

    @InjectMocks
    private UserService userService;

    private User user;
    private User admin;
    private UUID userId;
    private UUID adminId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        adminId = UUID.randomUUID();

        user = new User();
        user.setId(userId);
        user.setInKeycloak(false);

        admin = new User();
        admin.setId(adminId);
        admin.setInKeycloak(true);
    }

    @Test
    void shouldDeleteUserAndReassignReferences() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.findAllAdmins()).thenReturn(List.of(admin));

        // When
        userService.deleteUser(userId);

        // Then
        verify(inventoryItemRepository).updateOwner(user, admin);
        verify(shipRepository).updateOwner(user, admin);
        verify(refineryOrderRepository).updateOwner(user, admin);
        verify(missionRepository).updateOwner(user, admin);
        verify(missionRepository).removeManager(userId);
        verify(jobOrderRepository).removeAssignee(userId);
        verify(missionParticipantRepository).unlinkUser(userId);
        verify(userRepository).delete(user);
    }

    @Test
    void shouldNotDeleteUserStillInKeycloak() {
        // Given
        user.setInKeycloak(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When & Then
        assertThrows(IllegalStateException.class, () -> userService.deleteUser(userId));
        verify(userRepository, never()).delete(any());
    }

    @Test
    void shouldThrowExceptionIfNoAdminFound() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.findAllAdmins()).thenReturn(Collections.emptyList());

        // When & Then
        assertThrows(IllegalStateException.class, () -> userService.deleteUser(userId));
        verify(userRepository, never()).delete(any());
    }

    @Test
    void shouldThrowExceptionIfUserNotFound() {
        // Given
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(NoSuchElementException.class, () -> userService.deleteUser(userId));
    }
}
