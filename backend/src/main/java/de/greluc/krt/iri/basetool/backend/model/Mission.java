package de.greluc.krt.iri.basetool.backend.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.OptimisticLock;

/** Mission JPA entity. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"participants", "assignedUnits", "subMissions", "financeEntries"})
public class Mission extends AbstractEntity<UUID> {

  @Getter(onMethod_ = @__(@Override))
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  private String name;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(length = 2048)
  private String calendarLink;

  private String status; // e.g., PLANNED, ACTIVE, COMPLETED

  private Instant meetingTime;
  private Instant plannedStartTime;
  private Instant actualStartTime;
  private Instant plannedEndTime;
  private Instant actualEndTime;

  @Column(name = "is_internal", nullable = false)
  private Boolean isInternal = false;

  /**
   * Section-scoped optimistic-lock counter for the {@code core} patch endpoint (name, description,
   * calendar link, status, operation). Independent of {@link AbstractEntity#getVersion()} so that
   * concurrent edits on {@code schedule} and {@code flags} do not produce spurious 409 conflicts.
   * Marked {@code @OptimisticLock(excluded = true)} so bumping it does not in turn bump the global
   * {@link AbstractEntity#getVersion()}.
   */
  @Column(name = "core_version", nullable = false)
  @OptimisticLock(excluded = true)
  private Long coreVersion = 0L;

  /**
   * Section-scoped optimistic-lock counter for the {@code schedule} patch endpoint (meeting,
   * planned-start, planned-end, actual-start, actual-end). Status-driven auto-transitions that set
   * {@code actualStartTime} (PLANNED → ACTIVE) bump this counter via {@code …WithinTransaction}.
   */
  @Column(name = "schedule_version", nullable = false)
  @OptimisticLock(excluded = true)
  private Long scheduleVersion = 0L;

  /**
   * Section-scoped optimistic-lock counter for the {@code flags} patch endpoint ({@code
   * isInternal}).
   */
  @Column(name = "flags_version", nullable = false)
  @OptimisticLock(excluded = true)
  private Long flagsVersion = 0L;

  @OneToMany(mappedBy = "mission", cascade = CascadeType.ALL, orphanRemoval = true)
  @OptimisticLock(excluded = true)
  private Set<MissionParticipant> participants = new HashSet<>();

  @OneToMany(mappedBy = "mission", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("name ASC")
  @OptimisticLock(excluded = true)
  private Set<MissionUnit> assignedUnits = new LinkedHashSet<>();

  @OneToMany(mappedBy = "mission", cascade = CascadeType.ALL, orphanRemoval = true)
  @OptimisticLock(excluded = true)
  private Set<MissionFrequency> frequencies = new HashSet<>();

  @OneToMany(mappedBy = "mission", cascade = CascadeType.ALL, orphanRemoval = true)
  @OptimisticLock(excluded = true)
  private Set<MissionFinanceEntry> financeEntries = new HashSet<>();

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_mission_id")
  @com.fasterxml.jackson.annotation.JsonIgnore
  private Mission parent;

  @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
  @OptimisticLock(excluded = true)
  private Set<Mission> subMissions = new HashSet<>();

  @OneToMany(mappedBy = "mission")
  @OrderBy("createdAt DESC")
  @com.fasterxml.jackson.annotation.JsonIgnore
  @OptimisticLock(excluded = true)
  private Set<InventoryItem> inventoryEntries = new LinkedHashSet<>();

  @OneToMany(mappedBy = "mission")
  @OrderBy("startedAt DESC")
  @com.fasterxml.jackson.annotation.JsonIgnore
  @OptimisticLock(excluded = true)
  private Set<RefineryOrder> refineryOrders = new LinkedHashSet<>();

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "operation_id")
  @OptimisticLock(excluded = true)
  private Operation operation;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "owner_id")
  @OptimisticLock(excluded = true)
  private User owner;

  @ManyToMany
  @JoinTable(
      name = "mission_managers",
      joinColumns = @JoinColumn(name = "mission_id"),
      inverseJoinColumns = @JoinColumn(name = "user_id"))
  @OptimisticLock(excluded = true)
  private Set<User> managers = new HashSet<>();

  /**
   * Squadron that owns this mission. Set at creation time from the caller's active squadron and
   * immutable afterwards. Gates read/write access together with {@link #isInternal}: non-internal
   * missions are visible across squadrons, internal ones are restricted to the owning squadron and
   * admins.
   *
   * <p>Legacy field — kept authoritative during the R4 dual-write soak. The plan-aligned {@link
   * #owningOrgUnit} mirror field is kept in sync by {@link #syncOwnerFields()} on every lifecycle
   * event. A later release will drop this field along with the matching DB column once {@code
   * owning_org_unit_id} has soaked one full release cycle in production.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "owning_squadron_id", nullable = false)
  @OptimisticLock(excluded = true)
  private Squadron owningSquadron;

  /**
   * Org-unit owner of this mission — the R4 dual-write mirror of {@link #owningSquadron}. Pointed
   * at the {@code owning_org_unit_id} FK column that Flyway migration V96 added in R1, kept
   * synchronised with the legacy field by {@link #syncOwnerFields()}. Currently always a {@link
   * Squadron} subclass instance (the application's mission-create paths still stamp the legacy
   * field first); once R5 lands the owner-picker UI this widens to hold {@link SpecialCommand}
   * instances too.
   *
   * <p>JPA-nullable for the R4 soak window so a missed sync does not break inserts — the lifecycle
   * callback should leave this set, but the column stays nullable until the next release tightens
   * it to NOT NULL.
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "owning_org_unit_id")
  @OptimisticLock(excluded = true)
  private OrgUnit owningOrgUnit;

  /**
   * Lifecycle hook that keeps {@link #owningSquadron} and {@link #owningOrgUnit} aligned on every
   * INSERT / UPDATE / SELECT path. {@code owningSquadron} is the authoritative source during the R4
   * soak — the legacy field wins when both are set on the in-memory entity. The reverse copy runs
   * only when the legacy field is {@code null} and the org-unit reference happens to point at a
   * Squadron, which covers the future case where a Spezialkommando R5 caller writes only the new
   * field on a Squadron-owned aggregate.
   */
  @PrePersist
  @PreUpdate
  @PostLoad
  private void syncOwnerFields() {
    if (owningSquadron != null) {
      owningOrgUnit = owningSquadron;
    } else if (owningOrgUnit instanceof Squadron s) {
      owningSquadron = s;
    }
  }
}
