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

/** Faction JPA entity. */
@Entity
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class Faction extends AbstractEntity<UUID> {
  // {@code onMethod_ = @__(@Override)} tells Lombok to attach a real {@code @Override} to the
  // generated {@code getId()} so it's visibly tagged as the implementation of
  // {@code Persistable.getId()} (CodeQL flags missing override annotations on interface
  // implementations). The field-level {@code @Getter} wins over the class-level one for this
  // field so the override marker is attached without disabling Lombok for the rest of the class.
  @Getter(onMethod_ = @__(@Override))
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "id_faction", unique = true)
  private Integer idFaction;

  private String name;
  private String code;

  @Column(name = "is_available_live")
  private Boolean isAvailableLive;

  @Column(name = "wiki")
  private String wiki;

  @Column(name = "is_piracy")
  private Boolean isPiracy;

  @Column(name = "is_bounty_hunting")
  private Boolean isBountyHunting;
}
