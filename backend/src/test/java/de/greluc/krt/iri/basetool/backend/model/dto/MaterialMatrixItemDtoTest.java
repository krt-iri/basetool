package de.greluc.krt.iri.basetool.backend.model.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class MaterialMatrixItemDtoTest {
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
            true,
            true,
            true);
    ObjectMapper mapper = new ObjectMapper();
    mapper.writeValueAsString(dto);
  }
}
