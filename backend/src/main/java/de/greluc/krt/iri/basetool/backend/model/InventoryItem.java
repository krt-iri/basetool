package de.greluc.krt.iri.basetool.backend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.*;

import java.util.UUID;

@Entity
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class InventoryItem extends AbstractEntity<UUID> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // @ToString.Exclude on every LAZY association so a call to toString() outside
    // of a Hibernate session (e.g. from a log statement after the transaction
    // has committed) does not trigger LazyInitializationException. Matches the
    // pattern already used in Mission / Operation / RefineryOrder.
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    private User user;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id", nullable = false)
    @ToString.Exclude
    private Material material;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    @ToString.Exclude
    private Location location;

    @Min(0)
    @Max(1000)
    @Column(nullable = false)
    private Integer quality;

    @Min(0)
    @Column(nullable = false)
    private Double amount; // SCU

    @Column(nullable = false)
    private Boolean personal = false;

    @ManyToOne(optional = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "job_order_id", nullable = true)
    @ToString.Exclude
    private JobOrder jobOrder;

    @ManyToOne(optional = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "mission_id", nullable = true)
    @ToString.Exclude
    private Mission mission;

    @Column(name = "note", length = 1000)
    private String note;

    @Column(nullable = false)
    private Boolean delivered = false;
}
