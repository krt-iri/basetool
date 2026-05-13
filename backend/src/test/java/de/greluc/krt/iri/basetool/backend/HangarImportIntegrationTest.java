package de.greluc.krt.iri.basetool.backend;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.greluc.krt.iri.basetool.backend.model.Ship;
import de.greluc.krt.iri.basetool.backend.model.ShipType;
import de.greluc.krt.iri.basetool.backend.model.User;
import de.greluc.krt.iri.basetool.backend.model.dto.FleetviewImportResponseDto;
import de.greluc.krt.iri.basetool.backend.repository.ShipRepository;
import de.greluc.krt.iri.basetool.backend.repository.ShipTypeRepository;
import de.greluc.krt.iri.basetool.backend.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class HangarImportIntegrationTest {

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  @Autowired private ShipRepository shipRepository;

  @Autowired private ShipTypeRepository shipTypeRepository;

  @Autowired private UserRepository userRepository;

  @MockitoBean private JwtDecoder jwtDecoder;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private User user1;
  private ShipType type135c;
  private ShipType typeZeus;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

    user1 = new User();
    user1.setId(UUID.randomUUID());
    user1.setUsername("importuser");
    userRepository.save(user1);

    type135c = new ShipType();
    type135c.setName("135c");
    type135c = shipTypeRepository.save(type135c);

    typeZeus = new ShipType();
    typeZeus.setName("zeus mk ii mr");
    typeZeus = shipTypeRepository.save(typeZeus);
  }

  // -------------------------------------------------------------------------
  // Success: matched ships are imported, unmatched ships are reported
  // -------------------------------------------------------------------------

  @Test
  void importFleetview_success_importsMatchedShips() throws Exception {
    // Given
    String json =
        """
        [
          {"name":"135c","shipname":"","type":"ship"},
          {"name":"zeus mk ii mr","shipname":"My Zeus","type":"ship"},
          {"name":"unknown xz99","shipname":"","type":"ship"}
        ]
        """;
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "fleetview.json", "application/json", json.getBytes(StandardCharsets.UTF_8));

    // When
    String response =
        mockMvc
            .perform(
                multipart("/api/v1/hangar/import/fleetview")
                    .file(file)
                    .with(jwt().jwt(builder -> builder.subject(user1.getId().toString()))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Then
    FleetviewImportResponseDto result =
        objectMapper.readValue(response, FleetviewImportResponseDto.class);
    assertEquals(2, result.importedCount());
    assertEquals(1, result.skippedCount());
    assertEquals(0, result.duplicateCount());
    assertTrue(result.skippedShips().contains("unknown xz99"));

    // Verify ships are actually in the DB
    assertEquals(2, shipRepository.findByOwnerId(user1.getId()).size());
  }

  // -------------------------------------------------------------------------
  // Unauthenticated request → 401
  // -------------------------------------------------------------------------

  @Test
  void importFleetview_unauthenticated_returns401() throws Exception {
    // Given
    String json =
        """
        [{"name":"135c","shipname":"","type":"ship"}]
        """;
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "fleetview.json", "application/json", json.getBytes(StandardCharsets.UTF_8));

    // When / Then
    mockMvc
        .perform(multipart("/api/v1/hangar/import/fleetview").file(file))
        .andExpect(status().isUnauthorized());
  }

  // -------------------------------------------------------------------------
  // Empty file → 400
  // -------------------------------------------------------------------------

  @Test
  void importFleetview_emptyFile_returns400() throws Exception {
    // Given
    MockMultipartFile emptyFile =
        new MockMultipartFile("file", "fleetview.json", "application/json", new byte[0]);

    // When / Then
    mockMvc
        .perform(
            multipart("/api/v1/hangar/import/fleetview")
                .file(emptyFile)
                .with(jwt().jwt(builder -> builder.subject(user1.getId().toString()))))
        .andExpect(status().isBadRequest());
  }

  // -------------------------------------------------------------------------
  // Invalid JSON → 400
  // -------------------------------------------------------------------------

  @Test
  void importFleetview_invalidJson_returns400() throws Exception {
    // Given
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "fleetview.json",
            "application/json",
            "NOT JSON".getBytes(StandardCharsets.UTF_8));

    // When / Then
    mockMvc
        .perform(
            multipart("/api/v1/hangar/import/fleetview")
                .file(file)
                .with(jwt().jwt(builder -> builder.subject(user1.getId().toString()))))
        .andExpect(status().isBadRequest());
  }

  // -------------------------------------------------------------------------
  // Re-import (same file twice): already owned ship → no new ship created
  // -------------------------------------------------------------------------

  @Test
  void importFleetview_reImport_noNewShipCreatedWhenAlreadyPresent() throws Exception {
    // Given: 135c is already in user's hangar (1 existing)
    Ship existing = new Ship();
    existing.setShipType(type135c);
    existing.setOwner(user1);
    existing.setInsurance("LTI");
    shipRepository.save(existing);

    String json =
        """
        [{"name":"135c","shipname":"","type":"ship"}]
        """;
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "fleetview.json", "application/json", json.getBytes(StandardCharsets.UTF_8));

    // When: same file imported again
    String response =
        mockMvc
            .perform(
                multipart("/api/v1/hangar/import/fleetview")
                    .file(file)
                    .with(jwt().jwt(builder -> builder.subject(user1.getId().toString()))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Then: no new ship created (hangar count already >= JSON count)
    FleetviewImportResponseDto result =
        objectMapper.readValue(response, FleetviewImportResponseDto.class);
    assertEquals(0, result.importedCount());
    // duplicateCount = ships for which hangar count already met JSON count
    assertEquals(1, result.duplicateCount());

    // Verify only 1 ship in DB (no duplicate)
    assertEquals(1, shipRepository.findByOwnerId(user1.getId()).size());
  }

  // -------------------------------------------------------------------------
  // Partial duplicate: JSON has 2× same ship, hangar has 1 → 1 new created
  // -------------------------------------------------------------------------

  @Test
  void importFleetview_partialDuplicate_createsOnlyMissingShips() throws Exception {
    // Given: 1× 135c already in hangar
    Ship existing = new Ship();
    existing.setShipType(type135c);
    existing.setOwner(user1);
    existing.setInsurance("LTI");
    shipRepository.save(existing);

    // JSON requests 2× 135c
    String json =
        """
        [
          {"name":"135c","shipname":"","type":"ship"},
          {"name":"135c","shipname":"","type":"ship"}
        ]
        """;
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "fleetview.json", "application/json", json.getBytes(StandardCharsets.UTF_8));

    // When
    String response =
        mockMvc
            .perform(
                multipart("/api/v1/hangar/import/fleetview")
                    .file(file)
                    .with(jwt().jwt(builder -> builder.subject(user1.getId().toString()))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Then: 1 new 135c created (2 in JSON - 1 in hangar)
    FleetviewImportResponseDto result =
        objectMapper.readValue(response, FleetviewImportResponseDto.class);
    assertEquals(1, result.importedCount());
    assertEquals(0, result.duplicateCount());

    // Verify 2 ships in DB
    assertEquals(2, shipRepository.findByOwnerId(user1.getId()).size());
  }

  // -------------------------------------------------------------------------
  // Case-insensitive matching
  // -------------------------------------------------------------------------

  @Test
  void importFleetview_caseInsensitiveMatch_importsShip() throws Exception {
    // Given: fleetview.json uses uppercase "135C" but DB has "135c"
    String json =
        """
        [{"name":"135C","shipname":"","type":"ship"}]
        """;
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "fleetview.json", "application/json", json.getBytes(StandardCharsets.UTF_8));

    // When
    String response =
        mockMvc
            .perform(
                multipart("/api/v1/hangar/import/fleetview")
                    .file(file)
                    .with(jwt().jwt(builder -> builder.subject(user1.getId().toString()))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Then
    FleetviewImportResponseDto result =
        objectMapper.readValue(response, FleetviewImportResponseDto.class);
    assertEquals(1, result.importedCount());
    assertEquals(0, result.skippedCount());
  }

  // -------------------------------------------------------------------------
  // Individual ship name from shipname field is set
  // -------------------------------------------------------------------------

  @Test
  void importFleetview_setsIndividualShipName() throws Exception {
    // Given
    String json =
        """
        [{"name":"zeus mk ii mr","shipname":"Stella Aeterna","type":"ship"}]
        """;
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "fleetview.json", "application/json", json.getBytes(StandardCharsets.UTF_8));

    // When
    mockMvc
        .perform(
            multipart("/api/v1/hangar/import/fleetview")
                .file(file)
                .with(jwt().jwt(builder -> builder.subject(user1.getId().toString()))))
        .andExpect(status().isOk());

    // Then
    Ship imported = shipRepository.findByOwnerId(user1.getId()).stream().findFirst().orElseThrow();
    assertEquals("Stella Aeterna", imported.getName());
  }
}
