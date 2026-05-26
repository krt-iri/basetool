package de.greluc.krt.iri.basetool.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.backend.exception.BadRequestException;
import de.greluc.krt.iri.basetool.backend.exception.NotFoundException;
import de.greluc.krt.iri.basetool.backend.model.City;
import de.greluc.krt.iri.basetool.backend.model.Location;
import de.greluc.krt.iri.basetool.backend.model.Material;
import de.greluc.krt.iri.basetool.backend.model.MaterialType;
import de.greluc.krt.iri.basetool.backend.model.Mission;
import de.greluc.krt.iri.basetool.backend.model.RefineryGood;
import de.greluc.krt.iri.basetool.backend.model.RefineryOrder;
import de.greluc.krt.iri.basetool.backend.model.RefineryOrderStatus;
import de.greluc.krt.iri.basetool.backend.model.RefiningMethod;
import de.greluc.krt.iri.basetool.backend.model.SpaceStation;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.repository.InventoryItemRepository;
import de.greluc.krt.iri.basetool.backend.repository.JobOrderRepository;
import de.greluc.krt.iri.basetool.backend.repository.LocationRepository;
import de.greluc.krt.iri.basetool.backend.repository.MaterialRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionRepository;
import de.greluc.krt.iri.basetool.backend.repository.RefineryOrderRepository;
import de.greluc.krt.iri.basetool.backend.repository.RefiningMethodRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;

/**
 * Lifecycle / CRUD test for {@link RefineryOrderService}, complementing the existing {@code
 * RefineryOrderServiceTest} which focuses on {@code storeRefineryOrder}. Covers:
 *
 * <ul>
 *   <li>{@link RefineryOrderService#getRefineryOrder} not-found path.
 *   <li>{@link RefineryOrderService#getMyRefineryOrders} (both overloads and with/without status
 *       filter).
 *   <li>{@link RefineryOrderService#getAllRefineryOrders} (both overloads and with/without status
 *       filter).
 *   <li>{@link RefineryOrderService#getMissionRefineryOrders} (both overloads).
 *   <li>{@link RefineryOrderService#createRefineryOrder} — every validation branch (User / Location
 *       / Mission / RefiningMethod lookups, location-must-have-refinery, goods validation including
 *       RAW-input-only, output-must-match-refined-of-input, output fallback chain), plus the {@code
 *       zeroToNull} normalisation of the optional money fields.
 *   <li>{@link RefineryOrderService#updateRefineryOrder} — version-check (which fires
 *       <em>before</em> the owner check), owner-check (non-logistician), logistician bypass,
 *       partial-update semantics (Location / Mission / RefiningMethod set or cleared), goods
 *       replacement.
 *   <li>{@link RefineryOrderService#deleteRefineryOrder} — actually a status flip to CANCELED, plus
 *       owner-check / logistician bypass.
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class RefineryOrderServiceLifecycleTest {

  @Mock private RefineryOrderRepository refineryOrderRepository;
  @Mock private UserRepository userRepository;
  @Mock private LocationRepository locationRepository;
  @Mock private MissionRepository missionRepository;
  @Mock private RefiningMethodRepository refiningMethodRepository;
  @Mock private MaterialRepository materialRepository;
  @Mock private InventoryItemRepository inventoryItemRepository;
  @Mock private JobOrderRepository jobOrderRepository;
  @Mock private OwnerScopeService ownerScopeService;

  @InjectMocks private RefineryOrderService service;

  private static final UUID ORDER_ID = UUID.randomUUID();
  private static final UUID OWNER_ID = UUID.randomUUID();
  private static final UUID OTHER_USER_ID = UUID.randomUUID();
  private static final UUID LOCATION_ID = UUID.randomUUID();
  private static final UUID MISSION_ID = UUID.randomUUID();
  private static final UUID METHOD_ID = UUID.randomUUID();
  private static final UUID INPUT_MATERIAL_ID = UUID.randomUUID();
  private static final UUID OUTPUT_MATERIAL_ID = UUID.randomUUID();

  private User owner;
  private Location refineryLocation;
  private Material rawInput;

  @BeforeEach
  void setUpEntities() {
    owner = new User();
    owner.setId(OWNER_ID);
    owner.setUsername("alice");

    refineryLocation = new Location();
    refineryLocation.setId(LOCATION_ID);
    refineryLocation.setName("ARC-L1");
    SpaceStation station = new SpaceStation();
    station.setHasRefinery(true);
    refineryLocation.setSpaceStation(station);

    rawInput = new Material();
    rawInput.setId(INPUT_MATERIAL_ID);
    rawInput.setName("Quantanium");
    rawInput.setType(MaterialType.RAW);
  }

  // --------------------------------------------------------------
  // getRefineryOrder
  // --------------------------------------------------------------

  @Nested
  class GetRefineryOrderTests {

    @Test
    void returnsOrder_whenPresent() {
      RefineryOrder order = new RefineryOrder();
      order.setId(ORDER_ID);
      when(refineryOrderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

      assertSame(order, service.getRefineryOrder(ORDER_ID));
    }

    @Test
    void throwsNotFound_whenAbsent() {
      when(refineryOrderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

      assertThrows(NotFoundException.class, () -> service.getRefineryOrder(ORDER_ID));
    }
  }

  // --------------------------------------------------------------
  // list / page methods
  // --------------------------------------------------------------

  @Nested
  class ListAndPageTests {

    private final Pageable pageable = PageRequest.of(0, 10);

    @Test
    void getMyRefineryOrders_withEmptyStatusList_callsOwnerOnlyVariant() {
      Page<RefineryOrder> page = new PageImpl<>(List.of(new RefineryOrder()));
      when(refineryOrderRepository.findByOwnerId(OWNER_ID, pageable)).thenReturn(page);

      assertEquals(
          1, service.getMyRefineryOrders(OWNER_ID, List.of(), pageable).getTotalElements());
      verify(refineryOrderRepository, never()).findByOwnerIdAndStatusIn(any(), any(), any());
    }

    @Test
    void getMyRefineryOrders_withNullStatusList_callsOwnerOnlyVariant() {
      Page<RefineryOrder> page = new PageImpl<>(List.of(new RefineryOrder()));
      when(refineryOrderRepository.findByOwnerId(OWNER_ID, pageable)).thenReturn(page);

      assertEquals(1, service.getMyRefineryOrders(OWNER_ID, null, pageable).getTotalElements());
    }

    @Test
    void getMyRefineryOrders_withStatusList_routesToStatusFilter() {
      Page<RefineryOrder> page = new PageImpl<>(List.of(new RefineryOrder()));
      when(refineryOrderRepository.findByOwnerIdAndStatusIn(
              OWNER_ID, List.of(RefineryOrderStatus.OPEN), pageable))
          .thenReturn(page);

      assertEquals(
          1,
          service
              .getMyRefineryOrders(OWNER_ID, List.of(RefineryOrderStatus.OPEN), pageable)
              .getTotalElements());
      verify(refineryOrderRepository, never()).findByOwnerId(any(), any());
    }

    @Test
    void getMyRefineryOrders_secondOverload_delegatesToOwnerVariant() {
      Page<RefineryOrder> page = new PageImpl<>(List.of(new RefineryOrder()));
      when(refineryOrderRepository.findByOwnerId(OWNER_ID, pageable)).thenReturn(page);

      assertEquals(1, service.getMyRefineryOrders(OWNER_ID, pageable).getTotalElements());
    }

    @Test
    void getAllRefineryOrders_emptyStatusList_callsFindAll() {
      Page<RefineryOrder> page = new PageImpl<>(List.of(new RefineryOrder()));
      // After Phase 3 the admin "all orders" list goes through the squadron-scoped variant; the
      // test class has no squadron stub so currentScopePredicate() resolves to the admin-all
      // shape and the service forwards adminAllScope=true / no IDs.
      when(ownerScopeService.currentScopePredicate())
          .thenReturn(new ScopePredicate(true, null, java.util.Set.of()));
      when(refineryOrderRepository.findAllScoped(true, null, java.util.Set.of(), pageable))
          .thenReturn(page);

      assertEquals(1, service.getAllRefineryOrders(List.of(), pageable).getTotalElements());
      verify(refineryOrderRepository, never())
          .findByStatusInScoped(any(), anyBoolean(), any(), any(), any());
    }

    @Test
    void getAllRefineryOrders_withStatuses_callsFindByStatusIn() {
      Page<RefineryOrder> page = new PageImpl<>(List.of(new RefineryOrder()));
      when(ownerScopeService.currentScopePredicate())
          .thenReturn(new ScopePredicate(true, null, java.util.Set.of()));
      when(refineryOrderRepository.findByStatusInScoped(
              List.of(RefineryOrderStatus.COMPLETED), true, null, java.util.Set.of(), pageable))
          .thenReturn(page);

      assertEquals(
          1,
          service
              .getAllRefineryOrders(List.of(RefineryOrderStatus.COMPLETED), pageable)
              .getTotalElements());
    }

    @Test
    void getAllRefineryOrders_secondOverload_callsFindAll() {
      Page<RefineryOrder> page = new PageImpl<>(List.of(new RefineryOrder()));
      when(ownerScopeService.currentScopePredicate())
          .thenReturn(new ScopePredicate(true, null, java.util.Set.of()));
      when(refineryOrderRepository.findAllScoped(true, null, java.util.Set.of(), pageable))
          .thenReturn(page);

      assertEquals(1, service.getAllRefineryOrders(pageable).getTotalElements());
    }

    @Test
    void getMissionRefineryOrders_unfiltered_delegatesToFindByMissionId() {
      RefineryOrder o = new RefineryOrder();
      when(refineryOrderRepository.findByMissionId(MISSION_ID)).thenReturn(List.of(o));

      assertEquals(List.of(o), service.getMissionRefineryOrders(MISSION_ID));
    }

    @Test
    void getMissionRefineryOrders_filteredByOwner_delegatesToCombinedQuery() {
      RefineryOrder o = new RefineryOrder();
      when(refineryOrderRepository.findByMissionIdAndOwnerId(MISSION_ID, OWNER_ID))
          .thenReturn(List.of(o));

      assertEquals(List.of(o), service.getMissionRefineryOrders(MISSION_ID, OWNER_ID));
    }
  }

  // --------------------------------------------------------------
  // createRefineryOrder
  // --------------------------------------------------------------

  @Nested
  class CreateRefineryOrderTests {

    @Test
    void throwsNotFound_whenUserDoesNotExist() {
      when(userRepository.findById(OWNER_ID)).thenReturn(Optional.empty());

      RefineryOrder incoming = new RefineryOrder();
      incoming.setLocation(refineryLocation);

      assertThrows(
          NotFoundException.class, () -> service.createRefineryOrder(OWNER_ID, incoming, null));
      verify(refineryOrderRepository, never()).save(any());
    }

    @Test
    void throwsBadRequest_whenLocationIsNull() {
      when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));

      assertThrows(
          BadRequestException.class,
          () -> service.createRefineryOrder(OWNER_ID, new RefineryOrder(), null));
      verify(refineryOrderRepository, never()).save(any());
    }

    @Test
    void throwsBadRequest_whenLocationHasNullId() {
      when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));

      RefineryOrder incoming = new RefineryOrder();
      Location withoutId = new Location();
      withoutId.setId(null);
      incoming.setLocation(withoutId);

      assertThrows(
          BadRequestException.class, () -> service.createRefineryOrder(OWNER_ID, incoming, null));
    }

    @Test
    void throwsNotFound_whenLocationLookupFails() {
      when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
      when(locationRepository.findById(LOCATION_ID)).thenReturn(Optional.empty());

      assertThrows(
          NotFoundException.class,
          () -> service.createRefineryOrder(OWNER_ID, freshOrderWithLocation(), null));
    }

    @Test
    void throwsIllegalArgument_whenLocationHasNoRefinery() {
      Location notARefinery = new Location();
      notARefinery.setId(LOCATION_ID);
      // No city, no space station -> neither flag set.

      when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
      when(locationRepository.findById(LOCATION_ID)).thenReturn(Optional.of(notARefinery));

      assertThrows(
          IllegalArgumentException.class,
          () -> service.createRefineryOrder(OWNER_ID, freshOrderWithLocation(), null));
    }

    @Test
    void locationWithCityRefinery_isAccepted() {
      Location cityLoc = new Location();
      cityLoc.setId(LOCATION_ID);
      City city = new City();
      city.setHasRefinery(true);
      cityLoc.setCity(city);

      when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
      when(locationRepository.findById(LOCATION_ID)).thenReturn(Optional.of(cityLoc));
      when(refineryOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      RefineryOrder result = service.createRefineryOrder(OWNER_ID, freshOrderWithLocation(), null);

      assertSame(cityLoc, result.getLocation());
    }

    @Test
    void locationWithSpaceStationRefinery_isAccepted() {
      // refineryLocation seed already has a space-station-with-refinery.
      stubUserAndLocation();
      when(refineryOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      RefineryOrder result = service.createRefineryOrder(OWNER_ID, freshOrderWithLocation(), null);

      assertSame(refineryLocation, result.getLocation());
    }

    @Test
    void locationWithCity_butHasRefineryFalse_isRejected() {
      Location cityLoc = new Location();
      cityLoc.setId(LOCATION_ID);
      City city = new City();
      city.setHasRefinery(false);
      cityLoc.setCity(city);

      when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
      when(locationRepository.findById(LOCATION_ID)).thenReturn(Optional.of(cityLoc));

      assertThrows(
          IllegalArgumentException.class,
          () -> service.createRefineryOrder(OWNER_ID, freshOrderWithLocation(), null));
    }

    @Test
    void missionLookupFails_throwsNotFound() {
      stubUserAndLocation();
      Mission missionRef = new Mission();
      missionRef.setId(MISSION_ID);

      when(missionRepository.findById(MISSION_ID)).thenReturn(Optional.empty());

      RefineryOrder incoming = freshOrderWithLocation();
      incoming.setMission(missionRef);

      assertThrows(
          NotFoundException.class, () -> service.createRefineryOrder(OWNER_ID, incoming, null));
    }

    @Test
    void missionNull_setsOrderMissionToNull() {
      stubUserAndLocation();
      when(refineryOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      // Order with no mission attached at all.
      RefineryOrder incoming = freshOrderWithLocation();
      incoming.setMission(null);

      RefineryOrder result = service.createRefineryOrder(OWNER_ID, incoming, null);
      assertNull(result.getMission());
    }

    @Test
    void refiningMethodLookupFails_throwsNotFound() {
      stubUserAndLocation();
      RefiningMethod methodRef = new RefiningMethod();
      methodRef.setId(METHOD_ID);

      when(refiningMethodRepository.findById(METHOD_ID)).thenReturn(Optional.empty());

      RefineryOrder incoming = freshOrderWithLocation();
      incoming.setRefiningMethod(methodRef);

      assertThrows(
          NotFoundException.class, () -> service.createRefineryOrder(OWNER_ID, incoming, null));
    }

    @Test
    void goodWithNullInputMaterial_throwsBadRequest() {
      stubUserAndLocation();
      RefineryGood bad = new RefineryGood();
      bad.setInputMaterial(null);

      RefineryOrder incoming = freshOrderWithLocation();
      incoming.setGoods(new HashSet<>(Set.of(bad)));

      assertThrows(
          BadRequestException.class, () -> service.createRefineryOrder(OWNER_ID, incoming, null));
    }

    @Test
    void goodWithNonRawInput_throwsIllegalArgument() {
      Material refined = new Material();
      refined.setId(INPUT_MATERIAL_ID);
      refined.setType(MaterialType.REFINED);
      refined.setIsManualRawMaterial(false);
      refined.setName("RefinedOre");

      stubUserAndLocation();
      when(materialRepository.findById(INPUT_MATERIAL_ID)).thenReturn(Optional.of(refined));

      RefineryGood bad = newGoodWithInput(INPUT_MATERIAL_ID);
      RefineryOrder incoming = freshOrderWithLocation();
      incoming.setGoods(new HashSet<>(Set.of(bad)));

      IllegalArgumentException ex =
          assertThrows(
              IllegalArgumentException.class,
              () -> service.createRefineryOrder(OWNER_ID, incoming, null));
      assert ex.getMessage().contains("RAW");
    }

    @Test
    void goodWithNonRawInputButManualRawFlagTrue_isAccepted() {
      // Edge: type is REFINED but the operator manually flagged it as
      // raw-usable -> creation must succeed.
      Material flagged = new Material();
      flagged.setId(INPUT_MATERIAL_ID);
      flagged.setType(MaterialType.REFINED);
      flagged.setIsManualRawMaterial(true);

      stubUserAndLocation();
      when(materialRepository.findById(INPUT_MATERIAL_ID)).thenReturn(Optional.of(flagged));
      when(refineryOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      RefineryGood good = newGoodWithInput(INPUT_MATERIAL_ID);
      RefineryOrder incoming = freshOrderWithLocation();
      incoming.setGoods(new HashSet<>(Set.of(good)));

      service.createRefineryOrder(OWNER_ID, incoming, null);

      assertSame(flagged, good.getInputMaterial());
    }

    @Test
    void goodWithoutOutputMaterial_andNoRefinedMaterialOnInput_fallsBackToInputItself() {
      // rawInput.refinedMaterial == null -> output := input
      stubUserAndLocation();
      when(materialRepository.findById(INPUT_MATERIAL_ID)).thenReturn(Optional.of(rawInput));
      when(refineryOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      RefineryGood good = newGoodWithInput(INPUT_MATERIAL_ID);
      good.setOutputMaterial(null);
      RefineryOrder incoming = freshOrderWithLocation();
      incoming.setGoods(new HashSet<>(Set.of(good)));

      service.createRefineryOrder(OWNER_ID, incoming, null);
      assertSame(rawInput, good.getOutputMaterial());
    }

    @Test
    void goodWithoutOutputMaterial_butInputHasRefinedMaterial_usesRefined() {
      Material refinedOf = new Material();
      refinedOf.setId(OUTPUT_MATERIAL_ID);
      refinedOf.setType(MaterialType.REFINED);
      rawInput.setRefinedMaterial(refinedOf);

      stubUserAndLocation();
      when(materialRepository.findById(INPUT_MATERIAL_ID)).thenReturn(Optional.of(rawInput));
      when(refineryOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      RefineryGood good = newGoodWithInput(INPUT_MATERIAL_ID);
      good.setOutputMaterial(null);
      RefineryOrder incoming = freshOrderWithLocation();
      incoming.setGoods(new HashSet<>(Set.of(good)));

      service.createRefineryOrder(OWNER_ID, incoming, null);
      assertSame(refinedOf, good.getOutputMaterial());
    }

    @Test
    void goodWithExplicitOutputMatchingRefinedOfInput_isAccepted() {
      Material refinedOf = new Material();
      refinedOf.setId(OUTPUT_MATERIAL_ID);
      rawInput.setRefinedMaterial(refinedOf);

      stubUserAndLocation();
      when(materialRepository.findById(INPUT_MATERIAL_ID)).thenReturn(Optional.of(rawInput));
      when(materialRepository.findById(OUTPUT_MATERIAL_ID)).thenReturn(Optional.of(refinedOf));
      when(refineryOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      RefineryGood good = newGoodWithInput(INPUT_MATERIAL_ID);
      Material outputStub = new Material();
      outputStub.setId(OUTPUT_MATERIAL_ID);
      good.setOutputMaterial(outputStub);

      RefineryOrder incoming = freshOrderWithLocation();
      incoming.setGoods(new HashSet<>(Set.of(good)));

      service.createRefineryOrder(OWNER_ID, incoming, null);
      assertSame(refinedOf, good.getOutputMaterial());
    }

    @Test
    void goodWithExplicitOutputThatDoesNotMatchRefined_throwsIllegalArgument() {
      // Input has refinedMaterial=A, but the good claims output=B -> rejected.
      Material refinedOf = new Material();
      refinedOf.setId(OUTPUT_MATERIAL_ID);
      rawInput.setRefinedMaterial(refinedOf);

      UUID otherOutputId = UUID.randomUUID();
      Material wrongOutput = new Material();
      wrongOutput.setId(otherOutputId);

      stubUserAndLocation();
      when(materialRepository.findById(INPUT_MATERIAL_ID)).thenReturn(Optional.of(rawInput));
      when(materialRepository.findById(otherOutputId)).thenReturn(Optional.of(wrongOutput));

      RefineryGood good = newGoodWithInput(INPUT_MATERIAL_ID);
      Material outputStub = new Material();
      outputStub.setId(otherOutputId);
      good.setOutputMaterial(outputStub);

      RefineryOrder incoming = freshOrderWithLocation();
      incoming.setGoods(new HashSet<>(Set.of(good)));

      assertThrows(
          IllegalArgumentException.class,
          () -> service.createRefineryOrder(OWNER_ID, incoming, null));
    }

    @Test
    void outputMaterialLookupFails_throwsNotFound() {
      stubUserAndLocation();
      when(materialRepository.findById(INPUT_MATERIAL_ID)).thenReturn(Optional.of(rawInput));
      when(materialRepository.findById(OUTPUT_MATERIAL_ID)).thenReturn(Optional.empty());

      RefineryGood good = newGoodWithInput(INPUT_MATERIAL_ID);
      Material outputStub = new Material();
      outputStub.setId(OUTPUT_MATERIAL_ID);
      good.setOutputMaterial(outputStub);

      RefineryOrder incoming = freshOrderWithLocation();
      incoming.setGoods(new HashSet<>(Set.of(good)));

      assertThrows(
          NotFoundException.class, () -> service.createRefineryOrder(OWNER_ID, incoming, null));
    }

    @Test
    void startedAtNull_defaultsToInstantNow() {
      stubUserAndLocation();
      when(refineryOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      Instant before = Instant.now();
      RefineryOrder result = service.createRefineryOrder(OWNER_ID, freshOrderWithLocation(), null);
      Instant after = Instant.now();

      assertNotNull(result.getStartedAt());
      assert !result.getStartedAt().isBefore(before);
      assert !result.getStartedAt().isAfter(after);
    }

    @Test
    void startedAtProvided_isPreserved() {
      Instant explicit = Instant.parse("2026-01-01T00:00:00Z");
      stubUserAndLocation();
      when(refineryOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      RefineryOrder incoming = freshOrderWithLocation();
      incoming.setStartedAt(explicit);

      RefineryOrder result = service.createRefineryOrder(OWNER_ID, incoming, null);
      assertEquals(explicit, result.getStartedAt());
    }

    @Test
    void zeroMoneyFields_areNormalisedToNull() {
      stubUserAndLocation();
      when(refineryOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      RefineryOrder incoming = freshOrderWithLocation();
      incoming.setExpenses(0.0);
      incoming.setOtherExpenses(0.0);
      incoming.setOreSales(0.0);

      RefineryOrder result = service.createRefineryOrder(OWNER_ID, incoming, null);
      assertNull(result.getExpenses());
      assertNull(result.getOtherExpenses());
      assertNull(result.getOreSales());
    }

    @Test
    void positiveMoneyFields_arePreserved() {
      stubUserAndLocation();
      when(refineryOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      RefineryOrder incoming = freshOrderWithLocation();
      incoming.setExpenses(100.0);
      incoming.setOtherExpenses(50.0);
      incoming.setOreSales(1000.0);

      RefineryOrder result = service.createRefineryOrder(OWNER_ID, incoming, null);
      assertEquals(100.0, result.getExpenses());
      assertEquals(50.0, result.getOtherExpenses());
      assertEquals(1000.0, result.getOreSales());
    }
  }

  // --------------------------------------------------------------
  // updateRefineryOrder
  // --------------------------------------------------------------

  @Nested
  class UpdateRefineryOrderTests {

    @Test
    void throwsOptimisticLockingFailure_whenVersionMismatch() {
      RefineryOrder existing = newSavedOrder();
      existing.setVersion(5L);

      when(refineryOrderRepository.findById(ORDER_ID)).thenReturn(Optional.of(existing));

      RefineryOrder incoming = new RefineryOrder();
      incoming.setVersion(2L);

      assertThrows(
          ObjectOptimisticLockingFailureException.class,
          () -> service.updateRefineryOrder(OWNER_ID, ORDER_ID, incoming, false));
    }

    @Test
    void nullVersion_bypassesOptimisticCheck() {
      RefineryOrder existing = newSavedOrder();
      existing.setVersion(5L);

      when(refineryOrderRepository.findById(ORDER_ID)).thenReturn(Optional.of(existing));
      when(refineryOrderRepository.save(existing)).thenReturn(existing);

      // No version on the incoming DTO -> skip the explicit check; Hibernate's
      // UPDATE-WHERE-VERSION fallback would still catch a stale write in prod.
      service.updateRefineryOrder(OWNER_ID, ORDER_ID, new RefineryOrder(), false);

      verify(refineryOrderRepository).save(existing);
    }

    @Test
    void throwsAccessDenied_whenNonLogisticianIsNotOwner() {
      RefineryOrder existing = newSavedOrder();
      when(refineryOrderRepository.findById(ORDER_ID)).thenReturn(Optional.of(existing));

      assertThrows(
          AccessDeniedException.class,
          () -> service.updateRefineryOrder(OTHER_USER_ID, ORDER_ID, new RefineryOrder(), false));
    }

    @Test
    void throwsAccessDenied_whenOwnerIsNull_andNotLogistician() {
      RefineryOrder existing = newSavedOrder();
      existing.setOwner(null);
      when(refineryOrderRepository.findById(ORDER_ID)).thenReturn(Optional.of(existing));

      assertThrows(
          AccessDeniedException.class,
          () -> service.updateRefineryOrder(OWNER_ID, ORDER_ID, new RefineryOrder(), false));
    }

    @Test
    void logisticianCanUpdateAnyOrder() {
      RefineryOrder existing = newSavedOrder();
      when(refineryOrderRepository.findById(ORDER_ID)).thenReturn(Optional.of(existing));
      when(refineryOrderRepository.save(existing)).thenReturn(existing);

      service.updateRefineryOrder(OTHER_USER_ID, ORDER_ID, new RefineryOrder(), true);

      verify(refineryOrderRepository).save(existing);
    }

    @Test
    void locationProvided_isLookedUpAndValidated() {
      RefineryOrder existing = newSavedOrder();
      when(refineryOrderRepository.findById(ORDER_ID)).thenReturn(Optional.of(existing));
      when(locationRepository.findById(LOCATION_ID)).thenReturn(Optional.of(refineryLocation));
      when(refineryOrderRepository.save(existing)).thenReturn(existing);

      RefineryOrder incoming = new RefineryOrder();
      incoming.setLocation(refineryLocation);

      service.updateRefineryOrder(OWNER_ID, ORDER_ID, incoming, false);

      assertSame(refineryLocation, existing.getLocation());
    }

    @Test
    void missionExplicitlySetToNull_clearsExistingMission() {
      RefineryOrder existing = newSavedOrder();
      existing.setMission(new Mission());
      when(refineryOrderRepository.findById(ORDER_ID)).thenReturn(Optional.of(existing));
      when(refineryOrderRepository.save(existing)).thenReturn(existing);

      RefineryOrder incoming = new RefineryOrder();
      incoming.setMission(null);
      // location and status untouched

      service.updateRefineryOrder(OWNER_ID, ORDER_ID, incoming, false);
      assertNull(existing.getMission());
    }

    @Test
    void refiningMethodExplicitlySetToNull_clearsExisting() {
      RefineryOrder existing = newSavedOrder();
      existing.setRefiningMethod(new RefiningMethod());
      when(refineryOrderRepository.findById(ORDER_ID)).thenReturn(Optional.of(existing));
      when(refineryOrderRepository.save(existing)).thenReturn(existing);

      service.updateRefineryOrder(OWNER_ID, ORDER_ID, new RefineryOrder(), false);
      assertNull(existing.getRefiningMethod());
    }

    @Test
    void statusProvided_isApplied() {
      RefineryOrder existing = newSavedOrder();
      existing.setStatus(RefineryOrderStatus.OPEN);
      when(refineryOrderRepository.findById(ORDER_ID)).thenReturn(Optional.of(existing));
      when(refineryOrderRepository.save(existing)).thenReturn(existing);

      RefineryOrder incoming = new RefineryOrder();
      incoming.setStatus(RefineryOrderStatus.IN_PROGRESS);

      service.updateRefineryOrder(OWNER_ID, ORDER_ID, incoming, false);
      assertEquals(RefineryOrderStatus.IN_PROGRESS, existing.getStatus());
    }

    @Test
    void statusNullInIncoming_preservesExistingStatus() {
      RefineryOrder existing = newSavedOrder();
      existing.setStatus(RefineryOrderStatus.OPEN);
      when(refineryOrderRepository.findById(ORDER_ID)).thenReturn(Optional.of(existing));
      when(refineryOrderRepository.save(existing)).thenReturn(existing);

      service.updateRefineryOrder(OWNER_ID, ORDER_ID, new RefineryOrder(), false);
      assertEquals(
          RefineryOrderStatus.OPEN,
          existing.getStatus(),
          "null incoming status must NOT overwrite the existing one");
    }

    @Test
    void zeroMoneyFields_normalisedToNullOnUpdate() {
      RefineryOrder existing = newSavedOrder();
      when(refineryOrderRepository.findById(ORDER_ID)).thenReturn(Optional.of(existing));
      when(refineryOrderRepository.save(existing)).thenReturn(existing);

      RefineryOrder incoming = new RefineryOrder();
      incoming.setExpenses(0.0);
      incoming.setOtherExpenses(0.0);
      incoming.setOreSales(0.0);

      service.updateRefineryOrder(OWNER_ID, ORDER_ID, incoming, false);
      assertNull(existing.getExpenses());
      assertNull(existing.getOtherExpenses());
      assertNull(existing.getOreSales());
    }

    @Test
    void goodsReplacement_clearsAndReadsAllValidatedGoods() {
      // Existing order has one good; the update should clear it and re-add the
      // good(s) from the DTO. Verified via the captor on .save.
      RefineryGood preexisting = new RefineryGood();
      RefineryOrder existing = newSavedOrder();
      existing.setGoods(new HashSet<>(Set.of(preexisting)));

      when(refineryOrderRepository.findById(ORDER_ID)).thenReturn(Optional.of(existing));
      when(materialRepository.findById(INPUT_MATERIAL_ID)).thenReturn(Optional.of(rawInput));
      when(refineryOrderRepository.save(existing)).thenReturn(existing);

      RefineryGood replacement = newGoodWithInput(INPUT_MATERIAL_ID);
      RefineryOrder incoming = new RefineryOrder();
      incoming.setGoods(new HashSet<>(Set.of(replacement)));

      service.updateRefineryOrder(OWNER_ID, ORDER_ID, incoming, false);

      assertEquals(1, existing.getGoods().size());
      assert !existing.getGoods().contains(preexisting);
    }
  }

  // --------------------------------------------------------------
  // deleteRefineryOrder (actually cancels)
  // --------------------------------------------------------------

  @Nested
  class DeleteRefineryOrderTests {

    @Test
    void throwsNotFound_whenOrderDoesNotExist() {
      when(refineryOrderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

      assertThrows(
          NotFoundException.class, () -> service.deleteRefineryOrder(OWNER_ID, ORDER_ID, false));
    }

    @Test
    void throwsAccessDenied_whenNonLogisticianIsNotOwner() {
      RefineryOrder existing = newSavedOrder();
      when(refineryOrderRepository.findById(ORDER_ID)).thenReturn(Optional.of(existing));

      assertThrows(
          AccessDeniedException.class,
          () -> service.deleteRefineryOrder(OTHER_USER_ID, ORDER_ID, false));
      verify(refineryOrderRepository, never()).save(any());
    }

    @Test
    void ownerCanCancel_orderTransitionsToCanceled() {
      RefineryOrder existing = newSavedOrder();
      when(refineryOrderRepository.findById(ORDER_ID)).thenReturn(Optional.of(existing));

      service.deleteRefineryOrder(OWNER_ID, ORDER_ID, false);

      assertEquals(RefineryOrderStatus.CANCELED, existing.getStatus());
      verify(refineryOrderRepository).save(existing);
    }

    @Test
    void logisticianCanCancelAnyOrder() {
      RefineryOrder existing = newSavedOrder();
      when(refineryOrderRepository.findById(ORDER_ID)).thenReturn(Optional.of(existing));

      service.deleteRefineryOrder(OTHER_USER_ID, ORDER_ID, true);

      assertEquals(RefineryOrderStatus.CANCELED, existing.getStatus());
      verify(refineryOrderRepository).save(existing);
    }
  }

  // --------------------------------------------------------------
  // Helpers
  // --------------------------------------------------------------

  private RefineryOrder newSavedOrder() {
    RefineryOrder o = new RefineryOrder();
    o.setId(ORDER_ID);
    o.setVersion(1L);
    o.setOwner(owner);
    o.setStatus(RefineryOrderStatus.OPEN);
    o.setGoods(new HashSet<>());
    return o;
  }

  private RefineryOrder freshOrderWithLocation() {
    RefineryOrder o = new RefineryOrder();
    o.setLocation(refineryLocation);
    return o;
  }

  private void stubUserAndLocation() {
    when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
    when(locationRepository.findById(LOCATION_ID)).thenReturn(Optional.of(refineryLocation));
  }

  private static RefineryGood newGoodWithInput(UUID inputMaterialId) {
    RefineryGood good = new RefineryGood();
    Material inputStub = new Material();
    inputStub.setId(inputMaterialId);
    good.setInputMaterial(inputStub);
    return good;
  }
}
