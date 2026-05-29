package de.greluc.krt.iri.basetool.backend.model;

/**
 * External catalogue that produced an {@link ExternalSyncReport} event.
 *
 * <p>Distinct from {@link MaterialExternalAliasSource} (which is scoped to the commodity-alias
 * domain): the sync report spans every aggregate (commodities, items, vehicles, blueprints), so it
 * carries its own source discriminator. The two enums share the {@code UEX} / {@code SCWIKI} member
 * names by coincidence of the underlying systems, not by shared semantics.
 */
public enum SyncSourceSystem {

  /** Event emitted by a UEX sync service. */
  UEX,

  /** Event emitted by an SC Wiki sync service. */
  SCWIKI
}
