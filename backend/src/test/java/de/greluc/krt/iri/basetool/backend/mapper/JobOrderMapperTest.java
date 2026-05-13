package de.greluc.krt.iri.basetool.backend.mapper;

import static org.junit.jupiter.api.Assertions.*;

import de.greluc.krt.iri.basetool.backend.model.JobOrder;
import de.greluc.krt.iri.basetool.backend.model.JobOrderHandover;
import de.greluc.krt.iri.basetool.backend.model.JobOrderMaterial;
import de.greluc.krt.iri.basetool.backend.model.JobOrderStatus;
import de.greluc.krt.iri.basetool.backend.model.Material;
import de.greluc.krt.iri.basetool.backend.model.MaterialType;
import de.greluc.krt.iri.basetool.backend.model.QuantityType;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderDto;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderMaterialDto;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

class JobOrderMapperTest {

  private JobOrderMapper mapper;

  @BeforeEach
  void setUp() {
    // JobOrderMapperImpl @Autowires UserMapper, MaterialMapper and
    // JobOrderHandoverMapper — wire all three manually since we are not
    // running inside a Spring context.
    mapper = Mappers.getMapper(JobOrderMapper.class);

    var userMapper = Mappers.getMapper(UserMapper.class);
    var materialMapper = Mappers.getMapper(MaterialMapper.class);
    var handoverMapper = Mappers.getMapper(JobOrderHandoverMapper.class);
    ReflectionTestUtils.setField(handoverMapper, "materialMapper", materialMapper);

    ReflectionTestUtils.setField(mapper, "userMapper", userMapper);
    ReflectionTestUtils.setField(mapper, "materialMapper", materialMapper);
    ReflectionTestUtils.setField(mapper, "jobOrderHandoverMapper", handoverMapper);
  }

  @Test
  void toDto_shouldMapScalarsMaterialsAssigneesAndHandovers() {
    // Given
    UUID id = UUID.randomUUID();
    Instant createdAt = Instant.parse("2026-05-01T10:00:00Z");

    Material gold = newMaterial("Gold", QuantityType.SCU);
    JobOrderMaterial jm = new JobOrderMaterial();
    jm.setId(UUID.randomUUID());
    jm.setMaterial(gold);
    jm.setMinQuality(800);
    jm.setAmount(5.0);
    jm.setVersion(1L);

    User assignee = new User();
    assignee.setId(UUID.randomUUID());
    assignee.setUsername("logist");
    assignee.setRoles(new HashSet<>());

    JobOrder jobOrder = new JobOrder();
    jobOrder.setId(id);
    jobOrder.setDisplayId(42);
    jobOrder.setSquadron("Iridium");
    jobOrder.setHandle("recipient");
    jobOrder.setPriority(3);
    jobOrder.setStatus(JobOrderStatus.IN_PROGRESS);
    jobOrder.setMaterials(new HashSet<>(Set.of(jm)));
    jobOrder.setAssignees(new HashSet<>(Set.of(assignee)));
    jobOrder.setHandovers(new HashSet<>());
    jobOrder.setCreatedAt(createdAt);
    jobOrder.setVersion(7L);

    // When
    JobOrderDto dto = mapper.toDto(jobOrder);

    // Then
    assertNotNull(dto);
    assertEquals(id, dto.id());
    assertEquals(42, dto.displayId());
    assertEquals("Iridium", dto.squadron());
    assertEquals("recipient", dto.handle());
    assertEquals(3, dto.priority());
    assertEquals(JobOrderStatus.IN_PROGRESS, dto.status());
    assertEquals(createdAt, dto.createdAt());
    assertEquals(7L, dto.version());

    // Materials are mapped and sorted
    assertNotNull(dto.materials());
    assertEquals(1, dto.materials().size());
    JobOrderMaterialDto matDto = dto.materials().getFirst();
    assertEquals(jm.getId(), matDto.id());
    assertEquals(800, matDto.minQuality());
    assertEquals(5.0, matDto.amount());
    assertEquals("Gold", matDto.material().name());

    // Assignees mapped via UserMapper
    assertNotNull(dto.assignees());
    assertEquals(1, dto.assignees().size());
    assertEquals("logist", dto.assignees().getFirst().username());

    // Handovers — empty set should map to empty list, not null
    assertNotNull(dto.handovers());
    assertTrue(dto.handovers().isEmpty());
  }

  @Test
  void toDto_withNullCollections_shouldProduceNullCollections() {
    // Given — entity with no collections set (legacy fixture)
    JobOrder jobOrder = new JobOrder();
    jobOrder.setId(UUID.randomUUID());
    jobOrder.setSquadron("Iridium");
    jobOrder.setStatus(JobOrderStatus.OPEN);
    jobOrder.setMaterials(null);
    jobOrder.setAssignees(null);
    jobOrder.setHandovers(null);

    // When
    JobOrderDto dto = mapper.toDto(jobOrder);

    // Then
    assertNotNull(dto);
    assertNull(dto.materials(), "null source set should map to null list (MapStruct default)");
    assertNull(dto.assignees());
    assertNull(dto.handovers());
  }

  @Test
  void toDto_withHandovers_shouldMapEachHandoverViaHandoverMapper() {
    // Given
    JobOrder jobOrder = new JobOrder();
    jobOrder.setId(UUID.randomUUID());
    jobOrder.setSquadron("Iridium");
    jobOrder.setStatus(JobOrderStatus.COMPLETED);

    JobOrderHandover handover = new JobOrderHandover();
    handover.setId(UUID.randomUUID());
    handover.setJobOrder(jobOrder);
    handover.setHandoverTime(Instant.parse("2026-05-10T08:00:00Z"));
    handover.setRecipientHandle("buyer");
    handover.setRecipientSquadron("Iridium");
    handover.setVersion(1L);

    jobOrder.setHandovers(new HashSet<>(Set.of(handover)));
    jobOrder.setMaterials(new HashSet<>());
    jobOrder.setAssignees(new HashSet<>());

    // When
    JobOrderDto dto = mapper.toDto(jobOrder);

    // Then
    assertNotNull(dto.handovers());
    assertEquals(1, dto.handovers().size());
    var hDto = dto.handovers().getFirst();
    assertEquals(handover.getId(), hDto.id());
    assertEquals(
        jobOrder.getId(),
        hDto.jobOrderId(),
        "jobOrderId must be flattened from nested handover.jobOrder.id");
    assertEquals("buyer", hDto.recipientHandle());
  }

  @Test
  void singleMaterialToDto_shouldUseInjectedMaterialMapperAndLeaveCurrentStockNull() {
    // Given
    Material material = newMaterial("Quantanium", QuantityType.SCU);
    material.setIsIllegal(1);

    JobOrderMaterial jm = new JobOrderMaterial();
    jm.setId(UUID.randomUUID());
    jm.setMaterial(material);
    jm.setMinQuality(900);
    jm.setAmount(2.5);
    jm.setVersion(3L);

    // When
    JobOrderMaterialDto dto = mapper.toDto(jm);

    // Then
    assertNotNull(dto);
    assertEquals(jm.getId(), dto.id());
    assertEquals(900, dto.minQuality());
    assertEquals(2.5, dto.amount());
    assertEquals(3L, dto.version());
    assertNotNull(dto.material());
    assertEquals("Quantanium", dto.material().name());
    assertEquals(QuantityType.SCU.name(), dto.material().quantityType());
    assertTrue(dto.material().isIllegal());
    // The mapper explicitly leaves currentStock null; the service fills it
    // from a separate stock lookup, so the mapper must NOT make up a value.
    assertNull(dto.currentStock());
  }

  @Test
  void mapAndSortMaterials_shouldSortScuFirstThenAlphabetically() {
    // Given a deliberately unsorted set with both quantity types
    Material gold = newMaterial("Gold", QuantityType.SCU);
    Material iron = newMaterial("Iron", QuantityType.PIECE);
    Material silver = newMaterial("Silver", QuantityType.SCU);
    Material copper = newMaterial("Copper", QuantityType.PIECE);

    JobOrderMaterial jm1 = new JobOrderMaterial();
    jm1.setId(UUID.randomUUID());
    jm1.setMaterial(gold);
    jm1.setMinQuality(0);
    jm1.setAmount(1.0);
    JobOrderMaterial jm2 = new JobOrderMaterial();
    jm2.setId(UUID.randomUUID());
    jm2.setMaterial(iron);
    jm2.setMinQuality(0);
    jm2.setAmount(1.0);
    JobOrderMaterial jm3 = new JobOrderMaterial();
    jm3.setId(UUID.randomUUID());
    jm3.setMaterial(silver);
    jm3.setMinQuality(0);
    jm3.setAmount(1.0);
    JobOrderMaterial jm4 = new JobOrderMaterial();
    jm4.setId(UUID.randomUUID());
    jm4.setMaterial(copper);
    jm4.setMinQuality(0);
    jm4.setAmount(1.0);

    Set<JobOrderMaterial> materials = Set.of(jm1, jm2, jm3, jm4);

    // When
    List<JobOrderMaterialDto> result = mapper.mapAndSortMaterials(materials);

    // Then — SCU first, then PIECE, each group case-insensitive alphabetical
    assertEquals(4, result.size());
    assertEquals("Gold", result.get(0).material().name());
    assertEquals("Silver", result.get(1).material().name());
    assertEquals("Copper", result.get(2).material().name());
    assertEquals("Iron", result.get(3).material().name());
  }

  @Test
  void mapAndSortMaterials_shouldHandleNullMaterialOnEntryGracefully() {
    // Given a JobOrderMaterial with no material reference (paranoia — should not happen
    // under DB constraint, but the mapper has a defensive null check we want to cover)
    JobOrderMaterial broken = new JobOrderMaterial();
    broken.setId(UUID.randomUUID());
    broken.setMaterial(null);
    broken.setMinQuality(0);
    broken.setAmount(1.0);

    Material gold = newMaterial("Gold", QuantityType.SCU);
    JobOrderMaterial ok = new JobOrderMaterial();
    ok.setId(UUID.randomUUID());
    ok.setMaterial(gold);
    ok.setMinQuality(0);
    ok.setAmount(1.0);

    // When
    List<JobOrderMaterialDto> result = mapper.mapAndSortMaterials(Set.of(broken, ok));

    // Then — Gold first (SCU group), broken entry falls into the PIECE / no-material group at the
    // end
    assertEquals(2, result.size());
    assertEquals("Gold", result.get(0).material().name());
    assertNull(result.get(1).material());
  }

  @Test
  void mapAndSortMaterials_withNullSet_shouldReturnNull() {
    assertNull(mapper.mapAndSortMaterials(null));
  }

  @Test
  void nullSafety_shouldReturnNull_whenSourceNull() {
    assertNull(mapper.toDto((JobOrder) null));
    assertNull(mapper.toDto((JobOrderMaterial) null));
  }

  // ─── helper ─────────────────────────────────────────────────────────────────

  private static Material newMaterial(String name, QuantityType quantityType) {
    Material material = new Material();
    material.setId(UUID.randomUUID());
    material.setName(name);
    material.setType(MaterialType.RAW);
    material.setQuantityType(quantityType);
    material.setIsIllegal(0);
    material.setIsVolatileQt(0);
    material.setIsVolatileTime(0);
    material.setIsManualRawMaterial(false);
    material.setIsJobOrder(false);
    material.setVersion(1L);
    return material;
  }
}
