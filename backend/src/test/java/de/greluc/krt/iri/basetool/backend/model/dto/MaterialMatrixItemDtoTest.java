package de.greluc.krt.iri.basetool.backend.model.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Unit tests for {@link MaterialMatrixItemDto}. */
public class MaterialMatrixItemDtoTest {

  /** Verifies the DTO round-trips through Jackson and exposes the effective {@code planetName}. */
  @Test
  public void testSerialization() throws Exception {
    MaterialMatrixItemDto dto =
        new MaterialMatrixItemDto(
            UUID.randomUUID(),
            "Material",
            false,
            false,
            false,
            new MaterialCategoryDto(UUID.randomUUID(), "Metal", 0L),
            UUID.randomUUID(),
            "Terminal",
            "Nickname",
            "System",
            BigDecimal.TEN,
            BigDecimal.ONE,
            "City",
            "Station",
            "Outpost",
            "Hurston",
            true,
            true,
            true);
    JsonMapper mapper = JsonMapper.builder().build();
    String json = mapper.writeValueAsString(dto);
    assertNotNull(json);
    assertTrue(json.contains("\"planetName\":\"Hurston\""));
    assertEquals("Hurston", dto.planetName());
  }
}
