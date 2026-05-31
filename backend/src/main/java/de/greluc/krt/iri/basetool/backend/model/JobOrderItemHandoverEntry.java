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

/**
 * One delivered line of a {@link JobOrderItemHandover}: a whole-unit {@link #amount} of a specific
 * {@link JobOrderItem} that changed hands during the handover. Persisting the reference to the
 * ordered line (rather than the bare game item) lets fulfilment increment that line's {@code
 * deliveredAmount} directly and drives the order's auto-completion check.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "job_order_item_handover_entry")
public class JobOrderItemHandoverEntry extends AbstractEntity<UUID> {

  @Getter(onMethod_ = @__(@Override))
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  /** Owning item handover. */
  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "job_order_item_handover_id", nullable = false)
  private JobOrderItemHandover jobOrderItemHandover;

  /** The ordered item line this delivery fulfils. */
  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "job_order_item_id", nullable = false)
  private JobOrderItem jobOrderItem;

  /** Number of whole units delivered for the referenced line in this handover. */
  @Column(nullable = false)
  private Integer amount;
}
