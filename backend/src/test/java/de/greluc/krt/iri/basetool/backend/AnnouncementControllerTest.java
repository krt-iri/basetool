package de.greluc.krt.iri.basetool.backend;

import static org.junit.jupiter.api.Assertions.*;

import de.greluc.krt.iri.basetool.backend.controller.AnnouncementController;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class AnnouncementControllerTest {

  @Test
  void testJsonParsing_ContentOnly() throws Exception {
    String json = "{\"content\": \"test content\"}";

    JsonMapper mapper = JsonMapper.builder().build();

    try {
      AnnouncementController.AnnouncementRequest request =
          mapper.readValue(json, AnnouncementController.AnnouncementRequest.class);
      assertEquals("test content", request.getContent());
    } catch (Exception e) {
      fail("Parsing failed: " + e.getMessage());
    }
  }
}
