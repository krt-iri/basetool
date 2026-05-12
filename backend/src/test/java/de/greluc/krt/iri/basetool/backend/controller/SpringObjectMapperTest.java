package de.greluc.krt.iri.basetool.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.greluc.krt.iri.basetool.backend.model.dto.RefineryOrderDto;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.*;

public class SpringObjectMapperTest {

    private ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    public void testDeserialize() throws Exception {
        Map<String, Object> orderDto = new HashMap<>();
        
        OffsetDateTime startedAtTime = OffsetDateTime.now();
        orderDto.put("startedAt", startedAtTime.toInstant().toString());
        
        orderDto.put("location", Map.of("id", UUID.randomUUID()));
        orderDto.put("mission", Map.of("id", UUID.randomUUID()));
        orderDto.put("durationMinutes", 100);
        orderDto.put("refiningMethod", Map.of("id", UUID.randomUUID()));
        orderDto.put("expenses", 500);
        orderDto.put("status", "OPEN");

        List<Map<String, Object>> goodsDto = new ArrayList<>();
        Map<String, Object> good = new HashMap<>();
        good.put("inputMaterial", Map.of("id", UUID.randomUUID()));
        good.put("inputQuantity", 100.0);
        good.put("outputMaterial", Map.of("id", UUID.randomUUID()));
        good.put("outputQuantity", 50.0);
        good.put("quality", 100);
        goodsDto.add(good);

        orderDto.put("goods", goodsDto);

        String json = mapper.writeValueAsString(orderDto);

        RefineryOrderDto dto = mapper.readValue(json, RefineryOrderDto.class);
    }
}
