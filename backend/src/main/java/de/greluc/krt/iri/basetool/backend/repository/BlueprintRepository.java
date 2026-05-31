package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.scwiki.Blueprint;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link Blueprint}. The R4 blueprint sync upserts by {@link
 * #findByScwikiUuid(UUID)} and soft-deletes vanished recipes via {@link
 * #markScwikiDeleted(Collection, Instant)}.
 */
@Repository
public interface BlueprintRepository extends JpaRepository<Blueprint, UUID> {

  /**
   * Upsert key for the blueprint sync: match an inbound Wiki blueprint to its local row.
   *
   * @param scwikiUuid the SC Wiki blueprint UUID
   * @return the blueprint if a previous sync persisted it
   */
  Optional<Blueprint> findByScwikiUuid(UUID scwikiUuid);

  /**
   * Returns the distinct Wiki item UUIDs referenced by blueprint ITEM ingredient lines. Feeds the
   * R4 closure-mode item sync (§8.4): a blueprint may reference an item UEX never placed in {@code
   * game_item}, so the closure run must also fetch those uuids and create the {@code WIKI_ONLY}
   * rows. The query reads {@code BlueprintIngredient.wikiItemUuid} (always persisted, even for
   * unresolved lines).
   *
   * @return distinct non-null Wiki item UUIDs referenced by ingredient lines
   */
  @Query(
      "SELECT DISTINCT i.wikiItemUuid FROM BlueprintIngredient i WHERE i.wikiItemUuid IS NOT NULL")
  java.util.List<UUID> findReferencedItemUuids();

  /**
   * Soft-deletes every blueprint whose {@code scwiki_uuid} is NOT in {@code seenScwikiUuids} and is
   * not already marked. Gated by the caller on a non-empty seen set so a failed / empty Wiki fetch
   * never wipes the recipe graph (§8.7).
   *
   * @param seenScwikiUuids the blueprint UUIDs successfully processed in the current run
   * @param now timestamp to stamp on the soft-deleted rows
   * @return number of rows marked deleted
   */
  @Modifying
  @Query(
      "UPDATE Blueprint b SET b.scwikiDeletedAt = :now "
          + "WHERE b.scwikiUuid IS NOT NULL "
          + "AND b.scwikiUuid NOT IN :seenScwikiUuids "
          + "AND b.scwikiDeletedAt IS NULL")
  int markScwikiDeleted(
      @Param("seenScwikiUuids") Collection<UUID> seenScwikiUuids, @Param("now") Instant now);

  /**
   * Unfiltered page of active (non-soft-deleted) blueprints for the admin blueprint page when no
   * search term is supplied. Kept separate from {@link #searchActive(String, Pageable)} so the
   * common no-filter load stays a plain indexed predicate and never binds a {@code null} into a
   * string function: passing a {@code null} named parameter into {@code LOWER(CONCAT(...))} makes
   * PostgreSQL infer {@code bytea} and fail the whole request with {@code function lower(bytea)
   * does not exist}. Sorting comes from the caller's whitelisted {@code Pageable}; the owned
   * collections stay lazy (batched on access by their {@code @BatchSize}).
   *
   * @param pageable page request (whitelisted sort)
   * @return a page of active blueprints
   */
  Page<Blueprint> findByScwikiDeletedAtIsNull(Pageable pageable);

  /**
   * Page of active (non-soft-deleted) blueprints whose output-item name or Wiki key contains {@code
   * q}, matched case-insensitively. The caller guarantees {@code q} is non-null and non-blank, so
   * the {@code LOWER(CONCAT(...))} predicate never receives a {@code null} bind (which PostgreSQL
   * would type as {@code bytea}). Sorting comes from the caller's whitelisted {@code Pageable}; the
   * owned collections stay lazy (batched on access by their {@code @BatchSize}).
   *
   * @param q case-insensitive output-name / Wiki-key substring; must be non-null and non-blank
   * @param pageable page request (whitelisted sort)
   * @return a page of matching active blueprints
   */
  @Query(
      "SELECT b FROM Blueprint b WHERE b.scwikiDeletedAt IS NULL "
          + "AND (LOWER(b.outputName) LIKE LOWER(CONCAT('%', :q, '%')) "
          + "OR LOWER(b.scwikiKey) LIKE LOWER(CONCAT('%', :q, '%')))")
  Page<Blueprint> searchActive(@Param("q") String q, Pageable pageable);
}
