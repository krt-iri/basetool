/*
 * Profit Basetool - squadron-management web app.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
import de.greluc.krt.iri.basetool.backend.model.dto.InventoryStackDto;
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
  @Mock private OwnerScopeService ownerScopeService;

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
              anyBoolean(),
              any(),
              any(),
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
              anyBoolean(),
              any(),
              any(),
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
              anyBoolean(),
              any(),
              any(),
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
              anyBoolean(),
              any(),
              any(),
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
              anyBoolean(),
              any(),
              any(),
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
              anyBoolean(),
              any(),
              any(),
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
      assertEquals(1, result.get(0).stacks().size());
      assertEquals(1, result.get(0).stacks().get(0).entryCount());
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
      // Two entries share the stock identity (same location + quality), so they collapse into one
      // stack with two entries. The null amount must still coerce to 0 in the aggregation loop
      // instead of NPE-ing.
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
              1L,
              null);
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
    void stacksWithinMaterial_collapseSameIdentityAndSortByQualityDescLocationAscAmountDesc() {
      MaterialReferenceDto mat = newMat("Quantanium");
      // b and c share the stock identity (loc=A, q=500) and collapse into one stack (10 + 20 = 30);
      // a and d form their own stacks. Append-only keeps b and c as separate entries in the stack.
      InventoryItemDto a = newItem(mat, "B", 500, 10.0); // stack (B, 500) total 10
      InventoryItemDto b = newItem(mat, "A", 500, 10.0); // stack (A, 500) ...
      InventoryItemDto c = newItem(mat, "A", 500, 20.0); // ... + 20 = total 30, 2 entries
      InventoryItemDto d = newItem(mat, "A", 300, 100.0); // stack (A, 300) total 100

      stubGlobalQuery(d, a, b, c);

      List<InventoryStackDto> stacks =
          service.getAllAggregatedInventory(null, null).get(0).stacks();

      // Expected order: (A,500) total 30, then (B,500) total 10, then (A,300) total 100.
      assertEquals(3, stacks.size(), "b and c collapse into one stack, so three stacks remain");

      assertEquals("A", stacks.get(0).location().name(), "highest quality, location A first");
      assertEquals(500, stacks.get(0).quality());
      assertEquals(30.0, stacks.get(0).totalAmount(), "the two locA/q500 entries sum");
      assertEquals(2, stacks.get(0).entryCount());

      assertEquals("B", stacks.get(1).location().name(), "q=500 location B after A");
      assertEquals(10.0, stacks.get(1).totalAmount());
      assertEquals(1, stacks.get(1).entryCount());

      assertEquals("A", stacks.get(2).location().name());
      assertEquals(300, stacks.get(2).quality(), "lowest quality sorts last");
      assertEquals(100.0, stacks.get(2).totalAmount());
    }

    @Test
    void entriesWithinStack_orderedOldestFirstByCreatedAt() {
      MaterialReferenceDto mat = newMat("Quantanium");
      java.time.Instant t1 = java.time.Instant.parse("2026-01-01T00:00:00Z");
      java.time.Instant t2 = java.time.Instant.parse("2026-02-01T00:00:00Z");
      java.time.Instant t3 = java.time.Instant.parse("2026-03-01T00:00:00Z");
      // Same stock identity (loc A, q500) recorded out of order; the stack must list them oldest
      // first regardless of input order.
      InventoryItemDto newest = newItem(mat, "A", 500, 1.0, t3);
      InventoryItemDto oldest = newItem(mat, "A", 500, 1.0, t1);
      InventoryItemDto middle = newItem(mat, "A", 500, 1.0, t2);

      stubGlobalQuery(newest, oldest, middle);

      InventoryStackDto stack =
          service.getAllAggregatedInventory(null, null).get(0).stacks().get(0);
      assertEquals(3, stack.entryCount());
      assertEquals(
          List.of(oldest, middle, newest), stack.entries(), "entries ordered oldest-first");
    }
  }

  // ---------------------------------------------------------------------
  // helpers
  // ---------------------------------------------------------------------

  private void stubFindGlobalReturning(List<InventoryItem> entities) {
    Page<InventoryItem> page = new PageImpl<>(entities);
    lenient()
        .when(ownerScopeService.currentScopePredicate())
        .thenReturn(new ScopePredicate(true, null, java.util.Set.of()));
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
                anyBoolean(),
                any(),
                any(),
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
    lenient()
        .when(ownerScopeService.currentScopePredicate())
        .thenReturn(new ScopePredicate(true, null, java.util.Set.of()));
    when(inventoryItemRepository.findGlobalByFilters(
            anyBoolean(),
            any(),
            any(),
            anyBoolean(),
            any(),
            anyBoolean(),
            any(),
            anyBoolean(),
            any(),
            any(),
            any(Pageable.class)))
        .thenReturn(page);
  }

  private static MaterialReferenceDto newMat(String name) {
    return new MaterialReferenceDto(UUID.randomUUID(), name, QuantityType.SCU);
  }

  private static InventoryItemDto newItem(
      MaterialReferenceDto material, String locationName, Integer quality, Double amount) {
    return newItem(material, locationName, quality, amount, null);
  }

  /**
   * Builds an inventory DTO whose location id is derived from the location name so two {@code
   * newItem} calls with the same name land in the same stack (the grouping keys on the location id,
   * not the object identity). {@code createdAt} controls the oldest-first entry order within a
   * stack.
   */
  private static InventoryItemDto newItem(
      MaterialReferenceDto material,
      String locationName,
      Integer quality,
      Double amount,
      java.time.Instant createdAt) {
    LocationReferenceDto loc = new LocationReferenceDto(locationId(locationName), locationName);
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
        1L, // version
        createdAt);
  }

  /** Deterministic UUID per location name so same-named locations share one stack. */
  private static UUID locationId(String name) {
    return UUID.nameUUIDFromBytes(
        ("loc:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  private static void assertFalse_NaN(double v) {
    assertTrue(!Double.isNaN(v), "value must not be NaN");
  }

  private static void assertFalse(boolean condition) {
    assertTrue(!condition);
  }
}
