package de.greluc.krt.iri.basetool.backend;

import de.greluc.krt.iri.basetool.backend.model.dto.MaterialDto;
import org.junit.jupiter.api.Test;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import tools.jackson.databind.json.JsonMapper;

public class JacksonRecordTest {
  @Test
  public void testSpel() throws Exception {
    MaterialDto dto =
        new MaterialDto(
            null, "Test", "RAW", "SCU", "desc", null, null, true, true, true, false, false, false,
            true, 1L);
    JsonMapper mapper = JsonMapper.builder().build();
    String json = mapper.writeValueAsString(dto);

    ExpressionParser parser = new SpelExpressionParser();
    Boolean isVolatileQt = parser.parseExpression("isVolatileQt").getValue(dto, Boolean.class);

    Boolean isIllegal = parser.parseExpression("isIllegal").getValue(dto, Boolean.class);
  }
}
