package de.greluc.krt.iri.basetool.backend.model;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class RefineryOrder extends AbstractEntity<UUID> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @ManyToOne
    @JoinColumn(name = "mission_id")
    private Mission mission;

    private Instant startedAt;

    @Positive
    private Long durationMinutes;

    @ManyToOne
    @JoinColumn(name = "refining_method_id")
    private RefiningMethod refiningMethod;

    @Positive
    private Double expenses;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RefineryOrderStatus status = RefineryOrderStatus.OPEN;

    @OneToMany(mappedBy = "refineryOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @Valid
    private Set<RefineryGood> goods = new HashSet<>();
}
