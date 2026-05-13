package de.greluc.krt.iri.basetool.backend.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Job Order JPA entity. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "job_order")
public class JobOrder extends AbstractEntity<UUID> {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "display_id", insertable = false, updatable = false)
  @org.hibernate.annotations.Generated
  private Integer displayId;

  @Column(nullable = false)
  private String squadron;

  @Column private String handle;

  @Column private Integer priority;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @Builder.Default
  private JobOrderStatus status = JobOrderStatus.OPEN;

  @OneToMany(
      mappedBy = "jobOrder",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @Builder.Default
  private Set<JobOrderMaterial> materials = new HashSet<>();

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "job_order_assignees",
      joinColumns = @JoinColumn(name = "job_order_id"),
      inverseJoinColumns = @JoinColumn(name = "user_id"))
  @Builder.Default
  private Set<User> assignees = new HashSet<>();

  @OneToMany(
      mappedBy = "jobOrder",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @Builder.Default
  private Set<JobOrderHandover> handovers = new HashSet<>();

  /** Adds a material and keeps the bidirectional back-reference in sync. */
  public void addMaterial(JobOrderMaterial material) {
    materials.add(material);
    material.setJobOrder(this);
  }
}
