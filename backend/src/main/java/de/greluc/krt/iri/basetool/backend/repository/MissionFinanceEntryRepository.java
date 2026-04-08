package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.MissionFinanceEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MissionFinanceEntryRepository extends JpaRepository<MissionFinanceEntry, UUID> {

    Page<MissionFinanceEntry> findAllByMissionId(UUID missionId, Pageable pageable);

    List<MissionFinanceEntry> findAllByMissionId(UUID missionId);

    List<MissionFinanceEntry> findAllByMissionIdIn(List<UUID> missionIds);

    @Modifying
    @Query("DELETE FROM MissionFinanceEntry m WHERE m.mission.id IN :missionIds")
    void deleteByMissionIdIn(@Param("missionIds") List<UUID> missionIds);
}