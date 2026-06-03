package de.greluc.krt.iri.basetool.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.greluc.krt.iri.basetool.backend.model.FinanceType;
import de.greluc.krt.iri.basetool.backend.model.dto.MissionFinanceEntryDto;
import de.greluc.krt.iri.basetool.backend.model.dto.MissionParticipantDto;
import de.greluc.krt.iri.basetool.backend.model.dto.UserDto;
import de.greluc.krt.iri.basetool.backend.service.MissionFinanceEntryService;
import de.greluc.krt.iri.basetool.backend.service.OwnerScopeService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
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
 *   <li>authenticated officer → 201 with the nested participant's email stripped (H-1),
 *   <li>oversized {@code note} or out-of-range {@code amount} → 400 before the service is hit.
 * </ul>
 */
@SpringBootTest
class MissionFinanceEntryControllerSecurityTest {

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  @MockitoBean private MissionFinanceEntryService financeEntryService;
  @MockitoBean private OwnerScopeService ownerScopeService;
  @MockitoBean private JwtDecoder jwtDecoder;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  private static SimpleGrantedAuthority officer() {
    return new SimpleGrantedAuthority("ROLE_OFFICER");
  }

  private static SimpleGrantedAuthority member() {
    return new SimpleGrantedAuthority("ROLE_SQUADRON_MEMBER");
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
            // effectiveName == displayName by construction (User.getEffectiveName), never a
            // realname
            "Bob",
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
    when(ownerScopeService.canSeeMission(missionId)).thenReturn(true);
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
  }

  @Test
  void createFinanceEntry_anonymousOnInternalMission_isForbidden() throws Exception {
    UUID missionId = UUID.randomUUID();
    // canSeeMission returns false for an anonymous caller on an internal mission — the
    // @PreAuthorize
    // gate denies BEFORE the service is invoked.
    when(ownerScopeService.canSeeMission(missionId)).thenReturn(false);

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
  void createFinanceEntry_authenticatedOfficer_stripsParticipantEmail() throws Exception {
    UUID missionId = UUID.randomUUID();
    when(ownerScopeService.canSeeMission(missionId)).thenReturn(true);
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
            // the participant callsign still confirms which line was created…
            .andExpect(jsonPath("$.participant.user.username").value("bob.callsign"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // …but H-1 (refined): even an authenticated Officer must not get a peer's email back on create.
    org.junit.jupiter.api.Assertions.assertFalse(
        body.contains("bob@example.invalid"),
        "authenticated create response must not echo the participant's email");
  }

  @Test
  void createFinanceEntry_noteOver2000Chars_isBadRequest() throws Exception {
    UUID missionId = UUID.randomUUID();
    when(ownerScopeService.canSeeMission(missionId)).thenReturn(true);

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
    when(ownerScopeService.canSeeMission(missionId)).thenReturn(true);

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

  // ---------------------------------------------------------------------------
  // Audit finding H-1: the mission-finance READ endpoints used to be gated only by
  // isAuthenticated(), so any authenticated user could read any mission's ledger (and the nested
  // participant email) by UUID — a cross-squadron IDOR. They now carry
  // @ownerScopeService.canSeeMission AND redact participant PII for every caller (email is a
  // profile-only field — never echoed to a peer, not even to a Logistician/Officer).
  // ---------------------------------------------------------------------------

  @Test
  void getFinanceEntries_authenticatedNonMember_isForbidden() throws Exception {
    UUID missionId = UUID.randomUUID();
    // canSeeMission == false models a foreign squadron's internal mission: the @PreAuthorize gate
    // denies before the service is ever invoked.
    when(ownerScopeService.canSeeMission(missionId)).thenReturn(false);

    mockMvc
        .perform(
            get("/api/v1/missions/{id}/finance-entries", missionId)
                .with(jwt().authorities(member())))
        .andExpect(status().isForbidden());

    verify(financeEntryService, never()).getEntriesByMission(any(), any());
  }

  @Test
  void getFinanceEntriesSum_authenticatedNonMember_isForbidden() throws Exception {
    UUID missionId = UUID.randomUUID();
    when(ownerScopeService.canSeeMission(missionId)).thenReturn(false);

    mockMvc
        .perform(
            get("/api/v1/missions/{id}/finance-entries/sum", missionId)
                .with(jwt().authorities(member())))
        .andExpect(status().isForbidden());

    verify(financeEntryService, never()).calculateTotalSum(any());
  }

  @Test
  void getFinanceEntries_inScopeMember_redactsParticipantPii() throws Exception {
    UUID missionId = UUID.randomUUID();
    when(ownerScopeService.canSeeMission(missionId)).thenReturn(true);
    when(financeEntryService.getEntriesByMission(any(), any()))
        .thenReturn(new PageImpl<>(List.of(persistedEntryWithUserPii(missionId))));

    String body =
        mockMvc
            .perform(
                get("/api/v1/missions/{id}/finance-entries", missionId)
                    .with(jwt().authorities(member())))
            .andExpect(status().isOk())
            // public callsign stays visible
            .andExpect(jsonPath("$.content[0].participant.user.username").value("bob.callsign"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    org.junit.jupiter.api.Assertions.assertFalse(
        body.contains("bob@example.invalid"),
        "an in-scope member must not receive participant email through the finance ledger");
  }

  @Test
  void getFinanceEntries_officer_alsoRedactsParticipantEmail() throws Exception {
    UUID missionId = UUID.randomUUID();
    when(ownerScopeService.canSeeMission(missionId)).thenReturn(true);
    when(financeEntryService.getEntriesByMission(any(), any()))
        .thenReturn(new PageImpl<>(List.of(persistedEntryWithUserPii(missionId))));

    String body =
        mockMvc
            .perform(
                get("/api/v1/missions/{id}/finance-entries", missionId)
                    .with(jwt().authorities(officer())))
            .andExpect(status().isOk())
            // the public callsign still comes through — only the PII is stripped
            .andExpect(jsonPath("$.content[0].participant.user.username").value("bob.callsign"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // H-1 (refined): redaction is unconditional — even an Officer must not receive a peer's email
    // through the ledger; email is shown only to the user themselves in their own profile.
    org.junit.jupiter.api.Assertions.assertFalse(
        body.contains("bob@example.invalid"),
        "an Officer must not receive participant email through the finance ledger either");
  }
}
