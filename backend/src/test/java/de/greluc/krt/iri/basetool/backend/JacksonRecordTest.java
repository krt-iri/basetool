package de.greluc.krt.iri.basetool.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import de.greluc.krt.iri.basetool.backend.model.dto.MaterialDto;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

public class JacksonRecordTest {
    @Test
    public void testSpel() throws Exception {
        MaterialDto dto = new MaterialDto(null, "Test", "RAW", "SCU", "desc", null, null, true, true, true, 1L);
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(dto);
        System.out.println("[DEBUG_LOG] JSON: " + json);
        
        ExpressionParser parser = new SpelExpressionParser();
        Boolean isVolatileQt = parser.parseExpression("isVolatileQt").getValue(dto, Boolean.class);
        System.out.println("[DEBUG_LOG] SpEL isVolatileQt: " + isVolatileQt);
        
        Boolean isIllegal = parser.parseExpression("isIllegal").getValue(dto, Boolean.class);
        System.out.println("[DEBUG_LOG] SpEL isIllegal: " + isIllegal);
    }
}
