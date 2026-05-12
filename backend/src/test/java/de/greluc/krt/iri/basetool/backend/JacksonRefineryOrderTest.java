package de.greluc.krt.iri.basetool.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.greluc.krt.iri.basetool.backend.model.RefineryOrder;
import org.junit.jupiter.api.Test;
import java.util.*;

public class JacksonRefineryOrderTest {
    @Test
    public void testDeserialization() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        
        Map<String, Object> orderDto = new HashMap<>();
        orderDto.put("startedAt", "2026-03-27T18:35:59Z");
        orderDto.put("durationMinutes", 10);
        orderDto.put("expenses", 100);
        orderDto.put("location", Map.of("id", UUID.randomUUID().toString()));
        orderDto.put("mission", Map.of("id", UUID.randomUUID().toString()));
        orderDto.put("refiningMethod", Map.of("id", UUID.randomUUID().toString()));
        
        List<Map<String, Object>> goodsDto = new ArrayList<>();
        Map<String, Object> good = new HashMap<>();
        good.put("inputMaterial", Map.of("id", UUID.randomUUID().toString()));
        good.put("inputQuantity", 100);
        good.put("outputQuantity", 200);
        good.put("quality", 500);
        goodsDto.add(good);
        
        orderDto.put("goods", goodsDto);
        
        String json = mapper.writeValueAsString(orderDto);

        RefineryOrder order = mapper.readValue(json, RefineryOrder.class);
    }
}
