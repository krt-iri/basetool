package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.RefineryOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RefineryOrderRepository extends JpaRepository<RefineryOrder, UUID> {
    boolean existsByLocationId(UUID locationId);
    
    @EntityGraph(attributePaths = {"owner", "location", "mission", "refiningMethod"})
    List<RefineryOrder> findByMissionId(UUID missionId);

    @EntityGraph(attributePaths = {"owner", "location", "mission", "refiningMethod"})
    List<RefineryOrder> findByMissionIdAndOwnerId(UUID missionId, UUID ownerId);

    @EntityGraph(attributePaths = {"owner", "location", "mission", "refiningMethod"})
    List<RefineryOrder> findByMissionIdIn(List<UUID> missionIds);

    @EntityGraph(attributePaths = {"owner", "location", "mission", "refiningMethod"})
    List<RefineryOrder> findByOwnerId(UUID ownerId);
    
    @EntityGraph(attributePaths = {"owner", "location", "mission", "refiningMethod"})
    Page<RefineryOrder> findByOwnerId(UUID ownerId, Pageable pageable);

    @EntityGraph(attributePaths = {"owner", "location", "mission", "refiningMethod"})
    Page<RefineryOrder> findByOwnerIdAndStatusIn(UUID ownerId, List<de.greluc.krt.iri.basetool.backend.model.RefineryOrderStatus> statuses, Pageable pageable);

    @EntityGraph(attributePaths = {"owner", "location", "mission", "refiningMethod"})
    Page<RefineryOrder> findByStatusIn(List<de.greluc.krt.iri.basetool.backend.model.RefineryOrderStatus> statuses, Pageable pageable);

    @EntityGraph(attributePaths = {"owner", "location", "mission", "refiningMethod"})
    Page<RefineryOrder> findAll(Pageable pageable);

    @Modifying
    @Query("UPDATE RefineryOrder r SET r.mission = null WHERE r.mission.id IN :missionIds")
    void unlinkMissions(@Param("missionIds") List<UUID> missionIds);
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE RefineryOrder r SET r.owner = :newUser WHERE r.owner = :oldUser")
    void updateOwner(@org.jetbrains.annotations.NotNull de.greluc.krt.iri.basetool.backend.model.User oldUser, @org.jetbrains.annotations.NotNull de.greluc.krt.iri.basetool.backend.model.User newUser);
}
