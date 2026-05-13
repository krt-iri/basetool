package de.greluc.krt.iri.basetool.backend.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Job Order Handover JPA entity. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "job_order_handover")
public class JobOrderHandover extends AbstractEntity<UUID> {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "job_order_id", nullable = false)
  private JobOrder jobOrder;

  @Column(name = "handover_time", nullable = false)
  private Instant handoverTime;

  @Column(name = "recipient_handle", nullable = false)
  private String recipientHandle;

  @Column(name = "recipient_squadron")
  private String recipientSquadron;

  @OneToMany(
      mappedBy = "jobOrderHandover",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @Builder.Default
  private Set<JobOrderHandoverItem> items = new HashSet<>();

  /** Adds a handover item and keeps the bidirectional back-reference in sync. */
  public void addItem(JobOrderHandoverItem item) {
    items.add(item);
    item.setJobOrderHandover(this);
  }
}
