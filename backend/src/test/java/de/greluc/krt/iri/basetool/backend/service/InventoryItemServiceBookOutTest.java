package de.greluc.krt.iri.basetool.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.backend.exception.BadRequestException;
import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
import de.greluc.krt.iri.basetool.backend.mapper.InventoryItemMapper;
import de.greluc.krt.iri.basetool.backend.mapper.MaterialMapper;
import de.greluc.krt.iri.basetool.backend.model.CheckoutType;
import de.greluc.krt.iri.basetool.backend.model.FinanceType;
import de.greluc.krt.iri.basetool.backend.model.InventoryItem;
import de.greluc.krt.iri.basetool.backend.model.Location;
import de.greluc.krt.iri.basetool.backend.model.Material;
import de.greluc.krt.iri.basetool.backend.model.Mission;
import de.greluc.krt.iri.basetool.backend.model.MissionFinanceEntry;
import de.greluc.krt.iri.basetool.backend.model.MissionParticipant;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.dto.InventoryItemBookOutDto;
import de.greluc.krt.iri.basetool.backend.model.dto.InventoryItemDto;
import de.greluc.krt.iri.basetool.backend.repository.InventoryItemRepository;
import de.greluc.krt.iri.basetool.backend.repository.JobOrderRepository;
import de.greluc.krt.iri.basetool.backend.repository.LocationRepository;
import de.greluc.krt.iri.basetool.backend.repository.MaterialRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionFinanceEntryRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionParticipantRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;

/**
 * Coverage for {@link InventoryItemService#bookOutInventoryItem} — the money- and security-critical
 * "check out" flow that combines optimistic locking, owner-vs-admin authorisation, amount
 * validation, CheckoutType inference (DISCARD / TRANSFER / SELL), partial-vs-full deletion, and the
 * {@code MissionFinanceEntry} side effect for SELL.
 *
 * <p>Coverage analysis flagged this as the largest concentrated branch gap in the service package
 * (19/28 branches uncovered). A bug here means wrong ownership decisions, lost inventory, or
 * double-counted income.
 */
@ExtendWith(MockitoExtension.class)
class InventoryItemServiceBookOutTest {

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
  @Mock private OwnerScopeService ownerScopeService;

  @InjectMocks private InventoryItemService service;

  private static final UUID ITEM_ID = UUID.randomUUID();
  private static final UUID OWNER_ID = UUID.randomUUID();
  private static final UUID ADMIN_ID = UUID.randomUUID();
  private static final UUID LOCATION_ID = UUID.randomUUID();

  private User owner;
  private Location location;
  private Material material;

  @BeforeEach
  void setUpEntities() {
    owner = new User();
    owner.setId(OWNER_ID);
    owner.setUsername("alice");

    location = new Location();
    location.setId(LOCATION_ID);
    location.setName("ARC-L1");

    material = new Material();
    material.setId(UUID.randomUUID());
    material.setName("Quantanium");

    // The mapper is called on the saved item; return a sentinel DTO so we can
    // verify the return value identity.
    lenient()
        .when(inventoryItemMapper.toDto(any(InventoryItem.class)))
        .thenAnswer(inv -> sentinelDto(((InventoryItem) inv.getArgument(0)).getAmount()));
  }

  // ---------------------------------------------------------------
  // Up-front guards
  // ---------------------------------------------------------------

  @Nested
  class GuardTests {

    @Test
    void notFound_throws() {
      when(inventoryItemRepository.findById(ITEM_ID)).thenReturn(Optional.empty());

      assertThrows(
          NotFoundException.class,
          () ->
              service.bookOutInventoryItem(
                  ITEM_ID,
                  newDto(1.0, null, null, CheckoutType.DISCARD, null, null, 1L),
                  OWNER_ID,
                  false));
    }

    @Test
    void versionMismatch_throwsOptimisticLockingFailure() {
      InventoryItem item = newItem(10.0, 5L);
      when(inventoryItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));

      assertThrows(
          ObjectOptimisticLockingFailureException.class,
          () ->
              service.bookOutInventoryItem(
                  ITEM_ID,
                  newDto(1.0, null, null, CheckoutType.DISCARD, null, null, 99L),
                  OWNER_ID,
                  false));
    }

    @Test
    void nullVersion_bypassesOptimisticCheck() {
      // dto.version() == null skips the explicit check; Hibernate's
      // UPDATE-WHERE-VERSION fallback still catches stale writes in prod.
      InventoryItem item = newItem(10.0, 5L);
      when(inventoryItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));

      service.bookOutInventoryItem(
          ITEM_ID,
          newDto(1.0, null, null, CheckoutType.DISCARD, null, null, null),
          OWNER_ID,
          false);

      // No exception -> the version check was bypassed.
      verify(inventoryItemRepository).save(item);
    }

    @Test
    void nonOwnerNonAdmin_throwsAccessDenied() {
      // SECURITY: a normal user trying to book out someone else's item -> 403.
      InventoryItem item = newItem(10.0, 1L);
      when(inventoryItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));

      UUID otherUserId = UUID.randomUUID();
      assertThrows(
          AccessDeniedException.class,
          () ->
              service.bookOutInventoryItem(
                  ITEM_ID,
                  newDto(1.0, null, null, CheckoutType.DISCARD, null, null, 1L),
                  otherUserId,
                  false));
      verify(inventoryItemRepository, never()).save(any());
      verify(inventoryItemRepository, never()).delete(any());
    }

    @Test
    void adminBypassesOwnershipCheck() {
      InventoryItem item = newItem(10.0, 1L);
      when(inventoryItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));

      UUID otherUserId = UUID.randomUUID();
      service.bookOutInventoryItem(
          ITEM_ID,
          newDto(1.0, null, null, CheckoutType.DISCARD, null, null, 1L),
          otherUserId,
          /* isAdmin= */ true);

      verify(inventoryItemRepository).save(item);
    }

    @Test
    void amountExceedsAvailable_throwsBadRequest() {
      InventoryItem item = newItem(5.0, 1L);
      when(inventoryItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));

      assertThrows(
          BadRequestException.class,
          () ->
              service.bookOutInventoryItem(
                  ITEM_ID,
                  newDto(10.0, null, null, CheckoutType.DISCARD, null, null, 1L),
                  OWNER_ID,
                  false));
    }
  }

  // ---------------------------------------------------------------
  // CheckoutType inference (when type is null)
  // ---------------------------------------------------------------

  @Nested
  class CheckoutTypeInferenceTests {

    @Test
    void nullType_withTargetUser_inferredAsTransfer() {
      // dto.type == null + targetUserId != null -> infer TRANSFER.
      // Verified indirectly: TRANSFER path saves a new InventoryItem;
      // DISCARD path doesn't.
      UUID targetUserId = UUID.randomUUID();
      User targetUser = new User();
      targetUser.setId(targetUserId);

      InventoryItem item = newItem(10.0, 1L);
      when(inventoryItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));
      when(userRepository.findById(targetUserId)).thenReturn(Optional.of(targetUser));
      when(inventoryItemRepository.save(any(InventoryItem.class)))
          .thenAnswer(inv -> inv.getArgument(0));

      service.bookOutInventoryItem(
          ITEM_ID,
          newDto(1.0, targetUserId, null, /* type= */ null, null, null, 1L),
          OWNER_ID,
          false);

      // TRANSFER -> save called twice (new item + updated source).
      ArgumentCaptor<InventoryItem> captor = ArgumentCaptor.forClass(InventoryItem.class);
      verify(inventoryItemRepository, org.mockito.Mockito.times(2)).save(captor.capture());
      // First save = the new item with targetUser; second = updated source.
      assertSame(targetUser, captor.getAllValues().get(0).getUser());
    }

    @Test
    void nullType_withTargetLocation_inferredAsTransfer() {
      UUID targetLocationId = UUID.randomUUID();
      Location targetLocation = new Location();
      targetLocation.setId(targetLocationId);

      InventoryItem item = newItem(10.0, 1L);
      when(inventoryItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));
      when(locationRepository.findById(targetLocationId)).thenReturn(Optional.of(targetLocation));
      when(inventoryItemRepository.save(any(InventoryItem.class)))
          .thenAnswer(inv -> inv.getArgument(0));

      service.bookOutInventoryItem(
          ITEM_ID,
          newDto(1.0, null, targetLocationId, /* type= */ null, null, null, 1L),
          OWNER_ID,
          false);

      ArgumentCaptor<InventoryItem> captor = ArgumentCaptor.forClass(InventoryItem.class);
      verify(inventoryItemRepository, org.mockito.Mockito.times(2)).save(captor.capture());
      assertSame(targetLocation, captor.getAllValues().get(0).getLocation());
    }

    @Test
    void nullType_withoutTargets_inferredAsDiscard() {
      InventoryItem item = newItem(10.0, 1L);
      when(inventoryItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));

      service.bookOutInventoryItem(
          ITEM_ID, newDto(1.0, null, null, /* type= */ null, null, null, 1L), OWNER_ID, false);

      // DISCARD -> source updated, no transfer-side new item created.
      verify(inventoryItemRepository, org.mockito.Mockito.times(1)).save(any(InventoryItem.class));
    }
  }

  // ---------------------------------------------------------------
  // SELL validation guards
  // ---------------------------------------------------------------

  @Nested
  class SellGuardTests {

    @Test
    void sell_withoutTerminal_throwsBadRequest() {
      InventoryItem item = newItem(10.0, 1L);
      when(inventoryItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));

      assertThrows(
          BadRequestException.class,
          () ->
              service.bookOutInventoryItem(
                  ITEM_ID,
                  newDto(
                      1.0, null, null, CheckoutType.SELL, /* terminal= */ null, BigDecimal.TEN, 1L),
                  OWNER_ID,
                  false));
    }

    @Test
    void sell_withBlankTerminal_throwsBadRequest() {
      InventoryItem item = newItem(10.0, 1L);
      when(inventoryItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));

      assertThrows(
          BadRequestException.class,
          () ->
              service.bookOutInventoryItem(
                  ITEM_ID,
                  newDto(1.0, null, null, CheckoutType.SELL, "   ", BigDecimal.TEN, 1L),
                  OWNER_ID,
                  false));
    }

    @Test
    void sell_withNullSellAmount_throwsBadRequest() {
      InventoryItem item = newItem(10.0, 1L);
      when(inventoryItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));

      assertThrows(
          BadRequestException.class,
          () ->
              service.bookOutInventoryItem(
                  ITEM_ID,
                  newDto(1.0, null, null, CheckoutType.SELL, "TDD-Aphorism", null, 1L),
                  OWNER_ID,
                  false));
    }

    @Test
    void sell_withNegativeSellAmount_throwsBadRequest() {
      InventoryItem item = newItem(10.0, 1L);
      when(inventoryItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));

      assertThrows(
          BadRequestException.class,
          () ->
              service.bookOutInventoryItem(
                  ITEM_ID,
                  newDto(1.0, null, null, CheckoutType.SELL, "TDD", BigDecimal.valueOf(-50), 1L),
                  OWNER_ID,
                  false));
    }
  }

  // ---------------------------------------------------------------
  // TRANSFER subtree
  // ---------------------------------------------------------------

  @Nested
  class TransferTests {

    @Test
    void targetUserNotFound_throws() {
      UUID targetUserId = UUID.randomUUID();
      InventoryItem item = newItem(10.0, 1L);
      when(inventoryItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));
      when(userRepository.findById(targetUserId)).thenReturn(Optional.empty());

      assertThrows(
          NotFoundException.class,
          () ->
              service.bookOutInventoryItem(
                  ITEM_ID,
                  newDto(1.0, targetUserId, null, CheckoutType.TRANSFER, null, null, 1L),
                  OWNER_ID,
                  false));
    }

    @Test
    void targetLocationNotFound_throws() {
      UUID targetLocationId = UUID.randomUUID();
      InventoryItem item = newItem(10.0, 1L);
      when(inventoryItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));
      when(locationRepository.findById(targetLocationId)).thenReturn(Optional.empty());

      assertThrows(
          NotFoundException.class,
          () ->
              service.bookOutInventoryItem(
                  ITEM_ID,
                  newDto(1.0, null, targetLocationId, CheckoutType.TRANSFER, null, null, 1L),
                  OWNER_ID,
                  false));
    }

    @Test
    void transferToSelfAndSameLocation_throwsBadRequest() {
      // BOTH targets resolve to the existing item's user + location -> no-op
      // transfer must be rejected.
      InventoryItem item = newItem(10.0, 1L);
      when(inventoryItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));

      BadRequestException ex =
          assertThrows(
              BadRequestException.class,
              () ->
                  service.bookOutInventoryItem(
                      ITEM_ID,
                      newDto(1.0, OWNER_ID, LOCATION_ID, CheckoutType.TRANSFER, null, null, 1L),
                      OWNER_ID,
                      false));
      assert ex.getMessage().toLowerCase().contains("change");
    }

    @Test
    void transferPartial_keepsSourceWithRemainingAmount() {
      // amount=3 out of 10 -> source keeps 7, new item gets 3.
      UUID targetUserId = UUID.randomUUID();
      User targetUser = new User();
      targetUser.setId(targetUserId);

      InventoryItem item = newItem(10.0, 1L);
      when(inventoryItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));
      when(userRepository.findById(targetUserId)).thenReturn(Optional.of(targetUser));
      when(inventoryItemRepository.save(any(InventoryItem.class)))
          .thenAnswer(inv -> inv.getArgument(0));

      service.bookOutInventoryItem(
          ITEM_ID,
          newDto(3.0, targetUserId, null, CheckoutType.TRANSFER, null, null, 1L),
          OWNER_ID,
          false);

      ArgumentCaptor<InventoryItem> captor = ArgumentCaptor.forClass(InventoryItem.class);
      verify(inventoryItemRepository, org.mockito.Mockito.times(2)).save(captor.capture());
      InventoryItem newItem = captor.getAllValues().get(0);
      InventoryItem source = captor.getAllValues().get(1);
      assertEquals(3.0, newItem.getAmount(), "new item gets the booked-out amount");
      assertEquals(7.0, source.getAmount(), "source keeps the remainder");
      assertSame(targetUser, newItem.getUser());
      verify(inventoryItemRepository, never()).delete(any());
    }

    @Test
    void transferFull_deletesSourceItem() {
      // amount == available -> remaining <= QUANTITY_EPSILON -> source deleted.
      UUID targetUserId = UUID.randomUUID();
      User targetUser = new User();
      targetUser.setId(targetUserId);

      InventoryItem item = newItem(5.0, 1L);
      when(inventoryItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));
      when(userRepository.findById(targetUserId)).thenReturn(Optional.of(targetUser));
      when(inventoryItemRepository.save(any(InventoryItem.class)))
          .thenAnswer(inv -> inv.getArgument(0));

      service.bookOutInventoryItem(
          ITEM_ID,
          newDto(5.0, targetUserId, null, CheckoutType.TRANSFER, null, null, 1L),
          OWNER_ID,
          false);

      verify(inventoryItemRepository).delete(item);
      // Only the new item save call (the source is deleted, not saved).
      verify(inventoryItemRepository, org.mockito.Mockito.times(1)).save(any(InventoryItem.class));
    }
  }

  // ---------------------------------------------------------------
  // SELL subtree — MissionFinanceEntry side effect
  // ---------------------------------------------------------------

  @Nested
  class SellTests {

    @Test
    void sellWithMission_createsMissionFinanceEntryIncome() {
      Mission mission = new Mission();
      mission.setId(UUID.randomUUID());
      MissionParticipant participant = new MissionParticipant();
      participant.setId(UUID.randomUUID());

      InventoryItem item = newItem(10.0, 1L);
      item.setMission(mission);

      when(inventoryItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));
      when(missionParticipantRepository.findByMissionIdAndUserId(mission.getId(), OWNER_ID))
          .thenReturn(Optional.of(participant));

      service.bookOutInventoryItem(
          ITEM_ID,
          newDto(1.0, null, null, CheckoutType.SELL, "TDD", BigDecimal.valueOf(500), 1L),
          OWNER_ID,
          false);

      ArgumentCaptor<MissionFinanceEntry> captor =
          ArgumentCaptor.forClass(MissionFinanceEntry.class);
      verify(missionFinanceEntryRepository).save(captor.capture());
      MissionFinanceEntry entry = captor.getValue();
      assertEquals(FinanceType.INCOME, entry.getType());
      assertEquals(BigDecimal.valueOf(500), entry.getAmount());
      assertSame(mission, entry.getMission());
      assertSame(participant, entry.getParticipant());
      assert entry.getNote().contains("Quantanium");
      assert entry.getNote().contains("TDD");
    }

    @Test
    void sellWithMission_butCallerNotParticipant_throwsBadRequest() {
      Mission mission = new Mission();
      mission.setId(UUID.randomUUID());
      InventoryItem item = newItem(10.0, 1L);
      item.setMission(mission);

      when(inventoryItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));
      when(missionParticipantRepository.findByMissionIdAndUserId(mission.getId(), OWNER_ID))
          .thenReturn(Optional.empty());

      assertThrows(
          BadRequestException.class,
          () ->
              service.bookOutInventoryItem(
                  ITEM_ID,
                  newDto(1.0, null, null, CheckoutType.SELL, "TDD", BigDecimal.TEN, 1L),
                  OWNER_ID,
                  false));
      verify(missionFinanceEntryRepository, never()).save(any());
    }

    @Test
    void sellWithoutMission_skipsFinanceEntry() {
      // No mission attached -> the SELL just decrements the item, no
      // MissionFinanceEntry side-effect.
      InventoryItem item = newItem(10.0, 1L);
      item.setMission(null);

      when(inventoryItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));

      service.bookOutInventoryItem(
          ITEM_ID,
          newDto(1.0, null, null, CheckoutType.SELL, "TDD", BigDecimal.TEN, 1L),
          OWNER_ID,
          false);

      verify(missionFinanceEntryRepository, never()).save(any());
      verify(missionParticipantRepository, never()).findByMissionIdAndUserId(any(), any());
    }
  }

  // ---------------------------------------------------------------
  // DISCARD / partial-vs-full deletion (default branch)
  // ---------------------------------------------------------------

  @Nested
  class DiscardTests {

    @Test
    void discardPartial_updatesSourceAmount() {
      InventoryItem item = newItem(10.0, 1L);
      when(inventoryItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));
      when(inventoryItemRepository.save(item)).thenReturn(item);

      InventoryItemDto result =
          service.bookOutInventoryItem(
              ITEM_ID,
              newDto(3.0, null, null, CheckoutType.DISCARD, null, null, 1L),
              OWNER_ID,
              false);

      assertEquals(7.0, item.getAmount());
      assertNotNull(result);
      verify(inventoryItemRepository, never()).delete(any());
    }

    @Test
    void discardFull_deletesItemAndReturnsNull() {
      InventoryItem item = newItem(5.0, 1L);
      when(inventoryItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));

      InventoryItemDto result =
          service.bookOutInventoryItem(
              ITEM_ID,
              newDto(5.0, null, null, CheckoutType.DISCARD, null, null, 1L),
              OWNER_ID,
              false);

      assertNull(result, "full discard returns null");
      verify(inventoryItemRepository).delete(item);
      verify(inventoryItemRepository, never()).save(any());
    }
  }

  // ---------------------------------------------------------------
  // helpers
  // ---------------------------------------------------------------

  private InventoryItem newItem(double amount, Long version) {
    InventoryItem item = new InventoryItem();
    item.setId(ITEM_ID);
    item.setAmount(amount);
    item.setQuality(500);
    item.setUser(owner);
    item.setLocation(location);
    item.setMaterial(material);
    item.setPersonal(false);
    item.setVersion(version);
    return item;
  }

  private static InventoryItemBookOutDto newDto(
      double amount,
      UUID targetUserId,
      UUID targetLocationId,
      CheckoutType type,
      String terminal,
      BigDecimal sellAmount,
      Long version) {
    return new InventoryItemBookOutDto(
        amount, targetUserId, targetLocationId, type, terminal, sellAmount, version, null);
  }

  // --- R5.d.g TRANSFER picker delegation -----------------------------------

  @Test
  void bookOutInventoryItem_transferWithTargetOwningOrgUnitId_routesThroughResolver() {
    UUID itemId = UUID.randomUUID();
    UUID targetUserId = UUID.randomUUID();
    UUID pickedOrgUnitId = UUID.randomUUID();

    de.greluc.krt.iri.basetool.backend.model.User owner =
        new de.greluc.krt.iri.basetool.backend.model.User();
    owner.setId(UUID.randomUUID());
    de.greluc.krt.iri.basetool.backend.model.User targetUser =
        new de.greluc.krt.iri.basetool.backend.model.User();
    targetUser.setId(targetUserId);
    de.greluc.krt.iri.basetool.backend.model.Squadron picked =
        new de.greluc.krt.iri.basetool.backend.model.Squadron();
    picked.setId(pickedOrgUnitId);

    de.greluc.krt.iri.basetool.backend.model.InventoryItem item =
        new de.greluc.krt.iri.basetool.backend.model.InventoryItem();
    item.setId(itemId);
    item.setVersion(1L);
    item.setAmount(10.0);
    item.setUser(owner);
    de.greluc.krt.iri.basetool.backend.model.Location loc =
        new de.greluc.krt.iri.basetool.backend.model.Location();
    loc.setId(UUID.randomUUID());
    item.setLocation(loc);
    item.setMaterial(new de.greluc.krt.iri.basetool.backend.model.Material());
    item.setPersonal(false);

    org.mockito.Mockito.when(inventoryItemRepository.findById(itemId))
        .thenReturn(java.util.Optional.of(item));
    org.mockito.Mockito.when(userRepository.findById(targetUserId))
        .thenReturn(java.util.Optional.of(targetUser));
    org.mockito.Mockito.when(
            ownerScopeService.resolveOrgUnitForPickerOutputNullable(targetUser, pickedOrgUnitId))
        .thenReturn(picked);
    org.mockito.ArgumentCaptor<de.greluc.krt.iri.basetool.backend.model.InventoryItem> captor =
        org.mockito.ArgumentCaptor.forClass(
            de.greluc.krt.iri.basetool.backend.model.InventoryItem.class);
    org.mockito.Mockito.when(
            inventoryItemRepository.save(
                any(de.greluc.krt.iri.basetool.backend.model.InventoryItem.class)))
        .thenAnswer(i -> i.getArguments()[0]);

    InventoryItemBookOutDto dto =
        new InventoryItemBookOutDto(
            5.0, targetUserId, null, CheckoutType.TRANSFER, null, null, 1L, pickedOrgUnitId);

    service.bookOutInventoryItem(itemId, dto, owner.getId(), false);

    org.mockito.Mockito.verify(inventoryItemRepository, org.mockito.Mockito.atLeastOnce())
        .save(captor.capture());
    java.util.List<de.greluc.krt.iri.basetool.backend.model.InventoryItem> saved =
        captor.getAllValues();
    de.greluc.krt.iri.basetool.backend.model.InventoryItem newRow =
        saved.stream()
            .filter(i -> i.getUser() == targetUser)
            .findFirst()
            .orElseThrow(() -> new AssertionError("expected a save for the new transfer row"));
    org.junit.jupiter.api.Assertions.assertSame(
        picked,
        newRow.getOwningOrgUnit(),
        "picker output must flow through resolveOrgUnitForPickerOutputNullable on the new row");
  }

  private static InventoryItemDto sentinelDto(Double amount) {
    return new InventoryItemDto(
        UUID.randomUUID(),
        null,
        null,
        null,
        500,
        amount,
        false,
        null,
        null,
        null,
        null,
        null,
        null,
        1L);
  }
}
