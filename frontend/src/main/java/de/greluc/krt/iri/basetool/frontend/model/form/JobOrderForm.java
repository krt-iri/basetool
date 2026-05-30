package de.greluc.krt.iri.basetool.frontend.model.form;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Data;

/** Form-binding object for Job Order input. */
@Data
public class JobOrderForm {
  /**
   * UUID of the org unit the order is executed for. R5.d.c renamed this from the historical {@code
   * requestingSquadronId} so the form's owner-picker can offer both Staffel and Spezialkommando
   * entries. The Thymeleaf form binds the picker fragment to this field; the controller forwards it
   * as {@code requestingOrgUnitId} on the backend {@code CreateJobOrderDto}. The backend rejects SK
   * selections with 400 today because the legacy {@code requesting_squadron_id} column is still NOT
   * NULL — the destructive cleanup release lifts that constraint and unlocks SK stamping.
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
