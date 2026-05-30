package de.greluc.krt.iri.basetool.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.backend.model.JobOrderStatus;
import de.greluc.krt.iri.basetool.backend.model.dto.CreateJobOrderDto;
import de.greluc.krt.iri.basetool.backend.model.dto.HandoverReportPreviewRequestDto;
import de.greluc.krt.iri.basetool.backend.model.dto.InventoryItemDto;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderDto;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderHandoverCreateDto;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderHandoverDto;
import de.greluc.krt.iri.basetool.backend.model.dto.JobOrderReferenceDto;
import de.greluc.krt.iri.basetool.backend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.backend.model.dto.UpdateJobOrderStatusDto;
import de.greluc.krt.iri.basetool.backend.service.AuthHelperService;
import de.greluc.krt.iri.basetool.backend.service.JobOrderHandoverReportService;
import de.greluc.krt.iri.basetool.backend.service.JobOrderHandoverService;
import de.greluc.krt.iri.basetool.backend.service.JobOrderService;
import de.greluc.krt.iri.basetool.backend.service.UserService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Pure-Mockito unit tests for {@link JobOrderController}. The empty {@code
 * JobOrderControllerIntegrationTest.java} placeholder in the same package documents the intent to
 * cover this controller with a Docker-backed integration test; in the current environment Docker is
 * unavailable, so the unit-level pinning lives here.
 *
 * <p>Four non-pass-through behaviours need explicit coverage because regressing them is silent at
 * the type level:
 *
 * <ul>
 *   <li>{@link JobOrderController#addAssignee} / {@link JobOrderController#removeAssignee} resolve
 *       the self-vs-logistician decision at the HTTP boundary via {@code
 *       authHelperService.isLogisticianOrAbove()}. Self-assignment must always work; assigning a
 *       different user requires LOGISTICIAN-or-above (or higher via role hierarchy). The 403 path
 *       is the spot where moving the check into the service would break the ArchUnit rule.
 *   <li>{@link JobOrderController#downloadHandoverReport} parses the optional {@code
 *       X-User-Time-Zone} header. An invalid IANA zone is silently dropped (and the service falls
 *       back to UTC) — never propagated as a {@code DateTimeException} to the caller. The
 *       PDF/Content-Disposition headers are also pinned because they are the entire response
 *       contract for the download endpoint.
 *   <li>{@link JobOrderController#getAllJobOrders} accepts an explicit status filter list; the
 *       service receives it verbatim and the {@code PageResponse} envelope carries the sort tokens
 *       through {@code PaginationUtil.toSortStrings}. Default-empty filter must reach the service
 *       as {@code null}, not an empty list, otherwise the SQL {@code IN ()} clause yields no rows
 *       and the queue page would always be empty.
 *   <li>{@link JobOrderController#createJobOrder} is annotated {@code permitAll()}; the controller
 *       method itself must never consult the JWT helper or the authHelperService — that decision is
 *       intentional so that an unauthenticated squadron member can file a request via the public
 *       form.
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class JobOrderControllerTest {

  @Mock private JobOrderService jobOrderService;
  @Mock private JobOrderHandoverService jobOrderHandoverService;
  @Mock private JobOrderHandoverReportService jobOrderHandoverReportService;
  @Mock private UserService userService;
  @Mock private AuthHelperService authHelperService;

  @InjectMocks private JobOrderController controller;

  private static Jwt jwt(String sub) {
    return Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .subject(sub)
        .claim("sub", sub)
        .build();
  }

  private static JobOrderDto jobOrderDto(UUID id) {
    return new JobOrderDto(
        id,
        1,
        null,
        null,
        "alice",
        "deliver to ArcCorp",
        1,
        JobOrderStatus.OPEN,
        List.of(),
        List.of(),
        List.of(),
        Instant.parse("2026-01-01T00:00:00Z"),
        1L);
  }

  // ── POST /api/v1/orders (permitAll) ──────────────────────────────────

  @Test
  void createJobOrder_authenticatedCaller_passesThroughUnredacted() {
    CreateJobOrderDto request = new CreateJobOrderDto(null, null, "alice", null, List.of(), null);
    JobOrderDto created = jobOrderDto(UUID.randomUUID());
    when(jobOrderService.createJobOrder(request)).thenReturn(created);

    JobOrderDto result =
        controller.createJobOrder(request, jwt("00000000-0000-0000-0000-000000000099"));

    // Authenticated callers see the full DTO — the guest-redaction pass kicks in only when the
    // JWT is null. Pinning this also confirms the controller does not touch the JWT helper / role
    // helper on the create path — they remain unused for the authenticated branch as well, so a
    // future refactor cannot sneak in "let's just record the creator's id" without breaking this
    // test.
    assertThat(result).isSameAs(created);
    verifyNoInteractions(userService, authHelperService);
  }

  @Test
  void createJobOrder_anonymousCaller_redactsAssigneesHandoversAndVersion() {
    CreateJobOrderDto request = new CreateJobOrderDto(null, null, "alice", null, List.of(), null);
    JobOrderDto created = jobOrderDto(UUID.randomUUID());
    when(jobOrderService.createJobOrder(request)).thenReturn(created);

    JobOrderDto result = controller.createJobOrder(request, null);

    // Audit finding C-1 family for JobOrder: an anonymous caller submitting the public request
    // form must get a slim acknowledgement — assignees / handovers wiped (defence-in-depth: a
    // freshly-created order has neither, but a future regression that pre-populates either would
    // leak member PII), version stripped (anonymous cannot update so it has no purpose).
    assertThat(result.id()).isEqualTo(created.id());
    assertThat(result.displayId()).isEqualTo(created.displayId());
    assertThat(result.handle()).isEqualTo(created.handle());
    assertThat(result.assignees()).isEmpty();
    assertThat(result.handovers()).isEmpty();
    assertThat(result.version()).isNull();
    verifyNoInteractions(userService, authHelperService);
  }

  // ── GET /api/v1/orders (list) ────────────────────────────────────────

  @Test
  void getAllJobOrders_forwardsStatusFilterAndPageable() {
    JobOrderDto dto = jobOrderDto(UUID.randomUUID());
    Page<JobOrderDto> page =
        new PageImpl<>(
            List.of(dto),
            PageRequest.of(0, 20, org.springframework.data.domain.Sort.by("priority")),
            1);
    when(jobOrderService.getAllJobOrders(
            eq(List.of(JobOrderStatus.OPEN)), eq(null), any(Pageable.class)))
        .thenReturn(page);

    PageResponse<JobOrderDto> result =
        controller.getAllJobOrders(List.of(JobOrderStatus.OPEN), null, 0, 20, "priority,asc");

    assertThat(result.content()).containsExactly(dto);
    assertThat(result.sort()).isNotEmpty();
    verify(jobOrderService)
        .getAllJobOrders(eq(List.of(JobOrderStatus.OPEN)), eq(null), any(Pageable.class));
  }

  @Test
  void getAllJobOrders_nullStatusFilter_reachesServiceAsNullNotEmptyList() {
    JobOrderDto dto = jobOrderDto(UUID.randomUUID());
    Page<JobOrderDto> page = new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1);
    when(jobOrderService.getAllJobOrders(eq(null), eq(null), any(Pageable.class))).thenReturn(page);

    PageResponse<JobOrderDto> result =
        controller.getAllJobOrders(null, null, 0, 20, "priority,asc");

    // The status filter MUST reach the service as null when absent — coercing it to an empty list
    // here would produce SQL "WHERE status IN ()" which never matches any row, leaving the queue
    // page perpetually empty. Pin the verbatim null pass-through.
    assertThat(result.content()).containsExactly(dto);
    verify(jobOrderService).getAllJobOrders(eq(null), eq(null), any(Pageable.class));
  }

  @Test
  void getAllJobOrders_forwardsSquadronIdFilter() {
    JobOrderDto dto = jobOrderDto(UUID.randomUUID());
    UUID squadronId = UUID.randomUUID();
    Page<JobOrderDto> page = new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1);
    when(jobOrderService.getAllJobOrders(
            eq(List.of(JobOrderStatus.OPEN)), eq(squadronId), any(Pageable.class)))
        .thenReturn(page);

    PageResponse<JobOrderDto> result =
        controller.getAllJobOrders(List.of(JobOrderStatus.OPEN), squadronId, 0, 20, "priority,asc");

    // The squadronId param MUST reach the service verbatim — the orders-index "Nur eigene Staffel"
    // toggle (MULTI_SQUADRON_PLAN.md §5.3) relies on this pass-through to short-circuit the
    // cross-staffel union to the caller's own squadron on creating- or requesting-side match.
    assertThat(result.content()).containsExactly(dto);
    verify(jobOrderService)
        .getAllJobOrders(eq(List.of(JobOrderStatus.OPEN)), eq(squadronId), any(Pageable.class));
  }

  // ── GET /api/v1/orders/lookup ────────────────────────────────────────

  @Test
  void lookupJobOrders_delegatesToServiceReferenceQuery() {
    JobOrderReferenceDto ref =
        new JobOrderReferenceDto(UUID.randomUUID(), 42, "alice", JobOrderStatus.OPEN, List.of());
    when(jobOrderService.findAllActiveReference()).thenReturn(List.of(ref));

    List<JobOrderReferenceDto> result = controller.lookupJobOrders();

    assertThat(result).containsExactly(ref);
    verify(jobOrderService).findAllActiveReference();
  }

  // ── GET /api/v1/orders/{id} ──────────────────────────────────────────

  @Test
  void getJobOrderById_returnsServiceResultUnchanged() {
    UUID id = UUID.randomUUID();
    JobOrderDto dto = jobOrderDto(id);
    when(jobOrderService.getJobOrderById(id)).thenReturn(dto);

    JobOrderDto result = controller.getJobOrderById(id);

    assertThat(result).isSameAs(dto);
  }

  @Test
  void getInventoryItemsForJobOrderMaterial_delegatesBothPathParameters() {
    UUID jobOrderId = UUID.randomUUID();
    UUID materialId = UUID.randomUUID();
    InventoryItemDto inv =
        new InventoryItemDto(
            UUID.randomUUID(),
            null,
            null,
            null,
            750,
            5.0,
            false,
            jobOrderId,
            1,
            null,
            null,
            null,
            null,
            1L);
    when(jobOrderService.getInventoryItemsForJobOrderMaterial(jobOrderId, materialId))
        .thenReturn(List.of(inv));

    List<InventoryItemDto> result =
        controller.getInventoryItemsForJobOrderMaterial(jobOrderId, materialId);

    assertThat(result).containsExactly(inv);
  }

  // ── PUT /api/v1/orders/{id}/status ───────────────────────────────────

  @Test
  void updateJobOrderStatus_forwardsBodyVersionToService() {
    UUID id = UUID.randomUUID();
    UpdateJobOrderStatusDto dto = new UpdateJobOrderStatusDto(JobOrderStatus.COMPLETED, 7L);
    JobOrderDto persisted = jobOrderDto(id);
    when(jobOrderService.updateJobOrderStatus(id, dto)).thenReturn(persisted);

    JobOrderDto result = controller.updateJobOrderStatus(id, dto);

    // The request body's version field is the optimistic-locking key — the controller must pass
    // the whole DTO verbatim so the service can compare the expected version against the
    // persisted aggregate. The completeJobOrderWithinTransaction pattern (CLAUDE.md) depends on
    // this round-trip working.
    assertThat(result).isSameAs(persisted);
    verify(jobOrderService).updateJobOrderStatus(id, dto);
  }

  // ── PUT /api/v1/orders/{id}/priority ─────────────────────────────────

  @Test
  void updateJobOrderPriority_forwardsRequestParamToService() {
    UUID id = UUID.randomUUID();
    JobOrderDto persisted = jobOrderDto(id);
    when(jobOrderService.updateJobOrderPriority(id, 3)).thenReturn(persisted);

    JobOrderDto result = controller.updateJobOrderPriority(id, 3);

    assertThat(result).isSameAs(persisted);
    verify(jobOrderService).updateJobOrderPriority(id, 3);
  }

  // ── PUT /api/v1/orders/{id} (full update) ────────────────────────────

  @Test
  void updateJobOrder_forwardsBodyToService() {
    UUID id = UUID.randomUUID();
    CreateJobOrderDto updateDto = new CreateJobOrderDto(null, null, "bob", null, List.of(), 1L);
    JobOrderDto persisted = jobOrderDto(id);
    when(jobOrderService.updateJobOrder(id, updateDto)).thenReturn(persisted);

    JobOrderDto result = controller.updateJobOrder(id, updateDto);

    assertThat(result).isSameAs(persisted);
  }

  // ── DELETE /api/v1/orders/{id} ───────────────────────────────────────

  @Test
  void deleteJobOrder_delegatesToService() {
    UUID id = UUID.randomUUID();

    controller.deleteJobOrder(id);

    verify(jobOrderService).deleteJobOrder(id);
  }

  // ── DELETE /api/v1/orders/{jobOrderId}/materials/{materialId} ────────

  @Test
  void unlinkMaterial_delegatesBothPathParameters() {
    UUID jobOrderId = UUID.randomUUID();
    UUID materialId = UUID.randomUUID();

    controller.unlinkMaterial(jobOrderId, materialId);

    verify(jobOrderService).unlinkMaterial(jobOrderId, materialId);
  }

  // ── DELETE /api/v1/orders/{jobOrderId}/inventory/{inventoryItemId}/unlink ─

  @Test
  void unlinkInventoryItem_delegatesBothPathParameters() {
    UUID jobOrderId = UUID.randomUUID();
    UUID inventoryItemId = UUID.randomUUID();

    controller.unlinkInventoryItem(jobOrderId, inventoryItemId);

    verify(jobOrderService).unlinkInventoryItem(jobOrderId, inventoryItemId);
  }

  // ── POST /api/v1/orders/{id}/assignees/{userId} ──────────────────────

  @Test
  void addAssignee_self_doesNotConsultRoleHelper() {
    Jwt jwt = jwt("alice-sub");
    UUID callerId = UUID.randomUUID();
    UUID jobOrderId = UUID.randomUUID();
    JobOrderDto persisted = jobOrderDto(jobOrderId);
    when(userService.getUserIdFromJwt(jwt)).thenReturn(callerId);
    when(jobOrderService.addAssignee(jobOrderId, callerId)).thenReturn(persisted);

    JobOrderDto result = controller.addAssignee(jobOrderId, callerId, jwt);

    // Self-assignment short-circuits before the role helper is touched. This matters because
    // hasReachableRole() walks the role hierarchy and is more expensive than a UUID compare —
    // pin the order so a refactor that "simplifies" the if-chain doesn't accidentally make the
    // role lookup the only gate (and let everyone block themselves on hierarchy misconfig).
    assertThat(result).isSameAs(persisted);
    verify(authHelperService, never()).isLogisticianOrAbove();
  }

  @Test
  void addAssignee_otherUser_logistician_isAllowed() {
    Jwt jwt = jwt("alice-sub");
    UUID callerId = UUID.randomUUID();
    UUID targetUserId = UUID.randomUUID();
    UUID jobOrderId = UUID.randomUUID();
    JobOrderDto persisted = jobOrderDto(jobOrderId);
    when(userService.getUserIdFromJwt(jwt)).thenReturn(callerId);
    when(authHelperService.isLogisticianOrAbove()).thenReturn(true);
    when(jobOrderService.addAssignee(jobOrderId, targetUserId)).thenReturn(persisted);

    JobOrderDto result = controller.addAssignee(jobOrderId, targetUserId, jwt);

    assertThat(result).isSameAs(persisted);
  }

  @Test
  void addAssignee_otherUser_nonLogistician_throws403() {
    Jwt jwt = jwt("alice-sub");
    UUID callerId = UUID.randomUUID();
    UUID targetUserId = UUID.randomUUID();
    UUID jobOrderId = UUID.randomUUID();
    when(userService.getUserIdFromJwt(jwt)).thenReturn(callerId);
    when(authHelperService.isLogisticianOrAbove()).thenReturn(false);

    // The 403 path lives in the CONTROLLER, not in the service. Moving it into the service
    // would force a SecurityContextHolder read inside business logic and break the ArchUnit
    // rule. The test pins (1) the AccessDeniedException type — RFC 7807 problem mapping
    // depends on it — and (2) the fact that the service is NEVER called for the forbidden
    // case.
    assertThatThrownBy(() -> controller.addAssignee(jobOrderId, targetUserId, jwt))
        .isInstanceOf(AccessDeniedException.class);
    verify(jobOrderService, never()).addAssignee(any(), any());
  }

  // ── DELETE /api/v1/orders/{id}/assignees/{userId} ────────────────────

  @Test
  void removeAssignee_self_doesNotConsultRoleHelper() {
    Jwt jwt = jwt("alice-sub");
    UUID callerId = UUID.randomUUID();
    UUID jobOrderId = UUID.randomUUID();
    JobOrderDto persisted = jobOrderDto(jobOrderId);
    when(userService.getUserIdFromJwt(jwt)).thenReturn(callerId);
    when(jobOrderService.removeAssignee(jobOrderId, callerId)).thenReturn(persisted);

    JobOrderDto result = controller.removeAssignee(jobOrderId, callerId, jwt);

    assertThat(result).isSameAs(persisted);
    verify(authHelperService, never()).isLogisticianOrAbove();
  }

  @Test
  void removeAssignee_otherUser_logistician_isAllowed() {
    Jwt jwt = jwt("alice-sub");
    UUID callerId = UUID.randomUUID();
    UUID targetUserId = UUID.randomUUID();
    UUID jobOrderId = UUID.randomUUID();
    JobOrderDto persisted = jobOrderDto(jobOrderId);
    when(userService.getUserIdFromJwt(jwt)).thenReturn(callerId);
    when(authHelperService.isLogisticianOrAbove()).thenReturn(true);
    when(jobOrderService.removeAssignee(jobOrderId, targetUserId)).thenReturn(persisted);

    JobOrderDto result = controller.removeAssignee(jobOrderId, targetUserId, jwt);

    assertThat(result).isSameAs(persisted);
  }

  @Test
  void removeAssignee_otherUser_nonLogistician_throws403() {
    Jwt jwt = jwt("alice-sub");
    UUID callerId = UUID.randomUUID();
    UUID targetUserId = UUID.randomUUID();
    UUID jobOrderId = UUID.randomUUID();
    when(userService.getUserIdFromJwt(jwt)).thenReturn(callerId);
    when(authHelperService.isLogisticianOrAbove()).thenReturn(false);

    assertThatThrownBy(() -> controller.removeAssignee(jobOrderId, targetUserId, jwt))
        .isInstanceOf(AccessDeniedException.class);
    verify(jobOrderService, never()).removeAssignee(any(), any());
  }

  // ── POST /api/v1/orders/{id}/handovers ───────────────────────────────

  @Test
  void createHandover_delegatesToHandoverServiceWithBody() {
    UUID jobOrderId = UUID.randomUUID();
    JobOrderHandoverCreateDto dto =
        new JobOrderHandoverCreateDto(
            Instant.parse("2026-05-01T12:00:00Z"), "alice", "DAS KARTELL", List.of());
    JobOrderHandoverDto persisted =
        new JobOrderHandoverDto(
            UUID.randomUUID(),
            jobOrderId,
            dto.handoverTime(),
            "alice",
            "DAS KARTELL",
            null,
            null,
            List.of(),
            1L);
    when(jobOrderHandoverService.createHandover(jobOrderId, dto)).thenReturn(persisted);

    JobOrderHandoverDto result = controller.createHandover(jobOrderId, dto);

    assertThat(result).isSameAs(persisted);
    verify(jobOrderHandoverService).createHandover(jobOrderId, dto);
  }

  // ── GET /api/v1/orders/{jobOrderId}/handovers/{handoverId}/report ────

  @Test
  void downloadHandoverReport_validTimeZone_passedToService() {
    UUID jobOrderId = UUID.randomUUID();
    UUID handoverId = UUID.randomUUID();
    byte[] pdf = new byte[] {0x25, 0x50, 0x44, 0x46}; // %PDF
    when(jobOrderHandoverReportService.generateHandoverReport(
            eq(jobOrderId), eq(handoverId), eq(java.time.ZoneId.of("Europe/Berlin"))))
        .thenReturn(pdf);

    ResponseEntity<byte[]> response =
        controller.downloadHandoverReport(jobOrderId, handoverId, "Europe/Berlin");

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).isSameAs(pdf);
    HttpHeaders headers = response.getHeaders();
    assertThat(headers.getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
    // Content-Disposition pin: filename must include the job-order id so admins can grep
    // downloaded files by order; missing this lets the test catch a regression that hardcodes
    // a generic filename.
    assertThat(headers.getContentDisposition().getFilename())
        .isEqualTo("uebergabeprotokoll-" + jobOrderId + ".pdf");
  }

  @Test
  void downloadHandoverReport_blankTimeZone_treatedAsNull() {
    UUID jobOrderId = UUID.randomUUID();
    UUID handoverId = UUID.randomUUID();
    byte[] pdf = new byte[] {0x25, 0x50, 0x44, 0x46};
    when(jobOrderHandoverReportService.generateHandoverReport(jobOrderId, handoverId, null))
        .thenReturn(pdf);

    ResponseEntity<byte[]> response =
        controller.downloadHandoverReport(jobOrderId, handoverId, "   ");

    // A blank header value must reach the service as null (the service falls back to UTC).
    // Pinning this prevents a future refactor that calls ZoneId.of(" ".strip()) and throws
    // a DateTimeException before the service is even reached.
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    verify(jobOrderHandoverReportService).generateHandoverReport(jobOrderId, handoverId, null);
  }

  @Test
  void downloadHandoverReport_invalidTimeZone_silentlyDroppedAndServiceCalledWithNull() {
    UUID jobOrderId = UUID.randomUUID();
    UUID handoverId = UUID.randomUUID();
    byte[] pdf = new byte[] {0x25, 0x50, 0x44, 0x46};
    when(jobOrderHandoverReportService.generateHandoverReport(jobOrderId, handoverId, null))
        .thenReturn(pdf);

    ResponseEntity<byte[]> response =
        controller.downloadHandoverReport(jobOrderId, handoverId, "Not/AValidZone");

    // Invalid IANA zone MUST be silently dropped — the user gets a UTC-rendered PDF rather than
    // a 400, because the time-zone header is "best effort" UX, not a correctness contract. This
    // is the spot where a refactor that propagates the DateTimeException would silently change
    // a previously-working PDF download into a 400 error for everyone running an outdated
    // browser locale.
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    verify(jobOrderHandoverReportService).generateHandoverReport(jobOrderId, handoverId, null);
  }

  @Test
  void downloadHandoverReport_nullTimeZoneHeader_passedAsNull() {
    UUID jobOrderId = UUID.randomUUID();
    UUID handoverId = UUID.randomUUID();
    byte[] pdf = new byte[] {0x25, 0x50, 0x44, 0x46};
    when(jobOrderHandoverReportService.generateHandoverReport(jobOrderId, handoverId, null))
        .thenReturn(pdf);

    ResponseEntity<byte[]> response =
        controller.downloadHandoverReport(jobOrderId, handoverId, null);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    verify(jobOrderHandoverReportService).generateHandoverReport(jobOrderId, handoverId, null);
  }

  // ── POST /api/v1/orders/{jobOrderId}/handovers/report/preview ────────

  @Test
  void previewHandoverReport_returnsPdfWithPreviewFilename() {
    UUID jobOrderId = UUID.randomUUID();
    HandoverReportPreviewRequestDto dto =
        new HandoverReportPreviewRequestDto(
            "#42", LocalDateTime.of(2026, 5, 1, 12, 0), "alice", List.of());
    byte[] pdf = new byte[] {0x25, 0x50, 0x44, 0x46};
    when(jobOrderHandoverReportService.generateHandoverReportPreview(dto)).thenReturn(pdf);

    ResponseEntity<byte[]> response = controller.previewHandoverReport(jobOrderId, dto);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).isSameAs(pdf);
    HttpHeaders headers = response.getHeaders();
    assertThat(headers.getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
    // Preview filename is intentionally GENERIC (no job-order id) — the preview is rendered from
    // unsaved data so there is no canonical id to embed yet. The test pins the exact string so a
    // future "let's always include the job-order id" refactor surfaces here.
    assertThat(headers.getContentDisposition().getFilename())
        .isEqualTo("uebergabeprotokoll-vorschau.pdf");
  }
}
