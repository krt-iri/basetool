package de.greluc.krt.iri.basetool.backend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.*;

/** Job Type JPA entity. */
@Entity
@Getter
@Setter
@ToString(exclude = "parent")
@NoArgsConstructor
@AllArgsConstructor
public class JobType extends AbstractEntity<UUID> {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(unique = true, nullable = false)
  private String name;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @NotNull private JobTypeArchetype archetype;

  @ManyToOne
  @JoinColumn(name = "parent_id")
  private JobType parent;

  @Column(nullable = false)
  private boolean active = true;

  @Column(nullable = false)
  private boolean isLeadershipRole = false;
}
