package de.greluc.krt.iri.basetool.backend;

import de.greluc.krt.iri.basetool.backend.model.RefineryOrder;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

public class JacksonRefineryOrderTest {
  @Test
  public void testDeserialization() throws Exception {
    JsonMapper mapper = JsonMapper.builder().build();

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
