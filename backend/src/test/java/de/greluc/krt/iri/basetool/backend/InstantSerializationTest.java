package de.greluc.krt.iri.basetool.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class InstantSerializationTest {

    @Test
    void testInstantSerializationIsUtc() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        
        // Given an Instant object
        Instant time = Instant.parse("2026-03-03T12:00:00Z");

        // When serializing to JSON
        String json = objectMapper.writeValueAsString(time);

        // Then it should be represented in UTC (Z) format or as a numeric timestamp depending on config.
        // Assuming default Spring Boot Jackson setup uses ISO-8601 formatting or standard numeric.
        // With write_dates_as_timestamps = false (Spring Boot default), it writes standard ISO-8601 strings.
        assertThat(json).contains("2026-03-03T12:00:00Z");
    }
}
