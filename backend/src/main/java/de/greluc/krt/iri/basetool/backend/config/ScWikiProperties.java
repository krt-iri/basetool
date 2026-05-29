package de.greluc.krt.iri.basetool.backend.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties under {@code krt.scwiki.*}.
 *
 * <p>Holds the SC Wiki (api.star-citizen.wiki) base URL and every endpoint path the upcoming {@code
 * ScWikiClient} uses. The endpoints are stored here, not hardcoded in the client, so that a Wiki
 * schema rename is a one-line config change and so the {@code application-test.yml} can point the
 * client at a {@code MockWebServer} URL without touching code.
 *
 * <p>{@code schedulerEnabled} defaults to {@code false} in R1 on purpose: the R1 PR ships only the
 * scheduler skeleton (see {@code ScWikiScheduler}); the actual commodity / blueprint / item sync
 * services land in R3+. Flipping the flag on prematurely would yield no useful effect but would
 * burn upstream API budget for nothing. The R2 PR keeps the default; R3 flips it to {@code true}
 * once {@code ScWikiCommoditySyncService} ships.
 *
 * <p>{@code requestsPerSecond} caps the inter-page sleep to a safe rate (default 5/s) — Wiki's
 * advertised limits are 60/min for search and 10/min for image search; plain list endpoints have no
 * published cap but the conservative pacing leaves headroom for a future tightening.
 */
@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "krt.scwiki")
public class ScWikiProperties {

  /** Base URL of the SC Wiki API. Overridden in tests to point at a {@code MockWebServer}. */
  @NotBlank private String apiUrl = "https://api.star-citizen.wiki";

  /** Commodity (trade goods + ammo / atmosphere) catalogue endpoint. */
  @NotBlank private String commoditiesEndpoint = "/api/commodities";

  /** Crafting blueprints endpoint (R3). */
  @NotBlank private String blueprintsEndpoint = "/api/blueprints";

  /** Full game-item pool (R4+). */
  @NotBlank private String itemsEndpoint = "/api/items";

  /** Ship / vehicle component catalogue (R4+). */
  @NotBlank private String vehicleItemsEndpoint = "/api/vehicle-items";

  /** Mounted ship weapons (R4+). */
  @NotBlank private String vehicleWeaponsEndpoint = "/api/vehicle-weapons";

  /** Hand-held FPS weapons (R4+). */
  @NotBlank private String weaponsEndpoint = "/api/weapons";

  /** Weapon attachments / scopes / magazines (R4+). */
  @NotBlank private String weaponAttachmentsEndpoint = "/api/weapon-attachments";

  /** FPS armor pieces (R4+). */
  @NotBlank private String armorEndpoint = "/api/armor";

  /** Clothing items (R4+). */
  @NotBlank private String clothesEndpoint = "/api/clothes";

  /** Food / drink items (R4+). */
  @NotBlank private String foodEndpoint = "/api/food";

  /** Manufacturer / in-universe vendor catalogue (R6). */
  @NotBlank private String manufacturersEndpoint = "/api/manufacturers";

  /**
   * Master switch for the {@code ScWikiScheduler}. Defaults to {@code false} in R1 — the scheduler
   * skeleton exists but no sync services consume its tick yet. R3 flips this to {@code true}.
   */
  @NotNull private Boolean schedulerEnabled = false;

  /**
   * Fixed delay between successive {@code ScWikiScheduler} ticks, in milliseconds. Default 24h (86
   * 400 000 ms); Wiki data changes only on game patches (every 2-6 weeks) so the cadence is much
   * slower than the UEX hourly schedule.
   */
  @NotBlank private String schedulerDelay = "86400000";

  /**
   * Page size for the {@code ?page[size]=…} query parameter. Capped at the Wiki's documented
   * maximum of 200; values below 50 are rejected to keep the per-cycle round-trip count reasonable.
   */
  @Min(50)
  @Max(200)
  private Integer pageSize = 200;

  /**
   * Inter-page sleep target in requests-per-second. Used by {@code ScWikiClient.paceForRateLimit}
   * between page fetches inside a single sync run; the sleep is {@code 1000 / requestsPerSecond}
   * milliseconds.
   */
  @Min(1)
  @Max(20)
  private Integer requestsPerSecond = 5;

  /**
   * Optional pin for the {@code ?version=…} query parameter, e.g. {@code "4.8.0-LIVE.11875683"}.
   * {@code null} means "follow upstream default" — the current game version. Pinning is reserved
   * for incidents where a Wiki patch introduces an unexpected schema drift; the runbook documents
   * how to set it.
   */
  private String gameVersion;

  /**
   * R4 toggle for the full-item backfill mode. When {@code false} (R3 default), the Wiki item sync
   * runs in closure mode and only re-fetches items already present in {@code game_item} plus the
   * items referenced by an ingested blueprint. When {@code true}, every Wiki item is paged in --
   * ~12 700 rows, ~10-15 min per cycle at the default 5 req/s pacing.
   */
  @NotNull private Boolean syncAllItems = false;
}
