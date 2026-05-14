package de.greluc.krt.iri.basetool.backend;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
import de.greluc.krt.iri.basetool.backend.mapper.InventoryItemMapper;
import de.greluc.krt.iri.basetool.backend.mapper.MaterialMapper;
import de.greluc.krt.iri.basetool.backend.model.InventoryItem;
import de.greluc.krt.iri.basetool.backend.model.JobOrder;
import de.greluc.krt.iri.basetool.backend.model.Mission;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.dto.BulkCheckoutRequest;
import de.greluc.krt.iri.basetool.backend.repository.InventoryItemRepository;
import de.greluc.krt.iri.basetool.backend.repository.JobOrderRepository;
import de.greluc.krt.iri.basetool.backend.repository.LocationRepository;
import de.greluc.krt.iri.basetool.backend.repository.MaterialRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionFinanceEntryRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionParticipantRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import de.greluc.krt.iri.basetool.backend.service.InventoryItemService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

/** Unit tests for the bulk checkout functionality in {@link InventoryItemService}. */
@ExtendWith(MockitoExtension.class)
class InventoryItemServiceBulkCheckoutTest {

  @Mock private InventoryItemRepository inventoryItemRepository;
  @Mock private UserRepository userRepository;
  @Mock private MaterialRepository materialRepository;
  @Mock private LocationRepository locationRepository;
  @Mock private JobOrderRepository jobOrderRepository;
  @Mock private MissionRepository missionRepository;
  @Mock private MissionFinanceEntryRepository missionFinanceEntryRepository;
  @Mock private MissionParticipantRepository missionParticipantRepository;
  @Mock private InventoryItemMapper inventoryItemMapper;
  @Mock private MaterialMapper materialMapper;

  @InjectMocks private InventoryItemService inventoryItemService;

  // -------------------------------------------------------------------------
  // Helper
  // -------------------------------------------------------------------------

  private User userWithId(UUID id) {
    User u = new User();
    u.setId(id);
    return u;
  }

  private InventoryItem itemOwnedBy(UUID itemId, UUID ownerId) {
    InventoryItem item = new InventoryItem();
    item.setId(itemId);
    item.setUser(userWithId(ownerId));
    return item;
  }

  // -------------------------------------------------------------------------
  // Tests
  // -------------------------------------------------------------------------

  @Test
  void bulkCheckout_successfullyRemovesMultipleItems() {
    // Given
    UUID userId = UUID.randomUUID();
    UUID itemId1 = UUID.randomUUID();
    UUID itemId2 = UUID.randomUUID();

    InventoryItem item1 = itemOwnedBy(itemId1, userId);
    InventoryItem item2 = itemOwnedBy(itemId2, userId);

    when(inventoryItemRepository.findByIdForUpdate(itemId1)).thenReturn(Optional.of(item1));
    when(inventoryItemRepository.findByIdForUpdate(itemId2)).thenReturn(Optional.of(item2));

    BulkCheckoutRequest request = new BulkCheckoutRequest(List.of(itemId1, itemId2));

    // When
    inventoryItemService.bulkCheckout(request, userId);

    // Then
    verify(inventoryItemRepository).flush();
    verify(inventoryItemRepository).deleteAllById(List.of(itemId1, itemId2));
  }

  @Test
  void bulkCheckout_clearsJobOrderAndMissionAssociations() {
    // Given
    UUID userId = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();

    InventoryItem item = itemOwnedBy(itemId, userId);
    JobOrder jobOrder = new JobOrder();
    jobOrder.setId(UUID.randomUUID());
    Mission mission = new Mission();
    mission.setId(UUID.randomUUID());
    item.setJobOrder(jobOrder);
    item.setMission(mission);

    when(inventoryItemRepository.findByIdForUpdate(itemId)).thenReturn(Optional.of(item));

    BulkCheckoutRequest request = new BulkCheckoutRequest(List.of(itemId));

    // When
    inventoryItemService.bulkCheckout(request, userId);

    // Then – associations must be cleared before deletion
    assertNull(item.getJobOrder(), "JobOrder association must be cleared");
    assertNull(item.getMission(), "Mission association must be cleared");
    verify(inventoryItemRepository).flush();
    verify(inventoryItemRepository).deleteAllById(List.of(itemId));
  }

  @Test
  void bulkCheckout_throwsAccessDenied_whenItemBelongsToAnotherUser() {
    // Given
    UUID currentUserId = UUID.randomUUID();
    UUID otherUserId = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();

    InventoryItem item = itemOwnedBy(itemId, otherUserId);
    when(inventoryItemRepository.findByIdForUpdate(itemId)).thenReturn(Optional.of(item));

    BulkCheckoutRequest request = new BulkCheckoutRequest(List.of(itemId));

    // When / Then
    assertThrows(
        AccessDeniedException.class,
        () -> inventoryItemService.bulkCheckout(request, currentUserId));

    verify(inventoryItemRepository, never()).deleteAllById(any());
  }

  @Test
  void bulkCheckout_throwsNotFound_whenItemDoesNotExist() {
    // Given
    UUID userId = UUID.randomUUID();
    UUID missingItemId = UUID.randomUUID();

    when(inventoryItemRepository.findByIdForUpdate(missingItemId)).thenReturn(Optional.empty());

    BulkCheckoutRequest request = new BulkCheckoutRequest(List.of(missingItemId));

    // When / Then
    NotFoundException ex =
        assertThrows(
            NotFoundException.class, () -> inventoryItemService.bulkCheckout(request, userId));

    verify(inventoryItemRepository, never()).deleteAllById(any());
  }

  @Test
  void bulkCheckoutRequest_failsValidation_whenItemIdsIsEmpty() {
    // Given
    Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    BulkCheckoutRequest request = new BulkCheckoutRequest(List.of());

    // When
    Set<ConstraintViolation<BulkCheckoutRequest>> violations = validator.validate(request);

    // Then
    assertFalse(violations.isEmpty(), "Validation should fail for empty itemIds list");
  }

  @Test
  void bulkCheckoutRequest_failsValidation_whenItemIdsIsNull() {
    // Given
    Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    BulkCheckoutRequest request = new BulkCheckoutRequest(null);

    // When
    Set<ConstraintViolation<BulkCheckoutRequest>> violations = validator.validate(request);

    // Then
    assertFalse(violations.isEmpty(), "Validation should fail for null itemIds");
  }

  @Test
  void bulkCheckout_stopsImmediately_whenFirstItemBelongsToOtherUser() {
    // Given – two items, first belongs to another user
    UUID currentUserId = UUID.randomUUID();
    UUID otherUserId = UUID.randomUUID();
    UUID itemId1 = UUID.randomUUID();
    UUID itemId2 = UUID.randomUUID();

    InventoryItem foreignItem = itemOwnedBy(itemId1, otherUserId);
    when(inventoryItemRepository.findByIdForUpdate(itemId1)).thenReturn(Optional.of(foreignItem));

    BulkCheckoutRequest request = new BulkCheckoutRequest(List.of(itemId1, itemId2));

    // When / Then
    assertThrows(
        AccessDeniedException.class,
        () -> inventoryItemService.bulkCheckout(request, currentUserId));

    // Second item must never be fetched
    verify(inventoryItemRepository, never()).findByIdForUpdate(itemId2);
    verify(inventoryItemRepository, never()).deleteAllById(any());
  }
}
