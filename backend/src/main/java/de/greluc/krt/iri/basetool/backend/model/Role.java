package de.greluc.krt.iri.basetool.backend.model;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;
import lombok.*;

/** Role JPA entity. */
@Entity
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class Role extends AbstractEntity<Long> {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /**
   * Stable, machine-readable identifier (e.g. {@code ADMIN}, {@code OFFICER}). Set once at seed
   * time and not updatable afterwards. {@link #name} is the human-readable display label and may be
   * renamed by an admin without changing the role's identity; the seed logic in DataInitializer
   * matches against this code so a renamed role no longer triggers a silent re-create with default
   * permissions on the next boot.
   */
  @Column(unique = true, nullable = false, updatable = false, length = 64)
  private String code;

  @Column(unique = true, nullable = false)
  private String name;

  @Column(columnDefinition = "TEXT")
  private String description;

  // @ToString.Exclude on the LAZY ElementCollection so a logged Role outside
  // of a Hibernate session does not trigger LazyInitializationException
  // (matches the InventoryItem / Mission / RefineryOrder pattern).
  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"))
  @Column(name = "permission")
  @ToString.Exclude
  private Set<String> permissions = new HashSet<>();
}
