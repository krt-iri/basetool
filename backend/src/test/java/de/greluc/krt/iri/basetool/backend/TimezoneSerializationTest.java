package de.greluc.krt.iri.basetool.backend;

import static org.assertj.core.api.Assertions.assertThat;

import de.greluc.krt.iri.basetool.backend.model.dto.HandoverReportPreviewRequestDto;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Round-trip serialisation tests for the temporal types used at the REST boundary.
 *
 * <p>CLAUDE.md mandates "store/process as {@code Instant} or {@code OffsetDateTime}" plus "write
 * serialization tests for timezone behavior". These tests pin down the contract:
 *
 * <ul>
 *   <li>{@link Instant} is always rendered as a UTC string ending in {@code Z}, never with a
 *       server-default offset.
 *   <li>{@link OffsetDateTime} <em>serialises</em> with whichever offset the in-memory value
 *       carries (positive, negative or {@code Z}) — but Jackson's default {@code
 *       ADJUST_DATES_TO_CONTEXT_TIME_ZONE} setting <em>normalises the value to UTC on
 *       deserialise</em>. The latter matches the CLAUDE.md "times in UTC" contract: no matter what
 *       offset a client sends, the server-side instant is stored in UTC and the original offset is
 *       dropped.
 *   <li>{@link LocalDateTime} in {@link HandoverReportPreviewRequestDto} is the documented
 *       exception (see DTO Javadoc): the value the user typed in their local time zone is echoed
 *       back unchanged, with no implicit conversion to UTC. The DTO is parsed and re-serialised
 *       here to make sure no Jackson default silently adds an offset.
 *   <li>DST boundary timestamps (Europe/Berlin spring-forward) survive round-tripping through
 *       {@link OffsetDateTime} with their wall-clock value intact.
 * </ul>
 *
 * <p>All tests configure the {@link JsonMapper} the same way Spring Boot does by default (java.time
 * support is built into Jackson 3, {@code WRITE_DATES_AS_TIMESTAMPS} disabled) so that any
 * regression in this configuration here also surfaces in production.
 */
class TimezoneSerializationTest {

  private JsonMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = JsonMapper.builder().disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS).build();
  }

  // -----------------------------------------------------------------------
  // Instant — UTC contract
  // -----------------------------------------------------------------------

  @Test
  void instant_serializesAsIso8601WithZSuffix() throws Exception {
    // Given
    Instant time = Instant.parse("2026-03-03T12:00:00Z");

    // When
    String json = objectMapper.writeValueAsString(time);

    // Then
    assertThat(json).isEqualTo("\"2026-03-03T12:00:00Z\"");
  }

  @Test
  void instant_roundTripPreservesValue() throws Exception {
    // Given a series of Instants spanning epoch, distant-past and distant-future
    Instant[] samples = {
      Instant.parse("1970-01-01T00:00:00Z"),
      Instant.parse("2026-03-03T12:34:56Z"),
      Instant.parse("2199-12-31T23:59:59Z"),
    };

    // When/Then each survives a JSON round-trip without drift
    for (Instant original : samples) {
      String json = objectMapper.writeValueAsString(original);
      Instant parsed = objectMapper.readValue(json, Instant.class);
      assertThat(parsed).isEqualTo(original);
    }
  }

  @Test
  void instant_deserializesFromIsoStringWithNanos() throws Exception {
    // Given a JSON string carrying sub-second precision
    String json = "\"2026-03-03T12:00:00.123456789Z\"";

    // When
    Instant parsed = objectMapper.readValue(json, Instant.class);

    // Then nanos round-trip cleanly
    assertThat(parsed.getEpochSecond())
        .isEqualTo(Instant.parse("2026-03-03T12:00:00Z").getEpochSecond());
    assertThat(parsed.getNano()).isEqualTo(123_456_789);
  }

  @Test
  void instant_deserializesFromOffsetSuffix() throws Exception {
    // Given a JSON string with an explicit non-UTC offset (e.g. a client in CEST sending data)
    String json = "\"2026-03-03T14:00:00+02:00\"";

    // When
    Instant parsed = objectMapper.readValue(json, Instant.class);

    // Then the value is normalised to UTC — Instant has no concept of offset, so 14:00+02:00
    // collapses to 12:00Z
    assertThat(parsed).isEqualTo(Instant.parse("2026-03-03T12:00:00Z"));
  }

  // -----------------------------------------------------------------------
  // OffsetDateTime — offset preservation
  // -----------------------------------------------------------------------

  @Test
  void offsetDateTime_serialisesPositiveOffsetVerbatim() throws Exception {
    // Given a value in CEST (UTC+02:00)
    OffsetDateTime original = OffsetDateTime.of(2026, 3, 3, 14, 0, 0, 0, ZoneOffset.ofHours(2));

    // When
    String json = objectMapper.writeValueAsString(original);

    // Then the offset appears verbatim in the JSON payload
    assertThat(json).contains("+02:00");
    assertThat(json).contains("2026-03-03T14:00:00");
  }

  @Test
  void offsetDateTime_serialisesNegativeOffsetVerbatim() throws Exception {
    // Given a value in EST (UTC-05:00)
    OffsetDateTime original = OffsetDateTime.of(2026, 3, 3, 7, 0, 0, 0, ZoneOffset.ofHours(-5));

    // When
    String json = objectMapper.writeValueAsString(original);

    // Then
    assertThat(json).contains("-05:00");
    assertThat(json).contains("2026-03-03T07:00:00");
  }

  @Test
  void offsetDateTime_serialisesZuluOffsetVerbatim() throws Exception {
    // Given a value already in UTC
    OffsetDateTime original = OffsetDateTime.of(2026, 3, 3, 12, 0, 0, 0, ZoneOffset.UTC);

    // When
    String json = objectMapper.writeValueAsString(original);

    // Then
    assertThat(json).contains("Z");
    assertThat(json).contains("2026-03-03T12:00:00");
  }

  @Test
  void offsetDateTime_deserialiseNormalisesPositiveOffsetToUtc() throws Exception {
    // Given a JSON payload with an explicit +02:00 offset
    String json = "\"2026-03-03T14:00:00+02:00\"";

    // When
    OffsetDateTime parsed = objectMapper.readValue(json, OffsetDateTime.class);

    // Then the absolute moment is preserved, but the offset is normalised to UTC.
    // This matches Jackson's default ADJUST_DATES_TO_CONTEXT_TIME_ZONE=true and the
    // CLAUDE.md contract that times are stored / processed in UTC server-side.
    assertThat(parsed.toInstant()).isEqualTo(Instant.parse("2026-03-03T12:00:00Z"));
    assertThat(parsed.getOffset()).isEqualTo(ZoneOffset.UTC);
  }

  @Test
  void offsetDateTime_deserialiseNormalisesNegativeOffsetToUtc() throws Exception {
    // Given a JSON payload with a -05:00 offset
    String json = "\"2026-03-03T07:00:00-05:00\"";

    // When
    OffsetDateTime parsed = objectMapper.readValue(json, OffsetDateTime.class);

    // Then same moment in UTC
    assertThat(parsed.toInstant()).isEqualTo(Instant.parse("2026-03-03T12:00:00Z"));
    assertThat(parsed.getOffset()).isEqualTo(ZoneOffset.UTC);
  }

  @Test
  void offsetDateTime_andInstant_representSameMoment() throws Exception {
    // Given an OffsetDateTime in CEST
    OffsetDateTime odt = OffsetDateTime.of(2026, 3, 3, 14, 0, 0, 0, ZoneOffset.ofHours(2));

    // When serialised through Jackson and re-parsed as Instant
    String json = objectMapper.writeValueAsString(odt);
    Instant asInstant = objectMapper.readValue(json, Instant.class);

    // Then both refer to the same moment in absolute time
    assertThat(asInstant).isEqualTo(odt.toInstant());
    assertThat(asInstant).isEqualTo(Instant.parse("2026-03-03T12:00:00Z"));
  }

  // -----------------------------------------------------------------------
  // LocalDateTime in HandoverReportPreviewRequestDto — wall-clock pass-through
  // -----------------------------------------------------------------------

  @Test
  void handoverReportPreviewDto_keepsLocalDateTimeWithoutOffsetAcrossRoundTrip() throws Exception {
    // Given the JSON the frontend sends when the user typed "03.03.2026 14:00" into the modal
    String json =
        "{"
            + "\"jobOrderNumber\":\"#42\","
            + "\"handoverTime\":\"2026-03-03T14:00:00\","
            + "\"recipientHandle\":\"HanSolo\","
            + "\"items\":[]"
            + "}";

    // When
    HandoverReportPreviewRequestDto dto =
        objectMapper.readValue(json, HandoverReportPreviewRequestDto.class);
    String reSerialised = objectMapper.writeValueAsString(dto);

    // Then no implicit timezone conversion occurs — Jackson must not append "Z" or any offset
    assertThat(dto.handoverTime()).isEqualTo(LocalDateTime.of(2026, 3, 3, 14, 0));
    assertThat(reSerialised)
        .contains("\"handoverTime\":\"2026-03-03T14:00:00\"")
        .doesNotContain("2026-03-03T14:00:00Z")
        .doesNotContain("2026-03-03T14:00:00+");
  }

  @Test
  void handoverReportPreviewDto_acceptsLocalDateTimeWithoutOffsetEvenWhenServerInUtc()
      throws Exception {
    // Given a DTO constructed in Java (simulating service-layer use)
    HandoverReportPreviewRequestDto dto =
        new HandoverReportPreviewRequestDto(
            "#1", LocalDateTime.of(2026, 3, 3, 14, 0), "HanSolo", List.of());

    // When
    String json = objectMapper.writeValueAsString(dto);
    HandoverReportPreviewRequestDto roundTripped =
        objectMapper.readValue(json, HandoverReportPreviewRequestDto.class);

    // Then the wall-clock value survives unchanged
    assertThat(roundTripped.handoverTime()).isEqualTo(LocalDateTime.of(2026, 3, 3, 14, 0));
  }

  // -----------------------------------------------------------------------
  // DST edge case — spring-forward boundary
  // -----------------------------------------------------------------------

  @Test
  void offsetDateTime_dstSpringForwardBoundary_preservesAbsoluteMomentInUtc() throws Exception {
    // Europe/Berlin springs forward on 2026-03-29 at 02:00 local (CET → CEST).
    // The two values below straddle that boundary: one is 01:30 CET (+01:00), the other
    // 03:30 CEST (+02:00). After the transition the offset changes, and the missing
    // 02:00..03:00 window means the two moments are only one hour apart in absolute time,
    // not two — this test pins down that property.
    OffsetDateTime beforeDst = OffsetDateTime.of(2026, 3, 29, 1, 30, 0, 0, ZoneOffset.ofHours(1));
    OffsetDateTime afterDst = OffsetDateTime.of(2026, 3, 29, 3, 30, 0, 0, ZoneOffset.ofHours(2));

    // When
    OffsetDateTime beforeParsed =
        objectMapper.readValue(objectMapper.writeValueAsString(beforeDst), OffsetDateTime.class);
    OffsetDateTime afterParsed =
        objectMapper.readValue(objectMapper.writeValueAsString(afterDst), OffsetDateTime.class);

    // Then both round-trips preserve the absolute moment (the offset itself is normalised
    // to UTC by Jackson, see offsetDateTime_deserialise* tests above).
    assertThat(beforeParsed.toInstant()).isEqualTo(beforeDst.toInstant());
    assertThat(afterParsed.toInstant()).isEqualTo(afterDst.toInstant());

    // And the two moments are exactly one hour apart in absolute time (NOT two hours,
    // because the missing 02:00..03:00 window was skipped by DST).
    assertThat(afterParsed.toInstant().getEpochSecond() - beforeParsed.toInstant().getEpochSecond())
        .isEqualTo(3600);
  }
}
