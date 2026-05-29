package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.MaterialExternalAlias;
import de.greluc.krt.iri.basetool.backend.model.MaterialExternalAliasSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link MaterialExternalAlias}. Read access is sorted by external name
 * for stable rendering in the admin page; write access goes through the service layer (no direct
 * controller injection — pinned by the {@code controllerLayerShouldNotDependOnRepositoryLayer}
 * ArchUnit rule).
 */
@Repository
public interface MaterialExternalAliasRepository
    extends JpaRepository<MaterialExternalAlias, UUID> {

  /**
   * Returns every alias sorted by external name (case-sensitive — the DB unique index doesn't fold
   * case, so the table view shows exactly what the resolver will look up against).
   *
   * @return all alias rows ordered by external_name ascending
   */
  List<MaterialExternalAlias> findAllByOrderByExternalNameAsc();

  /**
   * Lookup used by the R3 Wiki commodity sync's resolution chain step 2. Case-insensitive by
   * convention: external systems sometimes carry the same name with different casing across patch
   * versions, so the lookup tolerates the drift.
   *
   * @param sourceSystem catalogue the alias belongs to
   * @param externalName case-insensitive external commodity name
   * @return the alias if present, empty otherwise
   */
  Optional<MaterialExternalAlias> findBySourceSystemAndExternalNameIgnoreCase(
      MaterialExternalAliasSource sourceSystem, String externalName);

  /**
   * Strict pre-create duplicate check used by the service layer to emit a 409 Conflict before the
   * DB unique constraint fires. Case-sensitive because the unique constraint on the DB side is
   * exact-match — a case-only difference would be allowed by the constraint and is therefore a
   * valid distinct alias here.
   *
   * @param sourceSystem catalogue the alias belongs to
   * @param externalName exact external commodity name
   * @return the alias if present, empty otherwise
   */
  Optional<MaterialExternalAlias> findBySourceSystemAndExternalName(
      MaterialExternalAliasSource sourceSystem, String externalName);
}
