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

/** Space Station JPA entity. */
@Entity
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class SpaceStation extends AbstractEntity<UUID> {
  @Getter(onMethod_ = @__(@Override))
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "id_space_station", unique = true)
  private Integer idSpaceStation;

  private String name;
  private String code;

  @Column(name = "is_available_live")
  private Boolean isAvailableLive;

  @Column(name = "nickname")
  private String nickname;

  @Column(name = "star_system_name")
  private String starSystemName;

  @Column(name = "planet_name")
  private String planetName;

  @Column(name = "orbit_name")
  private String orbitName;

  @Column(name = "moon_name")
  private String moonName;

  @Column(name = "city_name")
  private String cityName;

  @Column(name = "faction_name")
  private String factionName;

  @Column(name = "jurisdiction_name")
  private String jurisdictionName;

  private Boolean isAvailable;
  private Boolean isVisible;
  private Boolean isDefault;
  private Boolean isMonitored;
  private Boolean isArmistice;
  private Boolean isLandable;
  private Boolean isDecommissioned;
  private Boolean isLagrange;
  private Boolean isJumpPoint;
  private Boolean hasQuantumMarker;
  private Boolean hasTradeTerminal;
  private Boolean hasHabitation;
  private Boolean hasRefinery;
  private Boolean hasCargoCenter;
  private Boolean hasClinic;
  private Boolean hasFood;
  private Boolean hasShops;
  private Boolean hasRefuel;
  private Boolean hasRepair;
  private Boolean hasGravity;
  private Boolean hasLoadingDock;
  private Boolean hasDockingPort;
  private Boolean hasFreightElevator;
  private String padTypes;
}
