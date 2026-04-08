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
public class Manufacturer extends AbstractEntity<UUID> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false, unique = true)
    private String abbreviation;

    @Column
    private String nickname;

    @Column
    private String wiki;

    @Column(columnDefinition = "TEXT")
    private String description;

    private boolean hidden = false;
}
