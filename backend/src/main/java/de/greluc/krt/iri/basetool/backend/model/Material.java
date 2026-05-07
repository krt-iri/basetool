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
public class Material extends AbstractEntity<UUID> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "id_commodity", unique = true)
    private Integer idCommodity;

    @Column(nullable = false, unique = true)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MaterialType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "quantity_type")
    private QuantityType quantityType;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "code")
    private String code;

    @Column(name = "slug")
    private String slug;

    @Column(name = "kind")
    private String kind;

    @Column(name = "weight_scu")
    private Double weightScu;

    @Column(name = "price_buy")
    private Double priceBuy;

    @Column(name = "price_sell")
    private Double priceSell;

    @Column(name = "is_available")
    private Integer isAvailable;

    @Column(name = "is_available_live")
    private Integer isAvailableLive;

    @Column(name = "is_extractable")
    private Integer isExtractable;

    @Column(name = "is_mineral")
    private Integer isMineral;

    @Column(name = "is_raw")
    private Integer isRaw;

    @Column(name = "is_pure")
    private Integer isPure;

    @Column(name = "is_refined")
    private Integer isRefined;

    @Column(name = "is_refinable")
    private Integer isRefinable;

    @Column(name = "is_harvestable")
    private Integer isHarvestable;

    @Column(name = "is_buyable")
    private Integer isBuyable;

    @Column(name = "is_sellable")
    private Integer isSellable;

    @Column(name = "is_temporary")
    private Integer isTemporary;

    @Column(name = "is_illegal")
    private Integer isIllegal;

    @Column(name = "is_volatile_qt")
    private Integer isVolatileQt;

    @Column(name = "is_volatile_time")
    private Integer isVolatileTime;

    @Column(name = "is_inert")
    private Integer isInert;

    @Column(name = "is_explosive")
    private Integer isExplosive;

    @Column(name = "is_buggy")
    private Integer isBuggy;

    @Column(name = "is_fuel")
    private Integer isFuel;

    @Column(name = "is_manual_raw_material", nullable = false)
    private Boolean isManualRawMaterial = false;
    @Column(name = "is_job_order", nullable = false)
    private Boolean isJobOrder = false;

    @ManyToOne
    @JoinColumn(name = "refined_material_id")
    private Material refinedMaterial;

    @ManyToOne
    @JoinColumn(name = "category_id")
    private MaterialCategory category;
}
