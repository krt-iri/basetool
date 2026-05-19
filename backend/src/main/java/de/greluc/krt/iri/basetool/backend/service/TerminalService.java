package de.greluc.krt.iri.basetool.backend.service;

import de.greluc.krt.iri.basetool.backend.model.Terminal;
import de.greluc.krt.iri.basetool.backend.repository.TerminalRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read service plus visibility toggle for the terminal catalog. The records themselves are owned by
 * {@link UexUniverseSyncService}; this service only exposes the read API and the admin-only {@code
 * hidden} flag flip.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TerminalService {

  private final TerminalRepository terminalRepository;

  /**
   * Returns paged terminal list (includes hidden — frontend filters via {@code includeHidden}).
   *
   * @param pageable page request
   * @return paged terminal list (includes hidden — frontend filters via {@code includeHidden})
   */
  public Page<Terminal> getAllTerminals(Pageable pageable) {
    return terminalRepository.findAll(pageable);
  }

  /**
   * Returns the terminal.
   *
   * @param id terminal primary key
   * @return the terminal
   * @throws de.greluc.krt.iri.basetool.backend.exception.NotFoundException when no terminal matches
   */
  public Terminal getTerminal(UUID id) {
    return terminalRepository
        .findById(id)
        .orElseThrow(
            () ->
                new de.greluc.krt.iri.basetool.backend.exception.NotFoundException(
                    "Terminal not found"));
  }

  /**
   * Flips the {@code hidden} flag on a terminal.
   *
   * @param id terminal primary key
   * @param hidden new flag value
   * @return the persisted terminal
   */
  @Transactional
  public Terminal updateTerminalVisibility(UUID id, boolean hidden) {
    Terminal terminal = getTerminal(id);
    terminal.setHidden(hidden);
    return terminalRepository.save(terminal);
  }

  /**
   * Pins {@code hasLoadingDock} to the supplied value and marks it as admin-overridden so the next
   * UEX sweep leaves the value column untouched.
   *
   * @param id terminal primary key
   * @param value desired {@code hasLoadingDock} value
   * @return the persisted terminal
   */
  @Transactional
  public Terminal setLoadingDockOverride(UUID id, boolean value) {
    Terminal terminal = getTerminal(id);
    terminal.setHasLoadingDock(value);
    terminal.setHasLoadingDockOverridden(true);
    return terminalRepository.save(terminal);
  }

  /**
   * Releases the admin pin on {@code hasLoadingDock} and immediately reverts the value column to
   * the last UEX-reported state ({@link Terminal#getUexHasLoadingDock()}), so consumers like the
   * materials-overview filter and the UEX-source chip stop seeing the stale admin-pinned value
   * before the next UEX sweep runs.
   *
   * <p>If the terminal has never been synced yet, {@code uexHasLoadingDock} is {@code null} and the
   * value column is cleared to {@code null} too — that maps to "unknown" in every consumer and is
   * the correct semantics for "I do not have a UEX value yet, fall back to whatever the next sweep
   * tells me".
   *
   * @param id terminal primary key
   * @return the persisted terminal
   */
  @Transactional
  public Terminal clearLoadingDockOverride(UUID id) {
    Terminal terminal = getTerminal(id);
    terminal.setHasLoadingDockOverridden(false);
    terminal.setHasLoadingDock(terminal.getUexHasLoadingDock());
    return terminalRepository.save(terminal);
  }

  /**
   * Pins {@code isAutoLoad} to the supplied value and marks it as admin-overridden so the next UEX
   * sweep leaves the value column untouched.
   *
   * @param id terminal primary key
   * @param value desired {@code isAutoLoad} value
   * @return the persisted terminal
   */
  @Transactional
  public Terminal setAutoLoadOverride(UUID id, boolean value) {
    Terminal terminal = getTerminal(id);
    terminal.setIsAutoLoad(value);
    terminal.setIsAutoLoadOverridden(true);
    return terminalRepository.save(terminal);
  }

  /**
   * Releases the admin pin on {@code isAutoLoad} and immediately reverts the value column to the
   * last UEX-reported state ({@link Terminal#getUexIsAutoLoad()}), so consumers like the
   * materials-overview filter and the UEX-source chip stop seeing the stale admin-pinned value
   * before the next UEX sweep runs.
   *
   * <p>If the terminal has never been synced yet, {@code uexIsAutoLoad} is {@code null} and the
   * value column is cleared to {@code null} too — that maps to "unknown" in every consumer and is
   * the correct semantics for "I do not have a UEX value yet, fall back to whatever the next sweep
   * tells me".
   *
   * @param id terminal primary key
   * @return the persisted terminal
   */
  @Transactional
  public Terminal clearAutoLoadOverride(UUID id) {
    Terminal terminal = getTerminal(id);
    terminal.setIsAutoLoadOverridden(false);
    terminal.setIsAutoLoad(terminal.getUexIsAutoLoad());
    return terminalRepository.save(terminal);
  }
}
