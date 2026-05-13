package de.greluc.krt.iri.basetool.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Mission Finance Entry JPA entity. */
@Entity
@Getter
@Setter
@Builder
@ToString(exclude = {"mission", "participant"})
@NoArgsConstructor
@AllArgsConstructor
public class MissionFinanceEntry extends AbstractEntity<UUID> {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "mission_id", nullable = false)
  @com.fasterxml.jackson.annotation.JsonIgnore
  private Mission mission;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "mission_participant_id", nullable = false)
  private MissionParticipant participant;

  @Column(columnDefinition = "TEXT")
  private String note;

  @NotNull @Enumerated(EnumType.STRING)
  private FinanceType type;

  @NotNull @DecimalMin("0.0") @Column(precision = 19, scale = 4, nullable = false)
  private BigDecimal amount;
}
