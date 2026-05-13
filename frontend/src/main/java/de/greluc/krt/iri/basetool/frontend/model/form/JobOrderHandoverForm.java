package de.greluc.krt.iri.basetool.frontend.model.form;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Data;

/** Form-binding object for Job Order Handover input. */
@Data
public class JobOrderHandoverForm {
  private String handoverTime; // We will parse it to Instant before sending
  private String recipientHandle;
  private String recipientSquadron;
  private List<JobOrderHandoverItemForm> items = new ArrayList<>();

  /** Form-binding object for Job Order Handover Item input. */
  @Data
  public static class JobOrderHandoverItemForm {
    private UUID inventoryItemId;
    private Double amount;
  }
}
