package de.greluc.krt.iri.basetool.backend.controller;

import de.greluc.krt.iri.basetool.backend.mapper.TerminalMapper;
import de.greluc.krt.iri.basetool.backend.model.Terminal;
import de.greluc.krt.iri.basetool.backend.model.dto.PageResponse;
import de.greluc.krt.iri.basetool.backend.model.dto.TerminalDto;
import de.greluc.krt.iri.basetool.backend.service.TerminalService;
import de.greluc.krt.iri.basetool.backend.web.PaginationUtil;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Read-mostly REST surface over the terminal catalog. UEX owns the table; the PUT endpoint is
 * intentionally narrow — only the {@code hidden} flag flows through, every other field passed in
 * the body is ignored.
 */
@RestController
@RequestMapping("/api/v1/terminals")
@RequiredArgsConstructor
@Transactional
public class TerminalController {

  private final TerminalService terminalService;
  private final TerminalMapper terminalMapper;

  /**
   * Paged terminal list with whitelist-enforced sort.
   *
   * @return paged terminal DTOs
   */
  @GetMapping
  public PageResponse<TerminalDto> getAllTerminals(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String sort) {
    Pageable pageable =
        PaginationUtil.createPageRequest(
            page, size, sort, Set.of("name", "id", "starSystemName"), "name");
    Page<Terminal> p = terminalService.getAllTerminals(pageable);
    List<TerminalDto> content = p.getContent().stream().map(terminalMapper::toDto).toList();
    return new PageResponse<>(
        content,
        p.getNumber(),
        p.getSize(),
        p.getTotalElements(),
        p.getTotalPages(),
        PaginationUtil.toSortStrings(p.getSort()));
  }

  /**
   * @param id terminal id
   * @return the terminal DTO
   */
  @GetMapping("/{id}")
  public TerminalDto getTerminal(@PathVariable @NotNull UUID id) {
    return terminalMapper.toDto(terminalService.getTerminal(id));
  }

  /**
   * Toggles the terminal's {@code hidden} flag. Only the {@code hidden} field from the body is
   * applied; everything else is ignored so an admin cannot rename a UEX-imported terminal via this
   * endpoint.
   *
   * @param id terminal id
   * @param terminalDto request body — only {@code hidden} is read
   * @return the persisted DTO
   */
  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('OFFICER', 'ADMIN')")
  public TerminalDto updateTerminal(
      @PathVariable @NotNull UUID id, @RequestBody @Valid @NotNull TerminalDto terminalDto) {
    // Here we just allow toggling visibility according to the requirement,
    // but we mimic a normal PUT. In the Admin view, we only change 'hidden'.
    return terminalMapper.toDto(terminalService.updateTerminalVisibility(id, terminalDto.hidden()));
  }
}
