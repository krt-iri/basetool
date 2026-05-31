package de.greluc.krt.iri.basetool.frontend.model.form;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Data;

/**
 * Backing form for the item-order create page. Mirrors {@link JobOrderForm}'s shape (org-unit
 * picker, handle, comment) but carries finished-item lines instead of raw materials. The nested
 * line + material lists are populated by indexed binding from the dynamically-built item editor
 * (Spring auto-grows the lists on bind), so they start empty.
 */
@Data
public class JobOrderItemForm {

  /** Org unit the order is executed for (picker output); {@code null} falls back server-side. */
  private UUID requestingOrgUnitId;

  /** Optional contact handle. */
  private String handle;

  /** Optional free-text comment. */
  private String comment;

  /** Optimistic-lock version (unused on create). */
  private Long version;

  /** Optional create-source marker carried through the form (mirrors {@link JobOrderForm}). */
  private String source;

  /** Ordered finished-item lines, bound by index from the item editor. */
  private List<JobOrderItemLineForm> items = new ArrayList<>();

  /**
   * One ordered finished-item line: the item, its chosen blueprint, amount, and quality choices.
   */
  @Data
  public static class JobOrderItemLineForm {

    /** The finished item to order. */
    private UUID gameItemId;

    /** The blueprint chosen to produce the item. */
    private UUID blueprintId;

    /** Whole-unit count to order. */
    private Integer amount;

    /** Transient client id of this line, used to link adopted sub-assembly lines. */
    private Integer clientLineId;

    /** Transient client id of the line this one was adopted from, or {@code null}. */
    private Integer parentClientLineId;

    /** Per-material quality choices derived for this line. */
    private List<JobOrderItemMaterialForm> materials = new ArrayList<>();
  }

  /** One per-material quality choice on an item line. */
  @Data
  public static class JobOrderItemMaterialForm {

    /** The material the choice applies to. */
    private UUID materialId;

    /** The chosen quality requirement name ({@code GOOD} or {@code NONE}). */
    private String quality;
  }
}
