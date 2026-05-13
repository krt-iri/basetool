package de.greluc.krt.iri.basetool.backend.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class MissionParticipant extends AbstractEntity<UUID> {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne
  @JoinColumn(name = "mission_id", nullable = false)
  @com.fasterxml.jackson.annotation.JsonIgnore
  private Mission mission;

  @ManyToOne
  @JoinColumn(name = "user_id")
  private User user;

  private String guestName;

  @ManyToOne
  @JoinColumn(name = "squadron_id")
  private Squadron squadron;

  @ManyToOne
  @JoinColumn(name = "desired_mission_job_type_id")
  private JobType desiredMissionJobType;

  @ManyToOne
  @JoinColumn(name = "planned_task_job_type_id")
  private JobType plannedMissionJobType;

  @Column(columnDefinition = "TEXT")
  private String comment;

  private Instant startTime;
  private Instant endTime;

  @Enumerated(EnumType.STRING)
  private PayoutPreference payoutPreference = PayoutPreference.PAYOUT;
}
