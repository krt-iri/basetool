package de.greluc.krt.iri.basetool.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Dedicated aggregate for tracking ownership of a {@link Mission} with its own optimistic lock
 * version.
 *
 * <p>Rationale (Option A / multi-user concurrency on the mission detail page):
 *
 * <ul>
 *   <li>{@code Mission.owner} itself is marked with {@code @OptimisticLock(excluded = true)} so
 *       that changing the owner does NOT bump the parent {@code Mission.version} and therefore does
 *       not invalidate other users' open forms on the same mission.
 *   <li>To still prevent lost updates on concurrent owner changes, this entity maintains an own
 *       {@code @Version} counter on a 1:1 companion row keyed by {@code mission_id}.
 *   <li>Callers (service layer) change the owner transactionally via this entity and mirror the
 *       result into {@code Mission.owner} for backward-compatible reads.
 * </ul>
 */
@Entity
@Table(
    name = "mission_ownership",
    uniqueConstraints =
        @UniqueConstraint(name = "uk_mission_ownership_mission", columnNames = "mission_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"mission", "owner"})
public class MissionOwnership extends AbstractEntity<UUID> {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @OneToOne
  @JoinColumn(name = "mission_id", nullable = false, unique = true)
  private Mission mission;

  @ManyToOne
  @JoinColumn(name = "owner_id")
  private User owner;
}
