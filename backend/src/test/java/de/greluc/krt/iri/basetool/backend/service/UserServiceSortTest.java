package de.greluc.krt.iri.basetool.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.backend.repository.*;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

/** Verifies that {@link UserService#findAll()} requests case-insensitive username sorting. */
@ExtendWith(MockitoExtension.class)
class UserServiceSortTest {

  @Mock private UserRepository userRepository;

  @Mock private RoleRepository roleRepository;

  @Mock private InventoryItemRepository inventoryItemRepository;
  @Mock private ShipRepository shipRepository;
  @Mock private RefineryOrderRepository refineryOrderRepository;
  @Mock private MissionRepository missionRepository;
  @Mock private JobOrderRepository jobOrderRepository;
  @Mock private MissionParticipantRepository missionParticipantRepository;
  @Mock private SquadronRepository squadronRepository;
  @Mock private AuthHelperService authHelperService;
  @Mock private SquadronScopeService squadronScopeService;

  @InjectMocks private UserService userService;

  @BeforeEach
  void setUp() {}

  @Test
  void findAll_shouldRequestSortedUsers() {
    // Given: admin in "all squadrons" mode — squadron scope is empty so the repository receives
    // a null filter alongside the case-insensitive sort.
    when(squadronScopeService.currentSquadronId()).thenReturn(Optional.empty());
    when(userRepository.findAllScopedList(
            org.mockito.ArgumentMatchers.<java.util.UUID>any(),
            org.mockito.ArgumentMatchers.<Sort>any()))
        .thenReturn(Collections.emptyList());

    // When
    userService.findAll();

    // Then
    ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
    verify(userRepository)
        .findAllScopedList(org.mockito.ArgumentMatchers.isNull(), sortCaptor.capture());

    Sort capturedSort = sortCaptor.getValue();
    Sort.Order order = capturedSort.getOrderFor("username");

    assertThat(order).isNotNull();
    assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
    assertThat(order.isIgnoreCase()).isTrue(); // Optional but good for usernames
  }
}
