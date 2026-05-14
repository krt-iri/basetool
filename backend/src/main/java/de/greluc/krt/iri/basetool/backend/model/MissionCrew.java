package de.greluc.krt.iri.basetool.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Mission Crew JPA entity. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"missionUnit"})
public class MissionCrew extends AbstractEntity<UUID> {

  @Getter(onMethod_ = @__(@Override))
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne
  @JoinColumn(name = "mission_ship_id", nullable = false)
  @JsonIgnore
  private MissionUnit missionUnit;

  @ManyToOne
  @JoinColumn(name = "mission_participant_id", nullable = false)
  private MissionParticipant participant;

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "mission_crew_job_types",
      joinColumns = @JoinColumn(name = "mission_crew_id"),
      inverseJoinColumns = @JoinColumn(name = "job_type_id"))
  private Set<JobType> jobTypes = new HashSet<>();
}
