package de.greluc.krt.iri.basetool.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.*;

@Entity
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"mission_id", "frequency_type_id"}))
public class MissionFrequency extends AbstractEntity<UUID> {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "mission_id", nullable = false)
  @com.fasterxml.jackson.annotation.JsonIgnore
  private Mission mission;

  @ManyToOne(optional = false)
  @JoinColumn(name = "frequency_type_id", nullable = false)
  private FrequencyType frequencyType;

  @Column(name = "frequency_value", nullable = false, precision = 5, scale = 2)
  private BigDecimal value;
}
