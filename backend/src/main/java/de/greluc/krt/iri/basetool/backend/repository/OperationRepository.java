package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.Operation;
import de.greluc.krt.iri.basetool.backend.model.dto.OperationReferenceDto;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
