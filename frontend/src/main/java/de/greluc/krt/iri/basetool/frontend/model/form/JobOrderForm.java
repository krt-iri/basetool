package de.greluc.krt.iri.basetool.frontend.model.form;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Data;

/** Form-binding object for Job Order input. */
@Data
public class JobOrderForm {
  /**
   * UUID of the profit-eligible org unit that will <em>process</em> the order (the responsible org
   * unit). Bound by the responsible picker on the create form; forwarded as {@code
   * responsibleOrgUnitId} on the backend {@code CreateJobOrderDto}. Ignored for guests, who are
   * routed onto the configured intake SK.
   */
  private UUID responsibleOrgUnitId;

  /**
   * UUID of the customer org unit the order is placed for (the requesting org unit). Bound by the
   * requesting picker; forwarded as {@code requestingOrgUnitId}. Any squadron or Spezialkommando.
   */
  private UUID requestingOrgUnitId;

  private String handle;

  /** Optional free-text comment captured from the order creator; HTML-escaped on display. */
  private String comment;

  private Long version;
  private String source;
  private List<JobOrderMaterialForm> materials = new ArrayList<>();

  /** Seeds the form with one empty material row so the "add material" UI starts non-empty. */
  public JobOrderForm() {
    materials.add(new JobOrderMaterialForm());
  }

  /** Form-binding object for Job Order Material input. */
  @Data
  public static class JobOrderMaterialForm {
    private UUID materialId;
    private Integer minQuality = 700;
    private Double amount;
  }
}
