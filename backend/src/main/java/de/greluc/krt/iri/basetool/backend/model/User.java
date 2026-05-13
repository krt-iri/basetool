package de.greluc.krt.iri.basetool.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.jetbrains.annotations.Nullable;

/** User JPA entity. */
@Entity
@Table(name = "app_user")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class User extends AbstractEntity<UUID> {

  @Id private UUID id;

  @Override
  public boolean isNew() {
    return getVersion() == null;
  }

  private String username;
  private String displayName;
  private String firstName;
  private String lastName;
  private String email;

  @Min(1) @Max(20) @Column(name = "user_rank")
  private Integer rank;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(name = "last_read_announcement_id")
  private UUID lastReadAnnouncementId;

  @Column(name = "is_logistician")
  private boolean isLogistician = false;

  @Column(name = "is_mission_manager")
  private boolean isMissionManager = false;

  @Column(name = "in_keycloak")
  private boolean inKeycloak = true;

  @Nullable @Column(name = "join_date")
  private LocalDate joinDate;

  public String getEffectiveName() {
    return (displayName != null && !displayName.isBlank()) ? displayName : username;
  }

  // @ToString.Exclude on the LAZY @ManyToMany so a logged User outside of a
  // Hibernate session does not trigger LazyInitializationException — and so
  // toString() does not recurse User -> Role.permissions -> ... when Role
  // proxies are subsequently hydrated.
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "user_roles",
      joinColumns = @JoinColumn(name = "user_id"),
      inverseJoinColumns = @JoinColumn(name = "role_id"))
  @ToString.Exclude
  private Set<Role> roles = new HashSet<>();
}
