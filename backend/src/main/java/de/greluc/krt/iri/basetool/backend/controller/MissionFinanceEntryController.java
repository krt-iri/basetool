package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.model.dto.MissionFinanceEntryCreateDto;
import de.greluc.krt.iri.basetool.backend.model.dto.MissionFinanceEntryDto;
import de.greluc.krt.iri.basetool.backend.model.dto.MissionFinanceEntryUpdateDto;
import de.greluc.krt.iri.basetool.backend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.backend.service.MissionFinanceEntryService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST surface over mission finance entries. Reads are mission-scoped (via {@code
 * /missions/{missionId}/finance-entries}); writes are entry-scoped (via {@code
 * /finance-entries/{entryId}}). Creation is intentionally {@code permitAll()} so guest participants
 * can record their own payouts; update/delete are gated by {@code
 * MissionSecurityService.canEditFinanceEntry} on the service layer.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MissionFinanceEntryController {

  private final MissionFinanceEntryService financeEntryService;

  /**
   * Paged finance entries for a mission.
   *
   * @return paged finance-entry DTOs
   */
  @GetMapping("/missions/{missionId}/finance-entries")
  @PreAuthorize("isAuthenticated()")
  public PageResponse<MissionFinanceEntryDto> getFinanceEntries(
      @PathVariable UUID missionId, Pageable pageable) {
    return toPageResponse(financeEntryService.getEntriesByMission(missionId, pageable));
  }

  /**
   * @param missionId mission id
   * @return the signed bottom-line of the mission (entries + refinery profit)
   */
  @GetMapping("/missions/{missionId}/finance-entries/sum")
  @PreAuthorize("isAuthenticated()")
  public BigDecimal getFinanceEntriesSum(@PathVariable UUID missionId) {
    return financeEntryService.calculateTotalSum(missionId);
  }

  /**
   * Creates a finance entry. Public — guests record their own line.
   *
   * @param dto create payload
   * @return the persisted entry
   */
  @PostMapping("/finance-entries")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("permitAll()")
  public MissionFinanceEntryDto createFinanceEntry(
      @RequestBody @Valid MissionFinanceEntryCreateDto dto) {
    return financeEntryService.createEntry(dto);
  }

  /**
   * Updates an entry. Service-layer {@code @PreAuthorize} checks owner-vs-admin.
   *
   * @param entryId entry id
   * @param dto update payload (carries the expected version)
   * @return the persisted entry
   */
  @PutMapping("/finance-entries/{entryId}")
  @PreAuthorize("isAuthenticated()")
  public MissionFinanceEntryDto updateFinanceEntry(
      @PathVariable UUID entryId, @RequestBody @Valid MissionFinanceEntryUpdateDto dto) {
    return financeEntryService.updateEntry(entryId, dto);
  }

  /**
   * Deletes an entry. Service-layer {@code @PreAuthorize} checks owner-vs-admin.
   *
   * @param entryId entry id
   */
  @DeleteMapping("/finance-entries/{entryId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("isAuthenticated()")
  public void deleteFinanceEntry(@PathVariable UUID entryId) {
    financeEntryService.deleteEntry(entryId);
  }

  private <T> PageResponse<T> toPageResponse(Page<T> page) {
    return new PageResponse<>(
        page.getContent(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.getSort().stream().map(o -> o.getProperty() + "," + o.getDirection()).toList());
  }
}
