package de.greluc.krt.iri.basetool.backend.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import de.greluc.krt.iri.basetool.backend.model.GameItem;
import de.greluc.krt.iri.basetool.backend.model.PersonalBlueprint;
import de.greluc.krt.iri.basetool.backend.model.dto.PersonalBlueprintResponse;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

/** Unit tests for {@link PersonalBlueprintMapper}. */
class PersonalBlueprintMapperTest {

  private final PersonalBlueprintMapper mapper = Mappers.getMapper(PersonalBlueprintMapper.class);

  @Test
  void toResponse_mapsFieldsAndFlattensOutputItemId() {
    UUID id = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    GameItem item = new GameItem();
    item.setId(itemId);

    PersonalBlueprint entity =
        PersonalBlueprint.builder()
            .id(id)
            .ownerSub("user-sub-1")
            .productKey("arclight pistol")
            .productName("Arclight Pistol")
            .outputItem(item)
            .acquiredAt(Instant.parse("2026-01-02T03:04:05Z"))
            .note("looted in Pyro")
            .build();
    entity.setVersion(4L);

    PersonalBlueprintResponse response = mapper.toResponse(entity);

    assertEquals(id, response.id());
    assertEquals("arclight pistol", response.productKey());
    assertEquals("Arclight Pistol", response.productName());
    assertEquals(itemId, response.outputItemId());
    assertEquals(Instant.parse("2026-01-02T03:04:05Z"), response.acquiredAt());
    assertEquals("looted in Pyro", response.note());
    assertEquals(4L, response.version());
  }

  @Test
  void toResponse_leavesOutputItemIdNullWhenUnresolved() {
    PersonalBlueprint entity =
        PersonalBlueprint.builder()
            .id(UUID.randomUUID())
            .ownerSub("user-sub-1")
            .productKey("aril core")
            .productName("Aril Core")
            .build();

    PersonalBlueprintResponse response = mapper.toResponse(entity);

    assertNull(response.outputItemId());
  }
}
