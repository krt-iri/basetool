package de.greluc.krt.iri.basetool.backend.mapper;

import static org.junit.jupiter.api.Assertions.*;

import de.greluc.krt.iri.basetool.backend.model.Location;
import de.greluc.krt.iri.basetool.backend.model.Mission;
import de.greluc.krt.iri.basetool.backend.model.RefineryOrder;
import de.greluc.krt.iri.basetool.backend.model.dto.LocationDto;
import de.greluc.krt.iri.basetool.backend.model.dto.MissionDto;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

class RefineryOrderMapperTest {

  private RefineryOrderMapper mapper;

  @BeforeEach
  void setUp() {
    // RefineryOrderMapperImpl @Autowires both UserMapper and MaterialMapper —
    // wire them manually outside of a Spring context.
    mapper = Mappers.getMapper(RefineryOrderMapper.class);
    ReflectionTestUtils.setField(mapper, "userMapper", Mappers.getMapper(UserMapper.class));
    ReflectionTestUtils.setField(mapper, "materialMapper", Mappers.getMapper(MaterialMapper.class));
    ReflectionTestUtils.setField(mapper, "squadronMapper", Mappers.getMapper(SquadronMapper.class));
  }

  @Test
  void computeProfit_shouldBeSalesMinusExpensesMinusOtherExpenses() {
    // Given
    RefineryOrder order = new RefineryOrder();
    order.setOreSales(1000.0);
    order.setExpenses(200.0);
    order.setOtherExpenses(50.0);

    // When
    double profit = mapper.computeProfit(order);

    // Then
    assertEquals(750.0, profit, 0.0001);
  }

  @Test
  void computeProfit_withNullFields_shouldTreatAsZero() {
    // Given — legacy data with null finance fields
    RefineryOrder order = new RefineryOrder();
    order.setOreSales(null);
    order.setExpenses(null);
    order.setOtherExpenses(null);

    // When
    double profit = mapper.computeProfit(order);

    // Then
    assertEquals(0.0, profit, 0.0001);
  }

  @Test
  void computeProfit_withOnlySales_shouldEqualSales() {
    // Given
    RefineryOrder order = new RefineryOrder();
    order.setOreSales(500.0);
    order.setExpenses(null);
    order.setOtherExpenses(null);

    // When
    double profit = mapper.computeProfit(order);

    // Then
    assertEquals(500.0, profit, 0.0001);
  }

  @Test
  void computeProfit_withLossScenario_shouldBeNegative() {
    // Given — expenses outweigh sales
    RefineryOrder order = new RefineryOrder();
    order.setOreSales(100.0);
    order.setExpenses(150.0);
    order.setOtherExpenses(25.0);

    // When
    double profit = mapper.computeProfit(order);

    // Then
    assertEquals(-75.0, profit, 0.0001);
  }

  @Test
  void computeProfit_nullEntity_shouldReturnZero() {
    assertEquals(0.0, mapper.computeProfit(null), 0.0001);
  }

  @Test
  void toDto_shouldIncludeComputedProfit() {
    // Given
    UUID id = UUID.randomUUID();
    RefineryOrder order = new RefineryOrder();
    order.setId(id);
    order.setOreSales(500.0);
    order.setExpenses(100.0);
    order.setOtherExpenses(25.0);
    order.setDurationMinutes(120L);

    Location loc = new Location();
    loc.setId(UUID.randomUUID());
    loc.setName("ARC-L1");
    order.setLocation(loc);

    // When
    var dto = mapper.toDto(order);

    // Then
    assertNotNull(dto);
    assertEquals(id, dto.id());
    assertEquals(375.0, dto.profit(), 0.0001);
    assertEquals(120L, dto.durationMinutes());
    assertNotNull(dto.location());
    assertEquals("ARC-L1", dto.location().name());
  }

  @Test
  void toListDto_shouldIncludeComputedProfit() {
    // Given
    UUID id = UUID.randomUUID();
    RefineryOrder order = new RefineryOrder();
    order.setId(id);
    order.setOreSales(1200.0);
    order.setExpenses(300.0);
    order.setOtherExpenses(null);

    // When
    var dto = mapper.toListDto(order);

    // Then
    assertNotNull(dto);
    assertEquals(id, dto.id());
    assertEquals(900.0, dto.profit(), 0.0001);
  }

  @Test
  void missionDtoToMission_shouldOnlyCopyId() {
    // Given a MissionDto where only the id is relevant for the conversion
    UUID missionId = UUID.randomUUID();
    MissionDto dto =
        new MissionDto(
            missionId,
            "Op Sunfire",
            "desc",
            null,
            "PLANNED",
            null,
            null,
            null,
            null,
            null, // 5 Instants + 1 status set above
            null,
            null,
            null,
            null,
            null,
            null,
            null, // isInternal, participants, units, frequencies, subMissions, inventoryEntries,
            // refineryOrders
            null,
            null,
            null, // operation, owner, managers
            null,
            null, // canEdit, canManageManagers
            null, // version
            null,
            null,
            null, // coreVersion, scheduleVersion, flagsVersion
            null,
            null // checkedInParticipants, registeredParticipants
            );

    // When
    Mission mission = mapper.missionDtoToMission(dto);

    // Then
    assertNotNull(mission);
    assertEquals(missionId, mission.getId());
    // name and other fields are NOT copied — only id is used for FK linkage
    assertNull(mission.getName());
  }

  @Test
  void missionDtoToMission_nullDto_shouldReturnNull() {
    assertNull(mapper.missionDtoToMission(null));
  }

  @Test
  void locationToDto_shouldExposeFullSurface() {
    // Given
    Location loc = new Location();
    loc.setId(UUID.randomUUID());
    loc.setName("CRU-L4");
    loc.setHidden(false);
    loc.setVersion(1L);

    // When
    LocationDto dto = mapper.locationToDto(loc);

    // Then
    assertNotNull(dto);
    assertEquals(loc.getId(), dto.id());
    assertEquals("CRU-L4", dto.name());
  }

  @Test
  void nullSafety_shouldReturnNull_whenSourceNull() {
    assertNull(mapper.toDto(null));
    assertNull(mapper.toListDto(null));
    assertNull(mapper.toEntity(null));
    assertNull(mapper.locationToDto(null));
  }
}
