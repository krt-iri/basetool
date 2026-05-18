package de.greluc.krt.iri.basetool.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Squadron JPA entity. */
@Entity
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class Squadron extends AbstractEntity<UUID> {

  /**
   * Canonical UUID of the IRIDIUM squadron. Seeded by Flyway migration V80 so backfills, tests, and
   * the application code (e.g. backfilling new aggregates with the default tenant) refer to a
   * deterministic identifier without an upfront database lookup. Do not reuse for any other
   * squadron.
   */
  public static final UUID IRIDIUM_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  @Getter(onMethod_ = @__(@Override))
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(unique = true, nullable = false)
  private String name;

  @Column(unique = true, nullable = false)
  private String shorthand;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(nullable = false)
  private boolean active = true;
}
