package de.greluc.krt.iri.basetool.backend.model;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;

import java.util.UUID;

@Entity
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class Ship extends AbstractEntity<UUID> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String name;

    @ManyToOne
    @JoinColumn(name = "ship_type_id", nullable = false)
    private ShipType shipType;

    @NotBlank(message = "{validation.insurance.required}")
    @Pattern(regexp = "^(0|([1-9]|[1-9][0-9]|1[0-1][0-9]|120)|LTI)$", message = "{validation.insurance.pattern}")
    private String insurance;

    @ManyToOne
    @JoinColumn(name = "location_id")
    private Location location;

    private boolean fitted;

    @ManyToOne
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;
}
