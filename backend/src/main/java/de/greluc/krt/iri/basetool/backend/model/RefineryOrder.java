package de.greluc.krt.iri.basetool.backend.model;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
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

    @PositiveOrZero
    private Long durationMinutes;

    @ManyToOne
    @JoinColumn(name = "refining_method_id")
    private RefiningMethod refiningMethod;

    /**
     * Auftragskosten. Muss >= 0 sein. Optional: 0 wird beim Speichern wie "nicht gesetzt"
     * behandelt und als {@code null} persistiert. Die Profit-Berechnung behandelt {@code null} als 0.
     */
    @PositiveOrZero
    private Double expenses;

    /**
     * Sonstige Kosten neben den regulaeren {@link #expenses}. Muss >= 0 sein.
     * Optional: 0 wird beim Speichern wie "nicht gesetzt" behandelt und als {@code null} persistiert.
     */
    @PositiveOrZero
    private Double otherExpenses;

    /**
     * Einnahmen durch den Verkauf roher Erze ("Ore Sales"). Muss >= 0 sein.
     * Optional: 0 wird beim Speichern wie "nicht gesetzt" behandelt und als {@code null} persistiert.
     */
    @PositiveOrZero
    private Double oreSales;

    /**
     * Berechneter Gewinn/Verlust: oreSales - expenses - otherExpenses. Kann negativ sein.
     * Wird nicht persistiert, sondern serverseitig aus den Rohdaten abgeleitet.
     */
    @Transient
    public Double getProfit() {
        double sales = oreSales != null ? oreSales : 0d;
        double costs = expenses != null ? expenses : 0d;
        double other = otherExpenses != null ? otherExpenses : 0d;
        return sales - costs - other;
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RefineryOrderStatus status = RefineryOrderStatus.OPEN;

    @OneToMany(mappedBy = "refineryOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @Valid
    private Set<RefineryGood> goods = new HashSet<>();
}
