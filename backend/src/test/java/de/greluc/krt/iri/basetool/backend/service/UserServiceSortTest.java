package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceSortTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

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

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setUp() {
    }

    @Test
    void findAll_shouldRequestSortedUsers() {
        // Given
        when(userRepository.findAll(org.mockito.ArgumentMatchers.<Sort>any())).thenReturn(Collections.emptyList());

        // When
        userService.findAll();

        // Then
        ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
        verify(userRepository).findAll(sortCaptor.capture());

        Sort capturedSort = sortCaptor.getValue();
        Sort.Order order = capturedSort.getOrderFor("username");
        
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
        assertThat(order.isIgnoreCase()).isTrue(); // Optional but good for usernames
    }
}
