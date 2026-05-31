package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.model.dto.RefineryOrderDto;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

public class RefineryOrderJsonTest {
  @Test
  public void testDeserialize() throws Exception {
    // Mirror JacksonConfig: the app disables FAIL_ON_NULL_FOR_PRIMITIVES (Jackson 2 leniency) so an
    // absent nested primitive such as LocationDto.hidden defaults to false instead of failing.
    JsonMapper mapper =
        JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
            .build();

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
