package de.greluc.krt.iri.basetool.backend.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
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

  @ManyToOne
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

  @ManyToOne
  @JoinColumn(name = "operation_id")
  private Operation operation;

  @ManyToOne
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
}
