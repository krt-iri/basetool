package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.OrgUnit;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository over the polymorphic {@link OrgUnit} base entity. Unlike {@link
 * SquadronRepository} and {@link SpecialCommandRepository} — each narrowed to one discriminator
 * subtype — this repository loads {@code org_unit} rows of <em>either</em> kind through Hibernate's
 * single-table inheritance, returning the matching concrete subclass ({@code Squadron} or {@code
 * SpecialCommand}) per row.
 *
 * <p>Used by {@code MissionService} when stamping a participant's affiliations: a participant may
 * be linked to a Staffel and one or more Spezialkommandos at once, so the service resolves the
 * caller's membership org-unit ids to managed {@link OrgUnit} entities here rather than branching
 * on kind and dispatching to the two kind-specific repositories.
 */
@Repository
public interface OrgUnitRepository extends JpaRepository<OrgUnit, UUID> {

  /**
   * Counts how many of the given org-unit ids are flagged {@code is_profit_eligible} — works across
   * both kinds (Squadron + SpecialCommand) via single-table inheritance. Drives {@code
   * OwnerScopeService.canViewJobOrders()}: a caller may enter the Job-Order area iff at least one
   * of their membership org units is profit-eligible, so the service only needs the {@code > 0}
   * answer. Callers must pass a non-empty collection — an empty {@code IN ()} renders
   * inconsistently across dialects, so {@code OwnerScopeService} short-circuits the empty case
   * before calling this.
   *
   * @param ids the org-unit ids to inspect (the caller's membership ids); must be non-empty.
   * @return the number of those ids whose org unit is profit-eligible; {@code 0} when none.
   */
  @Query("SELECT COUNT(o) FROM OrgUnit o WHERE o.id IN :ids AND o.isProfitEligible = true")
  long countProfitEligibleByIdIn(@Param("ids") Collection<UUID> ids);

  /**
   * Loads every active, profit-eligible org unit across both kinds (Squadron + SpecialCommand) via
   * single-table inheritance. Backs the Profit-Bereich org chart, whose unit tier is exactly the
   * profit-eligible Staffeln + SKs; the caller splits the result by {@link OrgUnit#getKind()} into
   * the squadron and SK columns.
   *
   * @return the active profit-eligible org units in arbitrary order; never {@code null}, possibly
   *     empty.
   */
  @Query("SELECT o FROM OrgUnit o WHERE o.active = true AND o.isProfitEligible = true")
  List<OrgUnit> findActiveProfitEligible();
}
