package de.greluc.krt.iri.basetool.backend.mapper;

import static org.junit.jupiter.api.Assertions.*;

import de.greluc.krt.iri.basetool.backend.model.JobOrder;
import de.greluc.krt.iri.basetool.backend.model.JobOrderHandover;
import de.greluc.krt.iri.basetool.backend.model.JobOrderHandoverItem;
import de.greluc.krt.iri.basetool.backend.model.Material;
import de.greluc.krt.iri.basetool.backend.model.MaterialType;
import de.greluc.krt.iri.basetool.backend.model.QuantityType;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderHandoverDto;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderHandoverItemDto;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

class JobOrderHandoverMapperTest {

  private JobOrderHandoverMapper mapper;

  @BeforeEach
  void setUp() {
    // The generated JobOrderHandoverMapperImpl pulls MaterialMapper via @Autowired
    // for the item -> dto nested material conversion. Wire it manually outside
    // of a Spring context.
    mapper = Mappers.getMapper(JobOrderHandoverMapper.class);
    ReflectionTestUtils.setField(mapper, "materialMapper", Mappers.getMapper(MaterialMapper.class));
  }

  @Test
  void toDto_handover_shouldFlattenJobOrderIdAndCopyScalars() {
    // Given
    UUID jobOrderId = UUID.randomUUID();
    UUID handoverId = UUID.randomUUID();
    Instant when = Instant.parse("2026-05-13T08:00:00Z");

    JobOrder jo = new JobOrder();
    jo.setId(jobOrderId);

    JobOrderHandover handover = new JobOrderHandover();
    handover.setId(handoverId);
    handover.setJobOrder(jo);
    handover.setHandoverTime(when);
    handover.setRecipientHandle("recipient");
    handover.setRecipientSquadron("Iridium");
    handover.setVersion(2L);

    // When
    JobOrderHandoverDto dto = mapper.toDto(handover);

    // Then
    assertNotNull(dto);
    assertEquals(handoverId, dto.id());
    assertEquals(
        jobOrderId, dto.jobOrderId(), "jobOrderId must be flattened from nested jobOrder.id");
    assertEquals(when, dto.handoverTime());
    assertEquals("recipient", dto.recipientHandle());
    assertEquals("Iridium", dto.recipientSquadron());
    assertEquals(2L, dto.version());
  }

  @Test
  void toDto_handover_withoutJobOrder_shouldHaveNullJobOrderId() {
    // Given
    JobOrderHandover handover = new JobOrderHandover();
    handover.setId(UUID.randomUUID());
    handover.setHandoverTime(Instant.now());
    handover.setRecipientHandle("anonymous");

    // When
    JobOrderHandoverDto dto = mapper.toDto(handover);

    // Then
    assertNotNull(dto);
    assertNull(dto.jobOrderId());
  }

  @Test
  void toDto_item_shouldFlattenHandoverIdAndCopyScalars() {
    // Given
    UUID handoverId = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    UUID materialId = UUID.randomUUID();

    JobOrderHandover handover = new JobOrderHandover();
    handover.setId(handoverId);

    Material material = new Material();
    material.setId(materialId);
    material.setName("Titanium");
    material.setType(MaterialType.REFINED);
    material.setQuantityType(QuantityType.SCU);

    JobOrderHandoverItem item = new JobOrderHandoverItem();
    item.setId(itemId);
    item.setJobOrderHandover(handover);
    item.setMaterial(material);
    item.setQuality(900);
    item.setAmount(2.5);
    item.setLocationName("Lorville HAB");
    item.setVersion(1L);

    // When
    JobOrderHandoverItemDto dto = mapper.toDto(item);

    // Then
    assertNotNull(dto);
    assertEquals(itemId, dto.id());
    assertEquals(handoverId, dto.jobOrderHandoverId());
    assertNotNull(dto.material());
    assertEquals(materialId, dto.material().id());
    assertEquals("Titanium", dto.material().name());
    assertEquals(900, dto.quality());
    assertEquals(2.5, dto.amount());
    assertEquals("Lorville HAB", dto.locationName());
    assertEquals(1L, dto.version());
  }

  @Test
  void nullSafety_shouldReturnNull_whenSourceNull() {
    assertNull(mapper.toDto((JobOrderHandover) null));
    assertNull(mapper.toDto((JobOrderHandoverItem) null));
  }
}
