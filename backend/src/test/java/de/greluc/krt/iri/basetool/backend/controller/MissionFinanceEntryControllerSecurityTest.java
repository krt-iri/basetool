package de.greluc.krt.iri.basetool.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.greluc.krt.iri.basetool.backend.model.FinanceType;
import de.greluc.krt.iri.basetool.backend.model.dto.MissionFinanceEntryDto;
import de.greluc.krt.iri.basetool.backend.model.dto.MissionParticipantDto;
import de.greluc.krt.iri.basetool.backend.model.dto.UserDto;
import de.greluc.krt.iri.basetool.backend.service.MissionFinanceEntryService;
import de.greluc.krt.iri.basetool.backend.service.SquadronScopeService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Security-focused MockMvc tests for {@link MissionFinanceEntryController#createFinanceEntry} — the
 * public create-entry endpoint that previously leaked participant PII to anonymous callers and
 * accepted writes against internal missions (audit finding C-2). The route is documented as guest-
 * callable so anonymous callers can record their own payout line; the rules pinned here are:
 *
 * <ul>
 *   <li>anonymous on non-internal mission → 201 with the nested user's email / real name / roles
 *       scrubbed,
 *   <li>anonymous on internal mission → 403, no service call,
 *   <li>authenticated officer → 201 with full PII (existing UI flow unchanged),
 *   <li>oversized {@code note} or out-of-range {@code amount} → 400 before the service is hit.
 * </ul>
 */
@SpringBootTest
class MissionFinanceEntryControllerSecurityTest {

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  @MockitoBean private MissionFinanceEntryService financeEntryService;
  @MockitoBean private SquadronScopeService squadronScopeService;
  @MockitoBean private JwtDecoder jwtDecoder;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  private static SimpleGrantedAuthority officer() {
    return new SimpleGrantedAuthority("ROLE_OFFICER");
  }

  /**
   * Builds a finance-entry DTO whose nested participant carries a registered user with PII
   * populated — the response shape the controller assembles after a successful service call.
   */
  private static MissionFinanceEntryDto persistedEntryWithUserPii(UUID missionId) {
    UserDto user =
        new UserDto(
            UUID.randomUUID(),
            "bob.callsign",
            "Bob",
            "Bob Builder",
            "Bob",
            "Builder",
            "bob@example.invalid",
            null,
            null,
            null,
            null,
            null,
            false,
            false,
            true,
            null,
            1L,
            null);
    MissionParticipantDto participant =
        new MissionParticipantDto(
            UUID.randomUUID(), user, null, null, null, null, null, null, null, null, 1L);
    return new MissionFinanceEntryDto(
        UUID.randomUUID(),
        missionId,
        participant,
        "note",
        FinanceType.INCOME,
        new BigDecimal("500.00"),
        1L);
  }

  @Test
  void createFinanceEntry_anonymousOnPublicMission_returnsSlimAckWithoutParticipant()
      throws Exception {
    UUID missionId = UUID.randomUUID();
    when(squadronScopeService.canSeeMission(missionId)).thenReturn(true);
    when(financeEntryService.createEntry(any())).thenReturn(persistedEntryWithUserPii(missionId));

    String body =
        mockMvc
            .perform(
                post("/api/v1/finance-entries")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"missionId\":\""
                            + missionId
                            + "\",\"participantId\":\""
                            + UUID.randomUUID()
                            + "\",\"type\":\"INCOME\",\"amount\":500.00,\"note\":\"my-line\"}"))
            .andExpect(status().isCreated())
            // M-5: anonymous response is now a slim acknowledgement — participant is dropped
            // entirely (no more nested user / email vector to keep clean), version is dropped
            // (anonymous cannot update). id / missionId / type / amount / note stay so the
            // caller has enough to confirm the persisted row.
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.missionId").value(missionId.toString()))
            .andExpect(jsonPath("$.type").value("INCOME"))
            .andExpect(jsonPath("$.participant").isEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();

    org.junit.jupiter.api.Assertions.assertFalse(
        body.contains("bob.callsign"), "anonymous slim ack must not carry the participant at all");
    org.junit.jupiter.api.Assertions.assertFalse(
        body.contains("bob@example.invalid"), "anonymous ack must not leak participant email");
    org.junit.jupiter.api.Assertions.assertFalse(
        body.contains("\"firstName\":\"Bob\""),
        "anonymous ack must not leak participant first name");
    org.junit.jupiter.api.Assertions.assertFalse(
        body.contains("\"lastName\":\"Builder\""),
        "anonymous ack must not leak participant last name");
  }

  @Test
  void createFinanceEntry_anonymousOnInternalMission_isForbidden() throws Exception {
    UUID missionId = UUID.randomUUID();
    // canSeeMission returns false for an anonymous caller on an internal mission — the
    // @PreAuthorize
    // gate denies BEFORE the service is invoked.
    when(squadronScopeService.canSeeMission(missionId)).thenReturn(false);

    mockMvc
        .perform(
            post("/api/v1/finance-entries")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"missionId\":\""
                        + missionId
                        + "\",\"participantId\":\""
                        + UUID.randomUUID()
                        + "\",\"type\":\"INCOME\",\"amount\":500.00}"))
        .andExpect(status().isForbidden());

    verify(financeEntryService, never()).createEntry(any());
  }

  @Test
  void createFinanceEntry_authenticatedOfficer_keepsFullPii() throws Exception {
    UUID missionId = UUID.randomUUID();
    when(squadronScopeService.canSeeMission(missionId)).thenReturn(true);
    when(financeEntryService.createEntry(any())).thenReturn(persistedEntryWithUserPii(missionId));

    String body =
        mockMvc
            .perform(
                post("/api/v1/finance-entries")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"missionId\":\""
                            + missionId
                            + "\",\"participantId\":\""
                            + UUID.randomUUID()
                            + "\",\"type\":\"INCOME\",\"amount\":500.00}")
                    .with(jwt().authorities(officer())))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    org.junit.jupiter.api.Assertions.assertTrue(
        body.contains("bob@example.invalid"), "authenticated officer must see participant email");
    org.junit.jupiter.api.Assertions.assertTrue(
        body.contains("\"firstName\":\"Bob\""),
        "authenticated officer must see participant first name");
    org.junit.jupiter.api.Assertions.assertTrue(
        body.contains("\"lastName\":\"Builder\""),
        "authenticated officer must see participant last name");
  }

  @Test
  void createFinanceEntry_noteOver2000Chars_isBadRequest() throws Exception {
    UUID missionId = UUID.randomUUID();
    when(squadronScopeService.canSeeMission(missionId)).thenReturn(true);

    String oversizedNote = "a".repeat(2001);
    mockMvc
        .perform(
            post("/api/v1/finance-entries")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"missionId\":\""
                        + missionId
                        + "\",\"participantId\":\""
                        + UUID.randomUUID()
                        + "\",\"type\":\"INCOME\",\"amount\":500.00,\"note\":\""
                        + oversizedNote
                        + "\"}"))
        .andExpect(status().isBadRequest());

    verify(financeEntryService, never()).createEntry(any());
  }

  @Test
  void createFinanceEntry_amountOverCap_isBadRequest() throws Exception {
    UUID missionId = UUID.randomUUID();
    when(squadronScopeService.canSeeMission(missionId)).thenReturn(true);

    mockMvc
        .perform(
            post("/api/v1/finance-entries")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"missionId\":\""
                        + missionId
                        + "\",\"participantId\":\""
                        + UUID.randomUUID()
                        + "\",\"type\":\"INCOME\",\"amount\":1000000000.01}"))
        .andExpect(status().isBadRequest());

    verify(financeEntryService, never()).createEntry(any());
  }
}
