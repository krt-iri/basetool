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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST surface over mission finance entries. Reads are mission-scoped (via {@code
 * /missions/{missionId}/finance-entries}); writes are entry-scoped (via {@code
 * /finance-entries/{entryId}}). Creation stays open to anonymous callers so guest participants can
 * record their own payouts, but the {@code @squadronScopeService.canSeeMission} gate now blocks
 * unauthenticated POSTs against internal missions (audit finding C-2) and the response is redacted
 * for guests via {@link #cleanupParticipantForGuest}. Update/delete remain authenticated and are
 * gated on {@link
 * de.greluc.krt.iri.basetool.backend.service.MissionSecurityService#canEditFinanceEntry}.
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
   * Returns the signed bottom-line of the mission (entries + refinery profit).
   *
   * @param missionId mission id
   * @return the signed bottom-line of the mission (entries + refinery profit)
   */
  @GetMapping("/missions/{missionId}/finance-entries/sum")
  @PreAuthorize("isAuthenticated()")
  public BigDecimal getFinanceEntriesSum(@PathVariable UUID missionId) {
    return financeEntryService.calculateTotalSum(missionId);
  }

  /**
   * Creates a finance entry. Open to anonymous callers (guests recording their own payout line),
   * but gated by {@code @squadronScopeService.canSeeMission(dto.missionId)} so internal missions
   * are not writable without authentication, and the response is redacted via {@link
   * #cleanupParticipantForGuest} when the caller is anonymous to avoid leaking the linked
   * participant's email / real name / roles (audit finding C-2).
   *
   * @param dto create payload
   * @param jwt the caller's JWT, or {@code null} for anonymous callers
   * @return the persisted entry, with nested participant PII stripped for anonymous callers
   */
  @PostMapping("/finance-entries")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("@squadronScopeService.canSeeMission(#dto.missionId())")
  public MissionFinanceEntryDto createFinanceEntry(
      @RequestBody @Valid MissionFinanceEntryCreateDto dto, @AuthenticationPrincipal Jwt jwt) {
    MissionFinanceEntryDto created = financeEntryService.createEntry(dto);
    if (jwt == null) {
      created = cleanupFinanceEntryForGuest(created);
    }
    return created;
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

  /**
   * Redacts a finance-entry DTO for an anonymous caller — strips the nested participant entirely
   * AND the optimistic-lock version. Mirrors {@code MissionController#cleanupMissionForGuest}
   * (audit finding C-2) and tightens the response shape further (audit finding M-5): an anonymous
   * create gets an acknowledgement {@code {id, missionId, note, type, amount}} and no more — the
   * participant nesting is the original PII vector C-2 closed, and {@code version} is useless to an
   * anonymous caller (the update endpoint requires authentication anyway). The {@code
   * cleanup…ForGuest} name pattern is recognised by the ArchUnit rule {@code
   * anonymousReadableMissionEndpointsMustRedactGuestPii}.
   *
   * @param dto the persisted entry DTO
   * @return a slim acknowledgement DTO safe for anonymous callers
   */
  private MissionFinanceEntryDto cleanupFinanceEntryForGuest(MissionFinanceEntryDto dto) {
    return new MissionFinanceEntryDto(
        dto.id(),
        dto.missionId(),
        null, // participant — even the slim version is overkill for an anonymous acknowledgement
        dto.note(),
        dto.type(),
        dto.amount(),
        null // version — anonymous cannot update the entry; the value has no purpose here
        );
  }

  // The {@code cleanupParticipantForGuest} / {@code cleanupUserForGuest} helpers were removed as
  // part of audit finding M-5: the anonymous acknowledgement no longer carries the participant
  // nesting at all, so the targeted redaction of the nested {@code UserDto} became dead code.
  // The ArchUnit rule {@code anonymousReadableMissionEndpointsMustRedactGuestPii} accepts the
  // new {@code cleanupFinanceEntryForGuest} call site via the {@code cleanup…ForGuest} name
  // pattern.

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
