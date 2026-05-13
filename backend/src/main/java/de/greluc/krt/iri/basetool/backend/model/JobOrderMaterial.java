package de.greluc.krt.iri.basetool.backend.model;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "job_order_material")
public class JobOrderMaterial extends AbstractEntity<UUID> {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "job_order_id", nullable = false)
  private JobOrder jobOrder;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "material_id", nullable = false)
  private Material material;

  @Column(nullable = false)
  private Integer minQuality;

  @Column(nullable = false)
  private Double amount;
}
