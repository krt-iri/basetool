package de.greluc.krt.iri.basetool.backend.model;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;

@Entity
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class StarSystem extends AbstractEntity<UUID> {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "id_system", unique = true)
  private Integer idSystem;

  @Column(nullable = false, unique = true)
  private String name;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(name = "is_available_live")
  private Boolean isAvailableLive;

  @Column(columnDefinition = "TEXT")
  private String wiki;

  @Column(name = "jurisdiction_name")
  private String jurisdictionName;

  @Column(name = "faction_name")
  private String factionName;
}
