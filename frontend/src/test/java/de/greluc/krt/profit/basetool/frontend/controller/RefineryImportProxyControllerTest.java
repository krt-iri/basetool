/*
 * Profit Basetool - squadron-management web app.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.greluc.krt.profit.basetool.frontend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.greluc.krt.profit.basetool.frontend.model.dto.ImportIssueCode;
import de.greluc.krt.profit.basetool.frontend.model.dto.ImportIssueDto;
import de.greluc.krt.profit.basetool.frontend.model.dto.ImportIssueSeverity;
import de.greluc.krt.profit.basetool.frontend.model.dto.ImportSuggestionDto;
import de.greluc.krt.profit.basetool.frontend.model.dto.LocationDto;
import de.greluc.krt.profit.basetool.frontend.model.dto.MaterialDto;
import de.greluc.krt.profit.basetool.frontend.model.dto.RefineryGoodDto;
import de.greluc.krt.profit.basetool.frontend.model.dto.RefineryImportDraftDto;
import de.greluc.krt.profit.basetool.frontend.model.dto.RefineryOrderDto;
import de.greluc.krt.profit.basetool.frontend.model.dto.RefineryOrderStatus;
import de.greluc.krt.profit.basetool.frontend.model.dto.RefiningMethodDto;
import de.greluc.krt.profit.basetool.frontend.model.form.RefineryOrderForm;
import de.greluc.krt.profit.basetool.frontend.service.BackendApiClient;
import de.greluc.krt.profit.basetool.frontend.service.BackendServiceException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

/**
 * Unit tests for {@link RefineryImportProxyController} (#435): the JSON relay to the Phase 1
 * backend endpoint, the draft-to-form mapping (incl. the hours/minutes split and the row-issue
 * grouping by draft index), and every error branch (not-JSON upload, backend problem detail,
 * unexpected failure). The backend seam is a mocked {@link BackendApiClient} — the typed client is
 * the frontend's single backend seam, so no raw HTTP server is needed here.
 */
class RefineryImportProxyControllerTest {

  private static final UUID MATERIAL_ID = UUID.randomUUID();
  private static final UUID REFINED_ID = UUID.randomUUID();
  private static final UUID LOCATION_ID = UUID.randomUUID();
  private static final UUID METHOD_ID = UUID.randomUUID();

  private BackendApiClient backendApiClient;
  private RefineryImportProxyController controller;
  private RedirectAttributesModelMap redirectAttributes;

  @BeforeEach
  void setUp() {
    backendApiClient = mock(BackendApiClient.class);
    controller = new RefineryImportProxyController(backendApiClient);
    redirectAttributes = new RedirectAttributesModelMap();
  }

  @Test
  void importExtract_happyPath_flashesPrefilledFormAndIssues() {
    // Given — a draft with a matched row, an unmatched row with suggestions and order-level flags
    MaterialDto raw = material(MATERIAL_ID, "Stileron (Raw)");
    MaterialDto refined = material(REFINED_ID, "Stileron");
    RefineryGoodDto matched = new RefineryGoodDto(null, raw, 957, refined, 448, 618, null);
    RefineryGoodDto unmatched = new RefineryGoodDto(null, null, 300, null, 140, 0, null);
    ImportIssueDto unmatchedIssue =
        new ImportIssueDto(
            "goods[1].inputMaterial",
            "ALUMINIUM (ORE)",
            ImportIssueCode.UNMATCHED_MATERIAL,
            ImportIssueSeverity.WARNING,
            0.95,
            List.of(new ImportSuggestionDto(MATERIAL_ID, "Aluminum (Raw)", 0.889)));
    ImportIssueDto skippedIssue =
        new ImportIssueDto(
            "goods[2]",
            "INERT MATERIALS",
            ImportIssueCode.SKIPPED_REFINE_OFF,
            ImportIssueSeverity.INFO,
            null,
            null);
    ImportIssueDto locationIssue =
        new ImportIssueDto(
            "location",
            null,
            ImportIssueCode.UNRESOLVED_LOCATION,
            ImportIssueSeverity.WARNING,
            null,
            null);
    RefineryOrderDto order =
        new RefineryOrderDto(
            null,
            null,
            new LocationDto(LOCATION_ID, "Levski", null, false, false, 0L),
            null,
            Instant.parse("2026-06-01T19:39:01Z"),
            1258L,
            48928.0,
            null,
            null,
            null,
            new RefiningMethodDto(METHOD_ID, "Ferron Exchange", null, null, null, null, null),
            List.of(matched, unmatched),
            RefineryOrderStatus.OPEN,
            null,
            null,
            null);
    RefineryImportDraftDto draft =
        new RefineryImportDraftDto(
            order, List.of(locationIssue, unmatchedIssue, skippedIssue), 1, 3, 1);
    when(backendApiClient.post(
            eq("/api/v1/refinery-orders/import-extract"), any(), eq(RefineryImportDraftDto.class)))
        .thenReturn(draft);

    // When
    String view = controller.importExtract(jsonUpload("{\"schemaVersion\":1}"), redirectAttributes);

    // Then — redirect with the pre-filled form
    assertThat(view).isEqualTo("redirect:/refinery-orders/create");
    Map<String, Object> flash = flash();
    RefineryOrderForm form = (RefineryOrderForm) flash.get("refineryOrderForm");
    assertThat(form).isNotNull();
    assertThat(form.getLocationId()).isEqualTo(LOCATION_ID);
    assertThat(form.getRefiningMethodId()).isEqualTo(METHOD_ID);
    // The capture-derived start time arrives as the UTC ISO instant the splitter renders locally.
    assertThat(form.getStartedAt()).isEqualTo("2026-06-01T19:39:01Z");
    assertThat(form.getDurationHours()).isEqualTo(20);
    assertThat(form.getDurationMinutes()).isEqualTo(58);
    assertThat(form.getExpenses()).isEqualTo(48928.0);
    assertThat(form.getStatus()).isEqualTo(RefineryOrderStatus.OPEN);
    assertThat(form.getGoods()).hasSize(2);
    assertThat(form.getGoods().get(0).getInputMaterialId()).isEqualTo(MATERIAL_ID);
    assertThat(form.getGoods().get(0).getOutputMaterialId()).isEqualTo(REFINED_ID);
    assertThat(form.getGoods().get(0).getQuality()).isEqualTo(618);
    assertThat(form.getGoods().get(1).getInputMaterialId()).isNull();

    // Then — issues split into row-anchored and banner findings
    @SuppressWarnings("unchecked")
    Map<String, List<ImportIssueDto>> rowIssues =
        (Map<String, List<ImportIssueDto>>) flash.get("importRowIssues");
    assertThat(rowIssues).containsOnlyKeys("1");
    assertThat(rowIssues.get("1").getFirst().code()).isEqualTo(ImportIssueCode.UNMATCHED_MATERIAL);
    @SuppressWarnings("unchecked")
    List<ImportIssueDto> general = (List<ImportIssueDto>) flash.get("importIssues");
    assertThat(general)
        .extracting(ImportIssueDto::code)
        .containsExactly(ImportIssueCode.UNRESOLVED_LOCATION, ImportIssueCode.SKIPPED_REFINE_OFF);
    assertThat(flash.get("importGoodsMatched")).isEqualTo(1);
    assertThat(flash.get("importGoodsTotal")).isEqualTo(3);
    assertThat(flash.get("importRowsSkipped")).isEqualTo(1);
  }

  @Test
  void importExtract_nonJsonUpload_flashesInvalidFileWithoutBackendCall() {
    // Given
    MultipartFile file =
        new MockMultipartFile(
            "file", "screenshot.png", "image/png", "not json".getBytes(StandardCharsets.UTF_8));

    // When
    String view = controller.importExtract(file, redirectAttributes);

    // Then — rejected locally, the backend is never bothered
    assertThat(view).isEqualTo("redirect:/refinery-orders/create");
    assertThat(flash()).containsEntry("importErrorKey", "refineryImport.error.invalidFile");
    verifyNoInteractions(backendApiClient);
  }

  @Test
  void importExtract_jsonArrayUpload_flashesInvalidFile() {
    // Given — parseable JSON but not an object envelope
    String view = controller.importExtract(jsonUpload("[1,2,3]"), redirectAttributes);

    // Then
    assertThat(view).isEqualTo("redirect:/refinery-orders/create");
    assertThat(flash()).containsEntry("importErrorKey", "refineryImport.error.invalidFile");
    verifyNoInteractions(backendApiClient);
  }

  @Test
  void importExtract_emptyUpload_flashesInvalidFile() {
    // Given
    MultipartFile file =
        new MockMultipartFile("file", "empty.json", "application/json", new byte[0]);

    // When
    String view = controller.importExtract(file, redirectAttributes);

    // Then
    assertThat(view).isEqualTo("redirect:/refinery-orders/create");
    assertThat(flash()).containsEntry("importErrorKey", "refineryImport.error.invalidFile");
  }

  @Test
  void importExtract_oversizedUpload_flashesInvalidFileWithoutBackendCall() {
    // Given — a file above the 2 MB sanity cap (the wrong file, e.g. a screenshot)
    byte[] oversized = new byte[(int) RefineryImportProxyController.MAX_EXTRACT_BYTES + 1];
    MultipartFile file =
        new MockMultipartFile("file", "screenshot.png", "application/json", oversized);

    // When
    String view = controller.importExtract(file, redirectAttributes);

    // Then — rejected locally, the backend is never called (covers REQ-REFINERY-016)
    assertThat(view).isEqualTo("redirect:/refinery-orders/create");
    assertThat(flash()).containsEntry("importErrorKey", "refineryImport.error.invalidFile");
    verifyNoInteractions(backendApiClient);
  }

  @Test
  void handleOversizedUpload_flashesInvalidFile() {
    // Given / When — Spring rejected the multipart before the handler ran (above the 64 MB cap)
    String view = controller.handleOversizedUpload(redirectAttributes);

    // Then — same friendly inline error as the controller's own sanity cap
    assertThat(view).isEqualTo("redirect:/refinery-orders/create");
    assertThat(flash()).containsEntry("importErrorKey", "refineryImport.error.invalidFile");
  }

  @Test
  void rowIssues_indexBeyondIntRange_treatedAsUnanchoredInsteadOfThrowing() {
    // Given — a crafted/corrupt field path whose digits overflow Integer (regex \d+ accepts any
    // length); Integer.valueOf would throw NumberFormatException without the guard
    ImportIssueDto overflow =
        new ImportIssueDto(
            "goods[99999999999999999999].inputMaterial",
            "STILERON (ORE)",
            ImportIssueCode.UNMATCHED_MATERIAL,
            ImportIssueSeverity.WARNING,
            null,
            null);

    // When
    Map<String, List<ImportIssueDto>> byRow =
        RefineryImportProxyController.rowIssues(List.of(overflow));
    List<ImportIssueDto> general = RefineryImportProxyController.generalIssues(List.of(overflow));

    // Then — the finding falls through to the banner list instead of aborting the import
    assertThat(byRow).isEmpty();
    assertThat(general).containsExactly(overflow);
  }

  @Test
  void importExtract_backendProblemWithoutDetail_flashesGenericError() {
    // Given — a backend reject whose problem body carries no detail text
    when(backendApiClient.post(
            eq("/api/v1/refinery-orders/import-extract"), any(), eq(RefineryImportDraftDto.class)))
        .thenThrow(
            new BackendServiceException(
                "400 from backend", null, 400, "BAD_REQUEST", null, Collections.emptyList(), null));

    // When
    String view = controller.importExtract(jsonUpload("{\"schemaVersion\":2}"), redirectAttributes);

    // Then — falls back to the generic frontend i18n key instead of flashing blank text
    assertThat(view).isEqualTo("redirect:/refinery-orders/create");
    assertThat(flash()).containsEntry("importErrorKey", "refineryImport.error.failed");
    assertThat(flash()).doesNotContainKey("importErrorText");
  }

  @Test
  void importExtract_nullDraft_flashesGenericError() {
    // Given — the relay succeeded but returned no usable draft
    when(backendApiClient.post(
            eq("/api/v1/refinery-orders/import-extract"), any(), eq(RefineryImportDraftDto.class)))
        .thenReturn(null);

    // When
    String view = controller.importExtract(jsonUpload("{\"schemaVersion\":1}"), redirectAttributes);

    // Then
    assertThat(view).isEqualTo("redirect:/refinery-orders/create");
    assertThat(flash()).containsEntry("importErrorKey", "refineryImport.error.failed");
    assertThat(flash()).doesNotContainKey("refineryOrderForm");
  }

  @Test
  void importExtract_backendProblem_surfacesLocalizedDetailVerbatim() {
    // Given — envelope-level reject (e.g. unsupported schemaVersion) with localized detail
    when(backendApiClient.post(
            eq("/api/v1/refinery-orders/import-extract"), any(), eq(RefineryImportDraftDto.class)))
        .thenThrow(
            new BackendServiceException(
                "400 from backend",
                null,
                400,
                "BAD_REQUEST",
                null,
                Collections.emptyList(),
                "Die Extract-Datei verwendet eine nicht unterstützte Schema-Version."));

    // When
    String view = controller.importExtract(jsonUpload("{\"schemaVersion\":2}"), redirectAttributes);

    // Then — the backend's already-localized problem detail is shown verbatim
    assertThat(view).isEqualTo("redirect:/refinery-orders/create");
    assertThat(flash())
        .containsEntry(
            "importErrorText",
            "Die Extract-Datei verwendet eine nicht unterstützte Schema-Version.");
    assertThat(flash()).doesNotContainKey("refineryOrderForm");
  }

  @Test
  void importExtract_unexpectedFailure_flashesGenericError() {
    // Given
    when(backendApiClient.post(
            eq("/api/v1/refinery-orders/import-extract"), any(), eq(RefineryImportDraftDto.class)))
        .thenThrow(new IllegalStateException("boom"));

    // When
    String view = controller.importExtract(jsonUpload("{\"schemaVersion\":1}"), redirectAttributes);

    // Then
    assertThat(view).isEqualTo("redirect:/refinery-orders/create");
    assertThat(flash()).containsEntry("importErrorKey", "refineryImport.error.failed");
  }

  @Test
  void toForm_keepsSeededEmptyRowForEmptyGoodsDraft() {
    // Given — a draft whose rows were all skipped (e.g. fully un-quoted order)
    RefineryOrderDto order =
        new RefineryOrderDto(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(),
            RefineryOrderStatus.OPEN,
            null,
            null,
            null);

    // When
    RefineryOrderForm form = RefineryImportProxyController.toForm(order);

    // Then — the template's row-clone JS needs at least the one seeded empty row
    assertThat(form.getGoods()).hasSize(1);
    assertThat(form.getGoods().getFirst().getInputMaterialId()).isNull();
    // No capture metadata in the draft → the create flow keeps its "now" default at save time.
    assertThat(form.getStartedAt()).isNull();
  }

  private Map<String, Object> flash() {
    return new java.util.HashMap<>(redirectAttributes.getFlashAttributes());
  }

  private static MultipartFile jsonUpload(String json) {
    return new MockMultipartFile(
        "file", "extract.json", "application/json", json.getBytes(StandardCharsets.UTF_8));
  }

  private static MaterialDto material(UUID id, String name) {
    return new MaterialDto(
        id, name, "RAW", "SCU", null, null, null, false, false, false, false, false, false, true,
        0L);
  }
}
