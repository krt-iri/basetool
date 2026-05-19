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

  @Getter(onMethod_ = @__(@Override))
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

  /**
   * Audit field: the user who executed the handover (the logistician+ who clicked the button).
   * Stamped automatically by {@link
   * de.greluc.krt.iri.basetool.backend.service.JobOrderHandoverService} at create time from the
   * current JWT principal; {@code null} on rows created before the audit columns were introduced
   * and for anonymous executions (cross-staffel workspaces allow guest creates of the order itself,
   * but a handover always goes through an authenticated user). Cross-staffel workspace: the
   * executing user may belong to a different squadron than the order's owning one — see
   * MULTI_SQUADRON_PLAN.md section 4.4 / 7.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "executing_user_id")
  private User executingUser;

  /**
   * Audit field: the squadron the executing user belonged to at handover time. Captured as a
   * snapshot (rather than re-resolving via {@link #executingUser}) so a later squadron change on
   * that user does not retroactively rewrite the audit trail. {@code null} when the executing user
   * had no squadron assigned (admins / brand-new accounts).
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "executing_squadron_id")
  private Squadron executingSquadron;

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
