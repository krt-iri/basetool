package de.greluc.krt.iri.basetool.backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class Moon extends AbstractEntity<UUID> {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "id_moon", unique = true)
    private Integer idMoon;

    private String name;
    private String code;
    @Column(name = "is_available_live")
    private Boolean isAvailableLive;
    @Column(name = "star_system_name")
    private String starSystemName;
    @Column(name = "planet_name")
    private String planetName;
    @Column(name = "orbit_name")
    private String orbitName;
    @Column(name = "faction_name")
    private String factionName;
    @Column(name = "jurisdiction_name")
    private String jurisdictionName;

    private Boolean isAvailable;
    private Boolean isVisible;
    private Boolean isDefault;
}
