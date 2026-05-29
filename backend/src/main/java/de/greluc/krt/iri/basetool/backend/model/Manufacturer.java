package de.greluc.krt.iri.basetool.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Manufacturer JPA entity. */
@Entity
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class Manufacturer extends AbstractEntity<UUID> {

  @Getter(onMethod_ = @__(@Override))
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, unique = true)
  private String name;

  @Column(nullable = false, unique = true)
  private String abbreviation;

  @Column private String nickname;

  @Column private String wiki;

  @Column(columnDefinition = "TEXT")
  private String description;

  private boolean hidden = false;

  /**
   * UEX integer company id. Populated by the R2-hardened {@code UexManufacturerService} so the next
   * sync can fast-path the lookup instead of falling back to a case-insensitive name match.
   */
  @Column(name = "uex_company_id", unique = true)
  private Integer uexCompanyId;

  /**
   * SC Wiki manufacturer UUID. Populated by the R6 manufacturer reconciliation. UNIQUE so the same
   * Wiki entity cannot be linked to two local manufacturers.
   */
  @Column(name = "scwiki_uuid", unique = true)
  private UUID scwikiUuid;

  /** SC Wiki manufacturer short code (e.g. {@code "RSI"}, {@code "AEGS"}). */
  @Column(name = "scwiki_code")
  private String scwikiCode;

  /** Industry / sector label exposed by UEX (e.g. {@code "Aerospace"}, {@code "Fashion"}). */
  @Column(name = "industry")
  private String industry;

  /** Whether UEX flags this company as an item manufacturer. {@code null} until R2 sync runs. */
  @Column(name = "is_item_manufacturer")
  private Boolean isItemManufacturer;

  /** Whether UEX flags this company as a vehicle manufacturer. {@code null} until R2 sync runs. */
  @Column(name = "is_vehicle_manufacturer")
  private Boolean isVehicleManufacturer;

  /** Timestamp of the most recent successful UEX sync touch. */
  @Column(name = "uex_synced_at")
  private Instant uexSyncedAt;

  /** Timestamp of the most recent successful SC Wiki sync touch (R6+). */
  @Column(name = "scwiki_synced_at")
  private Instant scwikiSyncedAt;

  /**
   * Timestamp of the first sync run in which UEX no longer returned this manufacturer. Soft-delete
   * marker; cleared on the next sync that sees it again.
   */
  @Column(name = "uex_deleted_at")
  private Instant uexDeletedAt;

  /** Soft-delete marker mirroring {@link #uexDeletedAt} for the SC Wiki side. */
  @Column(name = "scwiki_deleted_at")
  private Instant scwikiDeletedAt;
}
