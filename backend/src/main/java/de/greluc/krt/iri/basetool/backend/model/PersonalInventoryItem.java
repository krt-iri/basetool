package de.greluc.krt.iri.basetool.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Personal inventory entry owned by exactly one user (identified by the Keycloak
 * {@code sub} claim, stored in {@link #ownerSub}). Belongs to the user's personal
 * inventory; not to be confused with {@link InventoryItem}, which represents
 * material/location-bound squadron stock.
 *
 * <p>The location is referenced by its UEX numeric id (see {@link City#getIdCity()}
 * resp. {@link SpaceStation#getIdSpaceStation()}) plus a {@link PersonalInventoryLocationType}
 * discriminator. The location's display name is denormalized into
 * {@link #locationNameSnapshot} so that the entry can still be rendered offline /
 * if the location is later removed from UEX.
 *
 * <p>Optimistic locking is inherited via {@link AbstractEntity#getVersion()}.
 */
@Entity
@Table(name = "personal_inventory_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonalInventoryItem extends AbstractEntity<UUID> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Keycloak JWT {@code sub} of the owning user. Never expose to clients. */
    @Column(name = "owner_sub", nullable = false, length = 64)
    private String ownerSub;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 2000)
    private String note;

    @Column(name = "location_uex_id", nullable = false)
    private Integer locationUexId;

    @Enumerated(EnumType.STRING)
    @Column(name = "location_type", nullable = false, length = 20)
    private PersonalInventoryLocationType locationType;

    @Column(name = "location_name_snapshot", nullable = false, length = 255)
    private String locationNameSnapshot;

    @Column(nullable = false)
    private Integer quantity;
}
