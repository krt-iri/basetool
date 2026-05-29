package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.scwiki.Blueprint;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
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
}
