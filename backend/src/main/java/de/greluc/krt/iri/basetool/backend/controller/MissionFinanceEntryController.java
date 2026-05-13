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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MissionFinanceEntryController {

  private final MissionFinanceEntryService financeEntryService;

  @GetMapping("/missions/{missionId}/finance-entries")
  @PreAuthorize("isAuthenticated()")
  public PageResponse<MissionFinanceEntryDto> getFinanceEntries(
      @PathVariable UUID missionId, Pageable pageable) {
    return toPageResponse(financeEntryService.getEntriesByMission(missionId, pageable));
  }

  @GetMapping("/missions/{missionId}/finance-entries/sum")
  @PreAuthorize("isAuthenticated()")
  public BigDecimal getFinanceEntriesSum(@PathVariable UUID missionId) {
    return financeEntryService.calculateTotalSum(missionId);
  }

  @PostMapping("/finance-entries")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("permitAll()")
  public MissionFinanceEntryDto createFinanceEntry(
      @RequestBody @Valid MissionFinanceEntryCreateDto dto) {
    return financeEntryService.createEntry(dto);
  }

  @PutMapping("/finance-entries/{entryId}")
  @PreAuthorize("isAuthenticated()")
  public MissionFinanceEntryDto updateFinanceEntry(
      @PathVariable UUID entryId, @RequestBody @Valid MissionFinanceEntryUpdateDto dto) {
    return financeEntryService.updateEntry(entryId, dto);
  }

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
