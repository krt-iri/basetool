package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.SpecialCommand;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link SpecialCommand}. Mirrors the surface of {@code
 * SquadronRepository} (find-by-shorthand, case-insensitive uniqueness checks, active-only paged
 * listing) so the eventual {@code SpecialCommandService} layer in R2.b can reuse the same query
 * idioms.
 *
 * <p>Hibernate's single-table inheritance filter automatically narrows every query to {@code WHERE
 * kind = 'SPECIAL_COMMAND'} based on the {@link SpecialCommand} {@code @DiscriminatorValue}, so
 * the SQUADRON-discriminated rows that V94 copied into {@code org_unit} are invisible to this
 * repository. No explicit predicate or {@code @Query} override is needed.
 */
@Repository
public interface SpecialCommandRepository extends JpaRepository<SpecialCommand, UUID> {

  /**
   * Returns the SK whose {@code shorthand} matches the given string exactly. Used by the admin UI
   * to dereference a Spezialkommando from its short identifier (e.g. resolving a chip click on
   * "ALPHA" to the underlying row). Empty when no SK carries the given shorthand.
   *
   * @param shorthand the short identifier to look up; case-sensitive, never {@code null}.
   * @return the matching SK if present, empty otherwise.
   */
  Optional<SpecialCommand> findByShorthand(String shorthand);

  /**
   * Case-insensitive existence check on the name column. Used by {@code
   * SpecialCommandService.create} (R2.b) to surface a 409 Conflict before the SQL UNIQUE
   * constraint trips, so the error reaches the admin UI as a structured validation error rather
   * than a generic optimistic-lock failure.
   *
   * @param name the proposed name; never {@code null}.
   * @return {@code true} iff at least one SK already carries this name (case-insensitive).
   */
  boolean existsByNameIgnoreCase(String name);

  /**
   * Case-insensitive existence check on the name column, excluding the row with the given id.
   * Used by the rename path: the admin may keep the existing name unchanged (a no-op uniqueness
   * collision should not block the save), but cannot rename one SK onto another existing SK's
   * name.
   *
   * @param name the proposed name; never {@code null}.
   * @param id the id of the SK currently being renamed; never {@code null}.
   * @return {@code true} iff at least one OTHER SK already carries this name (case-insensitive).
   */
  boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

  /**
   * Returns every active SK in an arbitrary order, intended for dropdown population (small result
   * sets, no pagination concern). Soft-deleted SKs ({@code active = false}) are excluded.
   *
   * @return list of active SKs; never {@code null}, possibly empty.
   */
  List<SpecialCommand> findAllByActiveTrue();

  /**
   * Paged version of {@link #findAllByActiveTrue()} for the admin list view, which may grow large
   * enough to warrant pagination. The {@link Pageable} carries the sort instructions (defaulting
   * to {@code name ASC} when the controller omits a sort).
   *
   * @param pageable the page request (size, offset, sort); never {@code null}.
   * @return page of active SKs in the requested order.
   */
  Page<SpecialCommand> findAllByActiveTrue(Pageable pageable);
}
