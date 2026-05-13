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
public class Planet extends AbstractEntity<UUID> {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "id_planet", unique = true)
  private Integer idPlanet;

  private String name;
  private String code;

  @Column(name = "is_available_live")
  private Boolean isAvailableLive;

  @Column(name = "star_system_name")
  private String starSystemName;

  @Column(name = "faction_name")
  private String factionName;

  @Column(name = "jurisdiction_name")
  private String jurisdictionName;

  private Boolean isAvailable;
  private Boolean isVisible;
  private Boolean isDefault;
}
