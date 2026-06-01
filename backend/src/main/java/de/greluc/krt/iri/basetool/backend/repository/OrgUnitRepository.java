package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.OrgUnit;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
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
public interface OrgUnitRepository extends JpaRepository<OrgUnit, UUID> {}
