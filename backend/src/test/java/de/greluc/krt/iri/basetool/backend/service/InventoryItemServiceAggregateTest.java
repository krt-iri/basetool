package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.mapper.InventoryItemMapper;
import de.greluc.krt.iri.basetool.backend.mapper.MaterialMapper;
import de.greluc.krt.iri.basetool.backend.model.InventoryItem;
import de.greluc.krt.iri.basetool.backend.model.User;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

/**
 * Exercises the private {@code aggregateInventoryItems} helper indirectly via
 * {@link InventoryItemService#getMyAggregatedInventory(UUID)}. The sort comparator
 * inside that helper used to dereference {@code quality} / {@code amount} / {@code location.name()}
 * unconditionally and would NPE on legacy rows with any of those fields null, before
 * the defensive coercion in the aggregation loop had a chance to run. These tests
 * pin down the post-fix behavior: nulls are tolerated, sort succeeds, aggregation
 * uses 0 / 0.0 in place of null.
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

    @InjectMocks private InventoryItemService inventoryItemService;

    private static final MaterialReferenceDto MATERIAL = new MaterialReferenceDto(
            UUID.fromString("00000000-0000-0000-0000-000000000001"), "Laranite", null);

    private static LocationReferenceDto loc(String name) {
        return new LocationReferenceDto(UUID.randomUUID(), name);
    }

    private static InventoryItemDto dto(Integer quality, Double amount, LocationReferenceDto location) {
        return new InventoryItemDto(UUID.randomUUID(), null, MATERIAL, location,
                quality, amount, false, null, null, null, null, null, 1L);
    }

    private static InventoryItem entityWithId() {
        InventoryItem e = new InventoryItem();
        e.setId(UUID.randomUUID());
        return e;
    }

    @Test
    void nullQuality_aggregatesWithQualityCoercedToZero() {
        // Given two items in the same material group, one with null quality.
        // Pre-fix: the sort key Comparator.comparing(InventoryItemDto::quality) NPEs
        // before the loop's defensive null-coercion runs.
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);

        InventoryItem e1 = entityWithId();
        InventoryItem e2 = entityWithId();

        InventoryItemDto d1 = dto(null, 100.0, loc("LocA"));
        InventoryItemDto d2 = dto(500, 100.0, loc("LocB"));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(inventoryItemRepository.findUserByFilters(
                any(User.class), anyBoolean(), any(), any(), anyBoolean(), any(), anyBoolean(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(e1, e2)));
        when(inventoryItemMapper.toDto(e1)).thenReturn(d1);
        when(inventoryItemMapper.toDto(e2)).thenReturn(d2);

        // When
        List<GroupedInventoryDto> result = inventoryItemService.getMyAggregatedInventory(userId);

        // Then: (null -> 0) * 100 + 500 * 100 = 50000; / 200 = 250.0
        assertEquals(1, result.size());
        GroupedInventoryDto group = result.get(0);
        assertEquals(200.0, group.totalAmount(), 1e-9);
        assertEquals(250.0, group.averageQuality(), 1e-9);
        assertEquals(500, group.maxQuality());
    }

    @Test
    void nullAmount_aggregatesWithAmountCoercedToZero_whenQualityTies() {
        // Given two items with the SAME quality and SAME location so the comparator
        // chain falls through to the tertiary amount key — that key NPE'd on null amount
        // before the fix.
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);

        InventoryItem e1 = entityWithId();
        InventoryItem e2 = entityWithId();

        InventoryItemDto d1 = dto(200, null, loc("LocA"));
        InventoryItemDto d2 = dto(200, 100.0, loc("LocA"));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(inventoryItemRepository.findUserByFilters(
                any(User.class), anyBoolean(), any(), any(), anyBoolean(), any(), anyBoolean(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(e1, e2)));
        when(inventoryItemMapper.toDto(e1)).thenReturn(d1);
        when(inventoryItemMapper.toDto(e2)).thenReturn(d2);

        // When
        List<GroupedInventoryDto> result = inventoryItemService.getMyAggregatedInventory(userId);

        // Then: (null -> 0) * 200 + 100 * 200 = 20000; / 100 = 200.0
        assertEquals(1, result.size());
        GroupedInventoryDto group = result.get(0);
        assertEquals(100.0, group.totalAmount(), 1e-9);
        assertEquals(200.0, group.averageQuality(), 1e-9);
        assertEquals(200, group.maxQuality());
    }

    @Test
    void nullLocation_doesNotThrow_andAggregatesNormally() {
        // Given two items, one with a null LocationReferenceDto. The secondary sort key
        // .thenComparing(i -> i.location().name()) would NPE before the fix.
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);

        InventoryItem e1 = entityWithId();
        InventoryItem e2 = entityWithId();

        InventoryItemDto d1 = dto(400, 50.0, null);
        InventoryItemDto d2 = dto(200, 100.0, loc("LocB"));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(inventoryItemRepository.findUserByFilters(
                any(User.class), anyBoolean(), any(), any(), anyBoolean(), any(), anyBoolean(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(e1, e2)));
        when(inventoryItemMapper.toDto(e1)).thenReturn(d1);
        when(inventoryItemMapper.toDto(e2)).thenReturn(d2);

        // When
        List<GroupedInventoryDto> result = inventoryItemService.getMyAggregatedInventory(userId);

        // Then: 400 * 50 + 200 * 100 = 40000; / 150 = 266.666... rounded to 266.67
        assertEquals(1, result.size());
        GroupedInventoryDto group = result.get(0);
        assertEquals(150.0, group.totalAmount(), 1e-9);
        assertEquals(266.67, group.averageQuality(), 1e-9);
        assertEquals(400, group.maxQuality());
    }
}
