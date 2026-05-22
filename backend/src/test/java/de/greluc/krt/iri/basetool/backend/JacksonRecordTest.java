package de.greluc.krt.iri.basetool.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.greluc.krt.iri.basetool.backend.model.dto.MaterialDto;
import org.junit.jupiter.api.Test;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

public class JacksonRecordTest {
  @Test
  public void testSpel() throws Exception {
    MaterialDto dto =
        new MaterialDto(
            null, "Test", "RAW", "SCU", "desc", null, null, true, true, true, false, false, false,
            1L);
    ObjectMapper mapper = new ObjectMapper();
    String json = mapper.writeValueAsString(dto);

    ExpressionParser parser = new SpelExpressionParser();
    Boolean isVolatileQt = parser.parseExpression("isVolatileQt").getValue(dto, Boolean.class);

    Boolean isIllegal = parser.parseExpression("isIllegal").getValue(dto, Boolean.class);
  }
}
