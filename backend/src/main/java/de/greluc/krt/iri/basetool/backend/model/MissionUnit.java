package de.greluc.krt.iri.basetool.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"mission", "crew"})
public class MissionUnit extends AbstractEntity<UUID> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "mission_id", nullable = false)
    @JsonIgnore
    private Mission mission;

    @ManyToOne
    @JoinColumn(name = "ship_type_id", nullable = true)
    private ShipType shipType;

    @ManyToOne
    @JoinColumn(name = "ship_id", nullable = true)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.SET_NULL)
    private Ship ship;

    @Column
    private Double frequency;

    @Column(nullable = false)
    private boolean highValueUnit = false;

    @Column(nullable = false)
    private String name;

    @OneToMany(mappedBy = "missionUnit", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<MissionCrew> crew = new HashSet<>();

}
