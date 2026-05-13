package de.greluc.krt.iri.basetool.backend.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.*;

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

  public void addItem(JobOrderHandoverItem item) {
    items.add(item);
    item.setJobOrderHandover(this);
  }
}
