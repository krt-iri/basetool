package de.greluc.krt.iri.basetool.frontend.model.form;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Data;

/** Form-binding object for Job Order input. */
@Data
public class JobOrderForm {
  private String squadron;
  private String handle;
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
