package de.greluc.krt.iri.basetool.frontend.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.greluc.krt.iri.basetool.frontend.model.dto.MissionListDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.OperationDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.OperationFinanceDto;
import de.greluc.krt.iri.basetool.frontend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.frontend.service.BackendApiClient;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Verifies that the operations index and operation-detail templates render status values through
 * the i18n bundle instead of leaking the raw backend enum strings. The i18n bug was: the
 * create/edit dropdowns went through {@code operation.status.planned} etc., but the displayed
 * status (in the table, the detail box, and the embedded missions table) was rendered as {@code
 * th:text="${op.status}"}, dumping the raw enum name into the page. These tests pin the fix: send
 * an Operation/Mission with status {@code PLANNED}/{@code COMPLETED}/{@code CANCELLED} through the
 * page and assert the rendered HTML contains the German translation, not the raw uppercase enum
 * value.
 */
@SpringBootTest
@SuppressWarnings("unchecked")
class OperationPageControllerMvcTest {

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  @MockitoBean private BackendApiClient backendApiClient;

  @MockitoBean
  private org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
      clientRegistrationRepository;

  @BeforeEach
  void setup() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  // ── /operations (list) ──────────────────────────────────────────────────

  @Test
  @WithMockUser(roles = "OFFICER")
  void operationsList_rendersOperationStatusViaI18n_inGerman() throws Exception {
    // Backend returns one operation with the raw enum status. The previous
    // bug rendered this verbatim in the table cell — we now expect the
    // German translation "GEPLANT".
    OperationDto op =
        new OperationDto(UUID.randomUUID(), "Op Alpha", "First op", "PLANNED", null, 0L);
    PageResponse<OperationDto> page =
        new PageResponse<>(List.of(op), 0, 20, 1L, 1, List.of("createdAt,desc"));
    when(backendApiClient.get(
            startsWith("/api/v1/operations?"), any(ParameterizedTypeReference.class), anyBoolean()))
        .thenReturn(page);

    mockMvc
        .perform(get("/operations").locale(Locale.GERMAN))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("GEPLANT")))
        // The raw enum name must NOT survive into the table cell.
        // (The string can still appear in the <option value="PLANNED">
        // attribute of the create modal, so we assert on the visible
        // text by looking for the wrapping <td> pattern.)
        .andExpect(content().string(not(containsString(">PLANNED<"))));
  }

  @Test
  @WithMockUser(roles = "OFFICER")
  void operationsList_rendersOperationStatusViaI18n_inEnglish() throws Exception {
    OperationDto op =
        new OperationDto(UUID.randomUUID(), "Op Alpha", "First op", "ACTIVE", null, 0L);
    PageResponse<OperationDto> page =
        new PageResponse<>(List.of(op), 0, 20, 1L, 1, List.of("createdAt,desc"));
    when(backendApiClient.get(
            startsWith("/api/v1/operations?"), any(ParameterizedTypeReference.class), anyBoolean()))
        .thenReturn(page);

    // English locale resolves to messages_en.properties → "ACTIVE".
    // Coincidentally the same casing as the backend enum, so this test
    // can't tell raw-vs-translated apart for English. We still run it
    // to make sure the lookup does not throw for the EN bundle.
    mockMvc
        .perform(get("/operations").locale(Locale.ENGLISH))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("ACTIVE")));
  }

  // ── /operations/{id} (detail) ───────────────────────────────────────────

  // ── /operations/{id} (detail) — canEdit-driven form visibility ──────────

  @Test
  @WithMockUser(roles = "SQUADRON_MEMBER")
  void operationDetail_readOnlyUser_seesDisabledFormAndNoSaveButton() throws Exception {
    UUID opId = UUID.randomUUID();
    stubDetailEndpoints(opId, new OperationDto(opId, "Op Read", "ro", "PLANNED", null, 0L));

    mockMvc
        .perform(get("/operations/" + opId).locale(Locale.GERMAN))
        .andExpect(status().isOk())
        // Form is rendered but inputs are disabled for non-editors.
        .andExpect(content().string(containsString("id=\"operation-form\"")))
        .andExpect(content().string(containsString("id=\"op-name\"")))
        .andExpect(content().string(containsString("disabled")))
        // No submit button anywhere — Save is gated on canEdit.
        .andExpect(content().string(not(containsString("form=\"operation-form\""))));
  }

  @Test
  @WithMockUser(roles = "MISSION_MANAGER")
  void operationDetail_missionManager_seesEnabledFormAndSaveButton() throws Exception {
    UUID opId = UUID.randomUUID();
    stubDetailEndpoints(opId, new OperationDto(opId, "Op Edit", "rw", "PLANNED", null, 0L));

    mockMvc
        .perform(get("/operations/" + opId).locale(Locale.GERMAN))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("id=\"operation-form\"")))
        // The submit button references the form by id — its presence is
        // the canonical signal that canEdit was true on the model.
        .andExpect(content().string(containsString("form=\"operation-form\"")));
  }

  private void stubDetailEndpoints(UUID opId, OperationDto operation) {
    when(backendApiClient.get(
            eq("/api/v1/operations/" + opId), eq(OperationDto.class), anyBoolean()))
        .thenReturn(operation);
    when(backendApiClient.get(
            contains("/api/v1/missions/search?operationId=" + opId),
            any(ParameterizedTypeReference.class),
            anyBoolean()))
        .thenReturn(new PageResponse<>(List.<MissionListDto>of(), 0, 10, 0L, 0, List.of()));
    when(backendApiClient.get(
            eq("/api/v1/operations/" + opId + "/finances"),
            eq(OperationFinanceDto.class),
            anyBoolean()))
        .thenReturn(new OperationFinanceDto(opId, BigDecimal.ZERO, List.of()));
    when(backendApiClient.get(
            eq("/api/v1/operations/" + opId + "/payouts"),
            any(ParameterizedTypeReference.class),
            anyBoolean()))
        .thenReturn(List.of());
  }

  @Test
  @WithMockUser(roles = "OFFICER")
  void operationDetail_translatesBothOperationAndMissionStatus() throws Exception {
    UUID opId = UUID.randomUUID();

    // Operation: status COMPLETED → German "ABGESCHLOSSEN".
    OperationDto operation = new OperationDto(opId, "Completed Op", "", "COMPLETED", null, 0L);
    when(backendApiClient.get(
            eq("/api/v1/operations/" + opId), eq(OperationDto.class), anyBoolean()))
        .thenReturn(operation);

    // Mission inside operation: status CANCELLED (double L on Mission!) →
    // German "ABGEBROCHEN". This is the second i18n bug-fix-point — the
    // nested missions table on the operation-detail page used to dump the
    // raw enum value.
    MissionListDto mission =
        new MissionListDto(
            UUID.randomUUID(),
            "Aborted Mission",
            null,
            null,
            "CANCELLED",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            0L);
    PageResponse<MissionListDto> missionsPage =
        new PageResponse<>(List.of(mission), 0, 10, 1L, 1, List.of("plannedStartTime,asc"));
    when(backendApiClient.get(
            contains("/api/v1/missions/search?operationId=" + opId),
            any(ParameterizedTypeReference.class),
            anyBoolean()))
        .thenReturn(missionsPage);

    // Empty finance/payout stubs so the page renders without NPE.
    when(backendApiClient.get(
            eq("/api/v1/operations/" + opId + "/finances"),
            eq(OperationFinanceDto.class),
            anyBoolean()))
        .thenReturn(new OperationFinanceDto(opId, BigDecimal.ZERO, List.of()));
    when(backendApiClient.get(
            eq("/api/v1/operations/" + opId + "/payouts"),
            any(ParameterizedTypeReference.class),
            anyBoolean()))
        .thenReturn(List.of());

    mockMvc
        .perform(get("/operations/" + opId).locale(Locale.GERMAN))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("ABGESCHLOSSEN")))
        .andExpect(content().string(containsString("ABGEBROCHEN")))
        // Raw enum values must not appear as cell content. The
        // dropdown <option value="..."> attributes still legitimately
        // carry the enum names, so we look for the closing-tag form
        // ">VALUE<" which only the visible text matches.
        .andExpect(content().string(not(containsString(">COMPLETED<"))))
        .andExpect(content().string(not(containsString(">CANCELLED<"))));
  }
}
