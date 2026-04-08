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
public class Faction extends AbstractEntity<UUID> {
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
