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

  /**
   * Vehicle catalogue endpoint — drives the R4 Wiki vehicle fill ({@code
   * ScWikiVehicleSyncService}).
   */
  @NotBlank private String vehiclesEndpoint = "/api/vehicles";

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
   * Master switch for the {@code ScWikiScheduler}. R3 flips the default to {@code true} now that
   * the scheduler drives a real implementation ({@code ScWikiCommoditySyncService}). The bean still
   * does nothing harmful when the per-sync feature flags below are off — the scheduler ticks but
   * each sync self-guards. Set to {@code false} to silence the scheduler entirely.
   */
  @NotNull private Boolean schedulerEnabled = true;

  /**
   * Per-sync feature flag for the R3 Wiki commodity merge ({@code ScWikiCommoditySyncService}).
   * Defaults to {@code false} so R3 ships "dark": the table, service and admin page all land, but
   * no live Wiki traffic is generated until an operator flips this on per the deployment runbook §3
   * (mirrors the R7 {@code krt.uex.item-price-sync-enabled} pattern). Decoupled from {@link
   * #schedulerEnabled} so the scheduler can stay live for future syncs while the commodity merge is
   * still being soaked.
   */
  @NotNull private Boolean commoditySyncEnabled = false;

  /**
   * Per-sync feature flag for the R4 Wiki blueprint sync ({@code ScWikiBlueprintSyncService}).
   * Default {@code false} — ships dark; flip on per runbook §4.
   */
  @NotNull private Boolean blueprintSyncEnabled = false;

  /**
   * Per-sync feature flag for the R4 closure-mode Wiki item sync ({@code ScWikiItemSyncService}).
   * Default {@code false} — ships dark. When on, fills Wiki columns on every existing {@code
   * game_item} (~5000 single fetches at the configured rate), so flip it on deliberately.
   */
  @NotNull private Boolean itemSyncEnabled = false;

  /**
   * Per-sync feature flag for the R4 Wiki vehicle sync ({@code ScWikiVehicleSyncService}). Default
   * {@code false} — ships dark; flip on per runbook §4.
   */
  @NotNull private Boolean vehicleSyncEnabled = false;

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

  /**
   * Mode-B {@code filter[classification]} value for {@code /api/armor}. <b>Must stay non-blank.</b>
   * The live Wiki {@code /api/armor} endpoint returns the FULL {@code /api/items} pool (~12 700
   * rows) when no classification filter is set (SC_WIKI_SYNC_PLAN.md §3.4 quirk #1, §13 open
   * question #3). The probed default {@code "FPS.Armor"} prefix-matches all six {@code FPS.Armor.*}
   * sub-classes (2 318 rows on game version 4.8.0). If this is cleared the {@link
   * #backfillKindSanityCap} guard refuses the resulting full-pool dump rather than mis-filing 12
   * 700 rows under {@code ARMOR}.
   */
  private String armorFilter = "FPS.Armor";

  /**
   * Mode-B {@code filter[classification]} value for {@code /api/clothes}. Defaults to the probed
   * {@code "FPS.Clothing"} prefix (1 826 rows on 4.8.0, identical to the endpoint's native total —
   * the explicit filter just makes the intent visible and survives a future armor-style quirk on
   * this endpoint). Blank disables the filter and relies on the endpoint's native behaviour.
   */
  private String clothesFilter = "FPS.Clothing";

  /**
   * Mode-B {@code filter[classification]} value for {@code /api/food}. Defaults to the probed
   * {@code "FPS.Consumable.Food"} prefix (221 rows on 4.8.0 = Bottle + Drink + Food, identical to
   * the endpoint's native total). Blank disables the filter.
   */
  private String foodFilter = "FPS.Consumable.Food";

  /**
   * Mode-B {@code filter[classification]} value for {@code /api/vehicle-items}. Blank by default:
   * the endpoint already returns only ship components (3 211 rows on 4.8.0, paints under {@code
   * Ship.Paints} included by design — they enter as {@code VEHICLE_ITEM}). Set a value only to
   * narrow the pass.
   */
  private String vehicleItemsFilter = "";

  /**
   * Mode-B {@code filter[classification]} value for {@code /api/vehicle-weapons}. Blank by default:
   * the endpoint natively returns only mounted ship weapons (168 rows on 4.8.0 = {@code
   * Ship.Weapon.*}).
   */
  private String vehicleWeaponsFilter = "";

  /**
   * Mode-B {@code filter[classification]} value for {@code /api/weapons}. Blank by default: the
   * endpoint natively returns only hand-held FPS weapons (391 rows on 4.8.0). A {@code FPS.Weapon}
   * prefix is deliberately NOT defaulted because the endpoint and the prefix disagree slightly.
   */
  private String weaponsFilter = "";

  /**
   * Mode-B {@code filter[classification]} value for {@code /api/weapon-attachments}. Blank by
   * default: the endpoint natively returns only attachments (104 rows on 4.8.0); the {@code
   * FPS.WeaponAttachment} prefix is broader (163) so it is deliberately NOT defaulted here.
   */
  private String weaponAttachmentsFilter = "";

  /**
   * Mode-B safety cap on the row count of a single <em>kind</em> endpoint pass (armor, clothes,
   * food, weapons, weapon-attachments, vehicle-items, vehicle-weapons). A pass that returns more
   * than this many rows is assumed to have hit the §3.4 full-pool quirk (e.g. a cleared {@link
   * #armorFilter}) and is <b>skipped</b> — its rows are not ingested and the cross-kind orphan
   * sweep is suppressed for the run. The default 9 000 sits comfortably above the largest
   * legitimate kind (vehicle-items, 3 211) and well below the full pool (~12 700). The residual
   * {@code /api/items} {@code GENERIC} catch-all pass is exempt — it legitimately returns the whole
   * pool.
   */
  @NotNull
  @Min(100)
  private Integer backfillKindSanityCap = 9000;
}
