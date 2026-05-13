package de.greluc.krt.iri.basetool.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"material_id", "terminal_id"}))
public class MaterialPrice extends AbstractEntity<UUID> {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "material_id", nullable = false)
  private Material material;

  @ManyToOne(optional = false)
  @JoinColumn(name = "terminal_id", nullable = false)
  private Terminal terminal;

  private BigDecimal priceBuy;
  private BigDecimal priceSell;

  private Integer scuBuy;
  private Integer scuSell;
  private Integer scuSellStock;

  private Boolean statusBuy;
  private Boolean statusSell;

  private Instant dateModified;
}
