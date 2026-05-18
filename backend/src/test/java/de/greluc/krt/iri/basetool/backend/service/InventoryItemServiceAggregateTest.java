package de.greluc.krt.iri.basetool.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.backend.mapper.InventoryItemMapper;
import de.greluc.krt.iri.basetool.backend.mapper.MaterialMapper;
import de.greluc.krt.iri.basetool.backend.model.InventoryItem;
import de.greluc.krt.iri.basetool.backend.model.QuantityType;
import de.greluc.krt.iri.basetool.backend.model.dto.GroupedInventoryDto;
import de.greluc.krt.iri.basetool.backend.model.dto.InventoryItemDto;
import de.greluc.krt.iri.basetool.backend.model.dto.LocationReferenceDto;
import de.greluc.krt.iri.basetool.backend.model.dto.MaterialReferenceDto;
import de.greluc.krt.iri.basetool.backend.repository.InventoryItemRepository;
import de.greluc.krt.iri.basetool.backend.repository.JobOrderRepository;
import de.greluc.krt.iri.basetool.backend.repository.LocationRepository;
import de.greluc.krt.iri.basetool.backend.repository.MaterialRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionFinanceEntryRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionParticipantRepository;
import de.greluc.krt.iri.basetool.backend.repository.MissionRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/**
 * Coverage for {@link InventoryItemService#getAllAggregatedInventory} and its private helper {@code
 * aggregateInventoryItems(...)}. The main test file {@code InventoryItemServiceTest} already covers
 * create / update / book-out flows; this sibling focuses specifically on the global-inventory
 * aggregation used by the squadron-wide stock view, which the coverage analysis flagged as one of
 * the largest single uncovered methods in the service package.
 *
 * <p>Branches exercised:
 *
 * <ul>
 *   <li>filter routing: each of {@code materialIds} / {@code jobOrderIds} / {@code missionIds} can
 *       be null / empty / populated, and the corresponding {@code hasX} boolean must flip
 *       accordingly while the original list is or isn't passed through;
 *   <li>{@code aggregateInventoryItems}: empty input -&gt; empty result; weighted-average quality
 *       math; max-quality tracking; null amount / null quality / null location tolerated in both
 *       sort and aggregation (regression — see {@code nullQuality_coercedToZeroInSortAndLoop});
 *       total-zero div-by-zero guard; intra-material sort (quality DESC, location name ASC, amount
 *       DESC); inter-material sort (alphabetic by name).
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class InventoryItemServiceAggregateTest {

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
  @Mock private SquadronScopeService squadronScopeService;

  @InjectMocks private InventoryItemService service;

  // ---------------------------------------------------------------------
  // filter routing
  // ---------------------------------------------------------------------

  @Nested
  class FilterRoutingTests {

    @Test
    void allFiltersNull_passesFalseFlagsAndNullLists() {
      stubFindGlobalReturning(List.of());

      service.getAllAggregatedInventory(null, null);

      verify(inventoryItemRepository)
          .findGlobalByFilters(
              eq(false),
              isNull(),
              isNull(),
              eq(false),
              isNull(),
              eq(false),
              isNull(),
              isNull(),
              any(Pageable.class));
    }

    @Test
    void emptyMaterialList_treatedAsNoFilter() {
      stubFindGlobalReturning(List.of());

      service.getAllAggregatedInventory(List.of(), null);

      verify(inventoryItemRepository)
          .findGlobalByFilters(
              eq(false),
              isNull(),
              isNull(),
              eq(false),
              isNull(),
              eq(false),
              isNull(),
              isNull(),
              any(Pageable.class));
    }

    @Test
    void nonEmptyMaterialList_setsFlagAndPassesIds() {
      UUID matId = UUID.randomUUID();
      stubFindGlobalReturning(List.of());

      service.getAllAggregatedInventory(List.of(matId), null);

      verify(inventoryItemRepository)
          .findGlobalByFilters(
              eq(true),
              eq(List.of(matId)),
              isNull(),
              eq(false),
              isNull(),
              eq(false),
              isNull(),
              isNull(),
              any(Pageable.class));
    }

    @Test
    void jobOrderAndMissionLists_routedSeparately() {
      UUID jobId = UUID.randomUUID();
      UUID missionId = UUID.randomUUID();
      stubFindGlobalReturning(List.of());

      service.getAllAggregatedInventory(null, 500, List.of(jobId), List.of(missionId));

      verify(inventoryItemRepository)
          .findGlobalByFilters(
              eq(false),
              isNull(),
              eq(500),
              eq(true),
              eq(List.of(jobId)),
              eq(true),
              eq(List.of(missionId)),
              isNull(),
              any(Pageable.class));
    }

    @Test
    void minQualityPassedThroughUnchanged() {
      stubFindGlobalReturning(List.of());

      service.getAllAggregatedInventory(null, 750);

      ArgumentCaptor<Integer> captor = ArgumentCaptor.forClass(Integer.class);
      verify(inventoryItemRepository)
          .findGlobalByFilters(
              anyBoolean(),
              any(),
              captor.capture(),
              anyBoolean(),
              any(),
              anyBoolean(),
              any(),
              isNull(),
              any(Pageable.class));
      assertEquals(750, captor.getValue());
    }

    @Test
    void shortOverload_delegatesToFullOverloadWithNullExtras() {
      // getAllAggregatedInventory(mats, minQ) -> calls (mats, minQ, null, null).
      // The delegation is verified by asserting the resulting repository call
      // has hasJobOrders=false and hasMissions=false with both id-lists null.
      stubFindGlobalReturning(List.of());

      service.getAllAggregatedInventory(List.of(UUID.randomUUID()), 500);

      verify(inventoryItemRepository)
          .findGlobalByFilters(
              eq(true),
              any(),
              eq(500),
              eq(false),
              isNull(),
              eq(false),
              isNull(),
              isNull(),
              any(Pageable.class));
    }
  }

  // ---------------------------------------------------------------------
  // aggregateInventoryItems — the math + sort
  // ---------------------------------------------------------------------

  @Nested
  class AggregationMathTests {

    @Test
    void emptyInput_returnsEmptyList() {
      stubFindGlobalReturning(List.of());

      List<GroupedInventoryDto> result = service.getAllAggregatedInventory(null, null);

      assertTrue(result.isEmpty());
    }

    @Test
    void singleItem_singleMaterial_produces100PercentQuality() {
      MaterialReferenceDto mat = newMat("Quantanium");
      InventoryItemDto dto = newItem(mat, "ARC-L1", 500, 100.0);

      stubGlobalQuery(dto);

      List<GroupedInventoryDto> result = service.getAllAggregatedInventory(null, null);

      assertEquals(1, result.size());
      assertEquals("Quantanium", result.get(0).material().name());
      assertEquals(100.0, result.get(0).totalAmount());
      assertEquals(500.0, result.get(0).averageQuality());
      assertEquals(500, result.get(0).maxQuality());
      assertEquals(1, result.get(0).items().size());
    }

    @Test
    void weightedAverageQuality_isCorrect() {
      // material at two locations:
      // (amount=100, quality=400) and (amount=300, quality=500)
      // -> totalAmount = 400, weighted quality = (100*400 + 300*500)/400 = 475
      MaterialReferenceDto mat = newMat("Quantanium");
      InventoryItemDto small = newItem(mat, "ARC-L1", 400, 100.0);
      InventoryItemDto large = newItem(mat, "ARC-L2", 500, 300.0);

      stubGlobalQuery(small, large);

      GroupedInventoryDto grouped = service.getAllAggregatedInventory(null, null).get(0);
      assertEquals(400.0, grouped.totalAmount());
      assertEquals(
          475.0, grouped.averageQuality(), "weighted avg = (100*400 + 300*500) / 400 = 475.0");
    }

    @Test
    void maxQuality_tracksTheLargest() {
      MaterialReferenceDto mat = newMat("Quantanium");
      InventoryItemDto low = newItem(mat, "ARC-L1", 200, 100.0);
      InventoryItemDto mid = newItem(mat, "ARC-L1", 500, 100.0);
      InventoryItemDto top = newItem(mat, "ARC-L1", 800, 100.0);

      stubGlobalQuery(low, mid, top);

      GroupedInventoryDto grouped = service.getAllAggregatedInventory(null, null).get(0);
      assertEquals(800, grouped.maxQuality(), "max quality must be the highest across items");
    }

    @Test
    void averageQualityRoundedToTwoDecimals() {
      // total=3, qualitySum = 1*100 + 1*150 + 1*200 = 450 -> avg=150.0
      // tweak so the avg has more decimals: total=3, sum=1*100+1*150+1*201=451/3=150.333...
      MaterialReferenceDto mat = newMat("Quantanium");
      InventoryItemDto a = newItem(mat, "ARC-L1", 100, 1.0);
      InventoryItemDto b = newItem(mat, "ARC-L2", 150, 1.0);
      InventoryItemDto c = newItem(mat, "ARC-L3", 201, 1.0);

      stubGlobalQuery(a, b, c);

      double avg = service.getAllAggregatedInventory(null, null).get(0).averageQuality();
      assertEquals(150.33, avg, "Math.round(150.3333 * 100) / 100 = 150.33 with HALF_UP rounding");
    }

    @Test
    void nullAmountCoercedToZero() {
      MaterialReferenceDto mat = newMat("Quantanium");
      InventoryItemDto solid = newItem(mat, "ARC-L1", 500, 100.0);
      InventoryItemDto nullAmt = newItem(mat, "ARC-L2", 600, null);

      stubGlobalQuery(solid, nullAmt);

      GroupedInventoryDto grouped = service.getAllAggregatedInventory(null, null).get(0);
      // total = 100 + 0 = 100; weighted = 100*500 + 0*600 = 50000; avg = 500
      assertEquals(100.0, grouped.totalAmount(), "null amount must be treated as 0, not NPE");
      assertEquals(500.0, grouped.averageQuality());
    }

    @Test
    void nullQuality_coercedToZeroInSortAndLoop() {
      // Regression: the sort comparator used to dereference
      // InventoryItemDto::quality unconditionally and NPE before the
      // loop's defensive null-coercion could run. After the fix the
      // sort key coerces null -> 0 just like the loop does, so a
      // legacy row with null quality aggregates cleanly.
      MaterialReferenceDto mat = newMat("Quantanium");
      InventoryItemDto solid = newItem(mat, "ARC-L1", 500, 100.0);
      InventoryItemDto nullQ = newItem(mat, "ARC-L2", null, 100.0);

      stubGlobalQuery(solid, nullQ);

      GroupedInventoryDto grouped = service.getAllAggregatedInventory(null, null).get(0);
      // total = 100 + 100 = 200; weighted = 100*500 + 100*0 = 50000; avg = 250
      assertEquals(200.0, grouped.totalAmount());
      assertEquals(250.0, grouped.averageQuality());
      assertEquals(
          500,
          grouped.maxQuality(),
          "max quality must come from the non-null sibling, null treated as 0");
    }

    @Test
    void nullAmount_inSort_whenQualityAndLocationTie() {
      // Regression companion to nullQuality_coercedToZeroInSortAndLoop:
      // two items with the SAME quality and SAME location so the
      // comparator chain falls through to the tertiary amount key.
      // Pre-fix, that key NPE'd on a null amount because it called
      // Double.compareTo(null).
      MaterialReferenceDto mat = newMat("Quantanium");
      InventoryItemDto withAmt = newItem(mat, "ARC-L1", 200, 100.0);
      InventoryItemDto nullAmt = newItem(mat, "ARC-L1", 200, null);

      stubGlobalQuery(withAmt, nullAmt);

      GroupedInventoryDto grouped = service.getAllAggregatedInventory(null, null).get(0);
      // total = 100 + 0 = 100; weighted = 100*200 + 0*200 = 20000; avg = 200
      assertEquals(100.0, grouped.totalAmount());
      assertEquals(200.0, grouped.averageQuality());
      assertEquals(200, grouped.maxQuality());
    }

    @Test
    void nullLocation_inSort_doesNotNpe() {
      // Regression: the secondary sort key was i -> i.location().name(),
      // which NPE'd on a null LocationReferenceDto. The fix falls back
      // to "" when either the location or its name is null.
      MaterialReferenceDto mat = newMat("Quantanium");
      InventoryItemDto nullLoc =
          new InventoryItemDto(
              UUID.randomUUID(),
              null,
              mat,
              null,
              400,
              50.0,
              false,
              null,
              null,
              null,
              null,
              null,
              null,
              1L);
      InventoryItemDto withLoc = newItem(mat, "ARC-L2", 200, 100.0);

      stubGlobalQuery(nullLoc, withLoc);

      GroupedInventoryDto grouped = service.getAllAggregatedInventory(null, null).get(0);
      // total = 50 + 100 = 150; weighted = 50*400 + 100*200 = 40000; avg = 40000/150
      // = 266.666... rounded HALF_UP to 266.67
      assertEquals(150.0, grouped.totalAmount());
      assertEquals(266.67, grouped.averageQuality());
      assertEquals(400, grouped.maxQuality());
    }

    @Test
    void totalAmountZero_avgIsZero_notNaN() {
      // All items have amount=0 OR null -> sum=0 -> div-by-zero guard must
      // clamp avg to 0.0 instead of producing NaN.
      MaterialReferenceDto mat = newMat("Quantanium");
      InventoryItemDto zero1 = newItem(mat, "ARC-L1", 500, 0.0);
      InventoryItemDto nullAmt = newItem(mat, "ARC-L2", 500, null);

      stubGlobalQuery(zero1, nullAmt);

      GroupedInventoryDto grouped = service.getAllAggregatedInventory(null, null).get(0);
      assertEquals(0.0, grouped.totalAmount());
      assertEquals(
          0.0,
          grouped.averageQuality(),
          "div-by-zero guard must clamp avgQuality to 0.0, NOT produce NaN");
      assertNotNull(grouped.averageQuality());
      assertFalse_NaN(grouped.averageQuality());
    }
  }

  // ---------------------------------------------------------------------
  // sort ordering
  // ---------------------------------------------------------------------

  @Nested
  class SortOrderTests {

    @Test
    void materials_sortedAlphabeticallyByName() {
      InventoryItemDto agricium = newItem(newMat("Agricium"), "L", 500, 10.0);
      InventoryItemDto laranite = newItem(newMat("Laranite"), "L", 500, 10.0);
      InventoryItemDto zeyneh = newItem(newMat("Zeyneh"), "L", 500, 10.0);
      InventoryItemDto beryl = newItem(newMat("Beryl"), "L", 500, 10.0);

      stubGlobalQuery(laranite, zeyneh, agricium, beryl);

      List<GroupedInventoryDto> result = service.getAllAggregatedInventory(null, null);

      assertEquals(
          List.of("Agricium", "Beryl", "Laranite", "Zeyneh"),
          result.stream().map(g -> g.material().name()).toList());
    }

    @Test
    void itemsWithinMaterial_sortedByQualityDescThenLocationAscThenAmountDesc() {
      MaterialReferenceDto mat = newMat("Quantanium");
      // a: q=500, loc=B, amt=10
      // b: q=500, loc=A, amt=10  -> ties with a on quality, location A < B
      // c: q=500, loc=A, amt=20  -> ties with b on quality+location, amount 20 > 10
      // d: q=300, loc=A, amt=100 -> lowest quality, sorts last
      InventoryItemDto a = newItem(mat, "B", 500, 10.0);
      InventoryItemDto b = newItem(mat, "A", 500, 10.0);
      InventoryItemDto c = newItem(mat, "A", 500, 20.0);
      InventoryItemDto d = newItem(mat, "A", 300, 100.0);

      stubGlobalQuery(d, a, b, c);

      List<InventoryItemDto> items = service.getAllAggregatedInventory(null, null).get(0).items();

      // Expected order: c (q=500, locA, amt=20), b (q=500, locA, amt=10),
      //                 a (q=500, locB, amt=10),  d (q=300, locA, amt=100)
      assertEquals(c, items.get(0), "highest quality + location A + highest amount wins");
      assertEquals(b, items.get(1), "q=500 + locA + amt=10 second");
      assertEquals(a, items.get(2), "q=500 + locB third (location B > A)");
      assertEquals(d, items.get(3), "q=300 last");
    }
  }

  // ---------------------------------------------------------------------
  // helpers
  // ---------------------------------------------------------------------

  private void stubFindGlobalReturning(List<InventoryItem> entities) {
    Page<InventoryItem> page = new PageImpl<>(entities);
    lenient()
        .when(
            inventoryItemRepository.findGlobalByFilters(
                anyBoolean(),
                any(),
                any(),
                anyBoolean(),
                any(),
                anyBoolean(),
                any(),
                isNull(),
                any(Pageable.class)))
        .thenReturn(page);
  }

  /**
   * Stubs the global query to return one InventoryItem per provided DTO, with the mapper rigged to
   * translate the entity back into the DTO. This keeps the test focused on the aggregation logic
   * without needing real entity-to-DTO conversion.
   */
  private void stubGlobalQuery(InventoryItemDto... dtos) {
    List<InventoryItem> entities = new ArrayList<>();
    for (InventoryItemDto dto : dtos) {
      InventoryItem entity = new InventoryItem();
      entity.setId(dto.id());
      entities.add(entity);
      lenient().when(inventoryItemMapper.toDto(entity)).thenReturn(dto);
    }
    Page<InventoryItem> page = new PageImpl<>(entities);
    when(inventoryItemRepository.findGlobalByFilters(
            anyBoolean(),
            any(),
            any(),
            anyBoolean(),
            any(),
            anyBoolean(),
            any(),
            isNull(),
            any(Pageable.class)))
        .thenReturn(page);
  }

  private static MaterialReferenceDto newMat(String name) {
    return new MaterialReferenceDto(UUID.randomUUID(), name, QuantityType.SCU);
  }

  private static InventoryItemDto newItem(
      MaterialReferenceDto material, String locationName, Integer quality, Double amount) {
    LocationReferenceDto loc = new LocationReferenceDto(UUID.randomUUID(), locationName);
    return new InventoryItemDto(
        UUID.randomUUID(), // id
        null, // user
        material,
        loc,
        quality,
        amount,
        false, // personal
        null,
        null, // jobOrderId, jobOrderDisplayId
        null,
        null, // missionId, missionName
        null, // note
        null, // owningSquadron
        1L); // version
  }

  private static void assertFalse_NaN(double v) {
    assertTrue(!Double.isNaN(v), "value must not be NaN");
  }

  private static void assertFalse(boolean condition) {
    assertTrue(!condition);
  }
}
