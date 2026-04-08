package de.greluc.krt.iri.basetool.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"missionUnit"})
public class MissionCrew extends AbstractEntity<UUID> {

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
        inverseJoinColumns = @JoinColumn(name = "job_type_id")
    )
    private Set<JobType> jobTypes = new HashSet<>();

}
