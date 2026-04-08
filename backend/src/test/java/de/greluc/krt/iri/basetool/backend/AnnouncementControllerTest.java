package de.greluc.krt.iri.basetool.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.greluc.krt.iri.basetool.backend.controller.AnnouncementController;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AnnouncementControllerTest {

    @Test
    void testJsonParsing_ContentOnly() throws Exception {
        String json = "{\"content\": \"test content\"}";
        
        ObjectMapper mapper = new ObjectMapper();
        
        try {
            AnnouncementController.AnnouncementRequest request = mapper.readValue(json, AnnouncementController.AnnouncementRequest.class);
            assertEquals("test content", request.getContent());
        } catch (Exception e) {
            fail("Parsing failed: " + e.getMessage());
        }
    }
}
