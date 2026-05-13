package de.greluc.krt.iri.basetool.backend.model;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

@Entity
@Getter
@Setter
@ToString
@NoArgsConstructor
public class FrequencyType extends AbstractEntity<UUID> {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(unique = true, nullable = false)
  private String name;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(nullable = false)
  private boolean active = true;

  @Column(nullable = false)
  private Integer sortIndex = 0;
}
