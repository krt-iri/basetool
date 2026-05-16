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

/** Terminal JPA entity. */
@Entity
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class Terminal extends AbstractEntity<UUID> {
  @Getter(onMethod_ = @__(@Override))
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "id_terminal", unique = true)
  private Integer idTerminal;

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

  @Column(name = "space_station_name")
  private String spaceStationName;

  @Column(name = "outpost_name")
  private String outpostName;

  @Column(name = "city_name")
  private String cityName;

  @Column(name = "faction_name")
  private String factionName;

  @Column(name = "company_name")
  private String companyName;

  private Boolean isAvailable;
  private Boolean isVisible;
  private Boolean isJumpPoint;
  private Boolean hasLoadingDock;
  private Boolean hasDockingPort;
  private Boolean hasFreightElevator;
  private Boolean isAutoLoad;
  private Boolean hidden = false;

  /**
   * If {@code true}, the UEX sync skips writing {@link #hasLoadingDock} from the upstream feed. Set
   * by admins/officers when UEX reports the wrong value; cleared by the same UI to hand control
   * back to UEX on the next sweep.
   */
  @Column(nullable = false)
  private Boolean hasLoadingDockOverridden = false;

  /**
   * If {@code true}, the UEX sync skips writing {@link #isAutoLoad} from the upstream feed. Set by
   * admins/officers when UEX reports the wrong value; cleared by the same UI to hand control back
   * to UEX on the next sweep.
   */
  @Column(nullable = false)
  private Boolean isAutoLoadOverridden = false;
}
