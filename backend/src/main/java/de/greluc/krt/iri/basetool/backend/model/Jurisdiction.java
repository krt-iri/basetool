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
public class Jurisdiction extends AbstractEntity<UUID> {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "id_jurisdiction", unique = true)
    private Integer idJurisdiction;

    private String name;
    private String code;
    @Column(name = "is_available_live")
    private Boolean isAvailableLive;
    @Column(name = "nickname")
    private String nickname;
    @Column(name = "wiki")
    private String wiki;
    @Column(name = "faction_name")
    private String factionName;
}
