package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.Operation;
import de.greluc.krt.iri.basetool.backend.model.dto.OperationReferenceDto;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Spring Data repository for Operation. */
@Repository
public interface OperationRepository extends JpaRepository<Operation, UUID> {

  /**
   * Pre-fetches the missions of an operation, their participants, and the participants' user
   * references in a single query. Used by the payout calculation, which would otherwise trip the
   * lazy collection at every level (1 + N missions + N*M participants).
   */
  @EntityGraph(attributePaths = {"missions", "missions.participants", "missions.participants.user"})
  Optional<Operation> findWithMissionsAndParticipantsById(UUID id);

  /**
   * Multi-tenant variant of {@link #findAll(org.springframework.data.domain.Pageable)}: returns
   * every operation whose owning squadron matches {@code owningSquadronId}, or every operation when
   * {@code owningSquadronId} is {@code null} (admin "all squadrons" mode). Operations are a
   * strict-staffel aggregate.
   */
  @org.springframework.data.jpa.repository.Query(
      "SELECT o FROM Operation o WHERE (:owningSquadronId IS NULL OR o.owningSquadron.id ="
          + " :owningSquadronId)")
  org.springframework.data.domain.Page<Operation> findAllScoped(
      @org.springframework.data.repository.query.Param("owningSquadronId") UUID owningSquadronId,
      org.springframework.data.domain.Pageable pageable);

  /**
   * Slim id + name projection of every operation visible to the caller, sorted by name. Drives the
   * {@code /lookup} endpoint that feeds the mission-detail page's operation-picker dropdown -
   * pulling the full {@code OperationDto} payload via the regular list endpoint with {@code
   * size=1000} was the previous shape and exhausted DB and serialisation budget on every mission
   * page render. Honours the same {@code owningSquadronId IS NULL} sentinel as {@link
   * #findAllScoped} so admins without an active squadron see all operations.
   *
   * @param owningSquadronId scope filter, or {@code null} for "all squadrons" (admin)
   * @return slim reference DTOs, sorted by name ascending
   */
  @org.springframework.data.jpa.repository.Query(
      "SELECT new de.greluc.krt.iri.basetool.backend.model.dto.OperationReferenceDto(o.id, o.name)"
          + " FROM Operation o WHERE (:owningSquadronId IS NULL OR o.owningSquadron.id ="
          + " :owningSquadronId) ORDER BY o.name ASC")
  List<OperationReferenceDto> findAllReferenceScoped(
      @org.springframework.data.repository.query.Param("owningSquadronId") UUID owningSquadronId);

  /**
   * Free-text + status + scope search across operations. Mirrors the contract of {@code
   * MissionRepository.searchMissions} within the limits of the operation aggregate: operations have
   * no {@code plannedStartTime} of their own (that field lives on the underlying missions), so the
   * missions' date-range filter has no meaningful equivalent here and is deliberately omitted.
   * {@code query} is optional - a {@code null} cast removes the corresponding clause; the {@code
   * status IN (:status)} list is always applied (pass the full enum set to disable status
   * filtering).
   *
   * <p>Operations are a strict-staffel aggregate: a non-null {@code owningSquadronId} restricts the
   * result to operations owned by that squadron; {@code null} means "all squadrons" (admin mode).
   * Unlike missions, there is no cross-staffel public escape - operations of other squadrons are
   * never visible to non-admins.
   *
   * <p>Status values are passed as strings to keep the contract consistent with the missions
   * search; the JPA layer matches them against the {@code OperationStatus} enum's string
   * representation.
   *
   * @param query free-text name/description fragment, may be {@code null}
   * @param status status list (string names of {@code OperationStatus}); always applied
   * @param owningSquadronId scope filter, or {@code null} for "all squadrons" (admin)
   * @param pageable page request
   * @return paged matching operations
   */
  @Query(
      "SELECT o FROM Operation o WHERE (:owningSquadronId IS NULL OR o.owningSquadron.id ="
          + " :owningSquadronId) AND (CAST(:query AS string) IS NULL OR o.name ILIKE CONCAT('%',"
          + " CAST(:query AS string), '%') OR CAST(o.description AS string) ILIKE CONCAT('%',"
          + " CAST(:query AS string), '%')) AND (CAST(o.status AS string) IN (:status))")
  Page<Operation> searchOperations(
      @Param("query") String query,
      @Param("status") List<String> status,
      @Param("owningSquadronId") UUID owningSquadronId,
      Pageable pageable);
}
