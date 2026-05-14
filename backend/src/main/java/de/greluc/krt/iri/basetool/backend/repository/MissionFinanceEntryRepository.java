package de.greluc.krt.iri.basetool.backend.repository;

import de.greluc.krt.iri.basetool.backend.model.MissionFinanceEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Spring Data repository for Mission Finance Entry. */
@Repository
public interface MissionFinanceEntryRepository extends JpaRepository<MissionFinanceEntry, UUID> {

  /** Returns every entity matching the derived {@code findAllByMissionId} criteria. */
  Page<MissionFinanceEntry> findAllByMissionId(UUID missionId, Pageable pageable);

  /** Returns every entity matching the derived {@code findAllByMissionId} criteria. */
  List<MissionFinanceEntry> findAllByMissionId(UUID missionId);

  /** Returns every entity matching the derived {@code findAllByMissionIdIn} criteria. */
  List<MissionFinanceEntry> findAllByMissionIdIn(List<UUID> missionIds);

  /** Derived Spring-Data delete - removes every row matching {@code MissionIdIn}. */
  @Modifying
  @Query("DELETE FROM MissionFinanceEntry m WHERE m.mission.id IN :missionIds")
  void deleteByMissionIdIn(@Param("missionIds") List<UUID> missionIds);
}
