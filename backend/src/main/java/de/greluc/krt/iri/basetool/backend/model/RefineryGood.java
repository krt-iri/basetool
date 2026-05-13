package de.greluc.krt.iri.basetool.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.*;

/** Refinery Good JPA entity. */
@Entity
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class RefineryGood extends AbstractEntity<UUID> {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne
  @JoinColumn(name = "input_material_id", nullable = false)
  private Material inputMaterial;

  @Column(nullable = false)
  @Min(1) private Integer inputQuantity;

  @ManyToOne
  @JoinColumn(name = "output_material_id", nullable = false)
  private Material outputMaterial;

  @Column(nullable = false)
  @Min(1) private Integer outputQuantity;

  @Column(nullable = false)
  @Min(0) @Max(1000) private Integer quality;

  @ManyToOne
  @JoinColumn(name = "refinery_order_id", nullable = false)
  @JsonIgnore
  private RefineryOrder refineryOrder;
}
