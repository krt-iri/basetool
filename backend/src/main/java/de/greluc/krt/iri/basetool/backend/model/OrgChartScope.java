package de.greluc.krt.iri.basetool.backend.model;

/**
 * The three places a {@link OrgChartPosition} can live in the Profit-Bereich org chart. Every
 * {@link OrgChartPositionType} belongs to exactly one scope (see {@link
 * OrgChartPositionType#scope()}), which decides whether the position carries an {@code org_unit_id}
 * and which validation rules apply in {@code OrgChartService}.
 */
public enum OrgChartScope {

  /**
   * The singleton area leadership at the top of the chart (Bereichsleitung). Positions in this
   * scope carry no {@code org_unit_id} ({@code org_unit_id IS NULL}).
   */
  AREA,

  /**
   * A single Staffel (Squadron). Positions in this scope reference an {@link OrgUnit} of kind
   * {@link OrgUnitKind#SQUADRON} and form the Staffelleiter / Kommandoleiter / Stv. / Ensign tree.
   */
  SQUADRON,

  /**
   * A single Spezialkommando (SK). Positions in this scope reference an {@link OrgUnit} of kind
   * {@link OrgUnitKind#SPECIAL_COMMAND} and only ever hold the 1-2 SK-Leiter (Commander).
   */
  SPECIAL_COMMAND
}
