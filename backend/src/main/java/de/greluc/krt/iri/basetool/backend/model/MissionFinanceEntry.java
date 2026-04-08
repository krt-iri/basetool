package de.greluc.krt.iri.basetool.backend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Getter
@Setter
@Builder
@ToString(exclude = {"mission", "participant"})
@NoArgsConstructor
@AllArgsConstructor
public class MissionFinanceEntry extends AbstractEntity<UUID> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mission_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Mission mission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mission_participant_id", nullable = false)
    private MissionParticipant participant;

    @Column(columnDefinition = "TEXT")
    private String note;

    @NotNull
    @Enumerated(EnumType.STRING)
    private FinanceType type;

    @NotNull
    @DecimalMin("0.0")
    @Column(precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;
}
