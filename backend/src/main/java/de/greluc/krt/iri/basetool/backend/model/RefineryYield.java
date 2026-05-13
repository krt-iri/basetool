package de.greluc.krt.iri.basetool.backend.model;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

@Entity
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class RefineryYield extends AbstractEntity<UUID> {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne
  @JoinColumn(name = "terminal_id", nullable = false)
  private Terminal terminal;

  @ManyToOne
  @JoinColumn(name = "material_id", nullable = false)
  private Material material;

  @Column(nullable = false)
  private Integer yieldBonus;
}
