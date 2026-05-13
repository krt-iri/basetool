package de.greluc.krt.iri.basetool.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Job Order Handover Item JPA entity. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "job_order_handover_item")
public class JobOrderHandoverItem extends AbstractEntity<UUID> {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "job_order_handover_id", nullable = false)
  private JobOrderHandover jobOrderHandover;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "material_id", nullable = false)
  private Material material;

  @Column(nullable = false)
  private Integer quality;

  @Column(nullable = false)
  private Double amount;

  @Column(name = "location_name")
  private String locationName;
}
