package de.greluc.krt.iri.basetool.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.greluc.krt.iri.basetool.backend.model.FinanceType;
import de.greluc.krt.iri.basetool.backend.model.dto.MissionFinanceEntryCreateDto;
import de.greluc.krt.iri.basetool.backend.model.dto.MissionFinanceEntryDto;
import de.greluc.krt.iri.basetool.backend.model.dto.MissionFinanceEntryUpdateDto;
import de.greluc.krt.iri.basetool.backend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.backend.service.MissionFinanceEntryService;
import java.math.BigDecimal;
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
import org.springframework.data.domain.Sort;

/**
 * Pure-Mockito unit tests for {@link MissionFinanceEntryController}. The controller's split
 * URL-space (reads are mission-scoped under {@code /missions/{missionId}/finance-entries}, writes
 * are entry-scoped under {@code /finance-entries/{entryId}}) is the easy-to-regress part: a future
 * refactor that moves the create endpoint to {@code /missions/{missionId}/finance-entries} (it
 * sounds natural!) would break the deliberate {@code permitAll()} carve-out for guest participants
 * recording their own payouts. Tests pin the existing route topology by asserting each handler's
 * pass-through to its specific service method.
 */
@ExtendWith(MockitoExtension.class)
class MissionFinanceEntryControllerTest {

  @Mock private MissionFinanceEntryService service;

  @InjectMocks private MissionFinanceEntryController controller;

  private static MissionFinanceEntryDto entry(UUID missionId, FinanceType type, BigDecimal amount) {
    return new MissionFinanceEntryDto(UUID.randomUUID(), missionId, null, "note", type, amount, 1L);
  }

  @Test
  void getFinanceEntries_wrapsServicePageIntoPageResponse() {
    UUID missionId = UUID.randomUUID();
    MissionFinanceEntryDto a = entry(missionId, FinanceType.INCOME, new BigDecimal("1000.00"));
    MissionFinanceEntryDto b = entry(missionId, FinanceType.EXPENSE, new BigDecimal("250.00"));
    Page<MissionFinanceEntryDto> page =
        new PageImpl<>(
            List.of(a, b), PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "createdAt")), 2);
    when(service.getEntriesByMission(eq(missionId), any(Pageable.class))).thenReturn(page);

    // Audit finding M-1 (2026-05-20): the controller now builds the {@link Pageable} from
    // explicit page / size / sort params with a whitelist (mirrors UserController / JobOrder).
    PageResponse<MissionFinanceEntryDto> result =
        controller.getFinanceEntries(missionId, 0, 10, "createdAt,asc");

    assertThat(result.content()).containsExactly(a, b);
    assertThat(result.totalElements()).isEqualTo(2L);
    // The sort encoding "<field>,<direction>" is what the frontend's pagination component echoes
    // back on the next request; the controller's local toPageResponse helper must mirror the
    // PaginationUtil contract used by every other listing endpoint or the next-page link breaks.
    assertThat(result.sort()).containsExactly("createdAt,ASC");
    verify(service).getEntriesByMission(eq(missionId), any(Pageable.class));
  }

  @Test
  void getFinanceEntries_rejectsUnknownSortField() {
    UUID missionId = UUID.randomUUID();

    // Whitelist guard: {@code participant.user.email} is NOT in {@link
    // MissionFinanceEntryController#ALLOWED_SORT} — a 400 here is what the global handler
    // surfaces, so ordering information about PII columns cannot leak via sort.
    assertThatThrownBy(
            () -> controller.getFinanceEntries(missionId, 0, 10, "participant.user.email,desc"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void getFinanceEntriesSum_delegatesToService() {
    UUID missionId = UUID.randomUUID();
    BigDecimal total = new BigDecimal("12345.67");
    when(service.calculateTotalSum(missionId)).thenReturn(total);

    BigDecimal result = controller.getFinanceEntriesSum(missionId);

    assertThat(result).isEqualByComparingTo(total);
    verify(service).calculateTotalSum(missionId);
  }

  @Test
  void createFinanceEntry_authenticatedCaller_passesDtoThroughToService() {
    UUID missionId = UUID.randomUUID();
    MissionFinanceEntryCreateDto request =
        new MissionFinanceEntryCreateDto(
            missionId, UUID.randomUUID(), "note", FinanceType.INCOME, new BigDecimal("500.00"));
    MissionFinanceEntryDto created = entry(missionId, FinanceType.INCOME, new BigDecimal("500.00"));
    when(service.createEntry(request)).thenReturn(created);

    // Non-null Jwt → controller must NOT redact, just pass the persisted DTO straight through.
    org.springframework.security.oauth2.jwt.Jwt jwt =
        org.springframework.security.oauth2.jwt.Jwt.withTokenValue("t")
            .header("alg", "none")
            .claim("sub", "tester")
            .build();
    MissionFinanceEntryDto result = controller.createFinanceEntry(request, jwt);

    assertThat(result).isSameAs(created);
    verify(service).createEntry(request);
  }

  @Test
  void updateFinanceEntry_forwardsBothPathAndBodyToService() {
    UUID entryId = UUID.randomUUID();
    MissionFinanceEntryUpdateDto request =
        new MissionFinanceEntryUpdateDto(
            "updated note", FinanceType.EXPENSE, new BigDecimal("999.99"), 3L);
    MissionFinanceEntryDto updated =
        entry(UUID.randomUUID(), FinanceType.EXPENSE, new BigDecimal("999.99"));
    when(service.updateEntry(entryId, request)).thenReturn(updated);

    MissionFinanceEntryDto result = controller.updateFinanceEntry(entryId, request);

    assertThat(result).isSameAs(updated);
    verify(service).updateEntry(entryId, request);
  }

  @Test
  void deleteFinanceEntry_delegatesToService() {
    UUID entryId = UUID.randomUUID();

    controller.deleteFinanceEntry(entryId);

    verify(service).deleteEntry(entryId);
  }
}
